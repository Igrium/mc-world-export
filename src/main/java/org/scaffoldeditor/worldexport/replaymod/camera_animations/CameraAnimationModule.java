package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.ReplayExportMod;
import org.scaffoldeditor.worldexport.gui.GuiCameraManager;
import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntity;
import org.scaffoldeditor.worldexport.replaymod.TimelineUpdateCallback;
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationSerializer;
import org.scaffoldeditor.worldexport.util.FutureUtils;
import org.scaffoldeditor.worldexport.util.RenderUtils;
import org.scaffoldeditor.worldexport.util.UnsupportedFileTypeException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.events.ReplayClosingCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replaystudio.pathing.path.Timeline;
import com.replaymod.replaystudio.replay.ReplayFile;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * A custom replay mod module allowing for camera paths to be imported from
 * external software.
 */
public class CameraAnimationModule extends EventRegistrations {

    public static final String ENTRY_ANIMATIONS = "animations.xml";
    public static final int CAMERA_ID_CONSTANT = 2048;

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Shortcut for <code>ReplayExportMod.getInstance().getCameraAnimationsModule()</code>
     * @return The active module instance.
     */
    public static CameraAnimationModule getInstance() {
        return ReplayExportMod.getInstance().getCameraAnimationsModule();
    }

    /**
     * Given a camera ID, get the net ID of the corresponding client entity.
     * @param cameraId Camera ID.
     * @return Entity net ID.
     */
    public static int getEntId(int cameraId) {
        return (cameraId + CAMERA_ID_CONSTANT) * -1;
    }

    /**
     * Given the network ID of an animated camera entity, get the corresponding camera animation.
     * @param entId Entity net ID.
     * @return Camera ID.
     */
    public static int getCamId(int entId) {
        return entId * -1 - CAMERA_ID_CONSTANT;
    }

    /**
     * For some (unknown) reason, the Replay mod doesn't store deserialized objects
     * in memory. Instead, it re-parses them every time it needs to access them.
     * This is stupid, so we cache our values here.
     */
    private Map<ReplayFile, BiMap<Integer, AbstractCameraAnimation>> animCache = new HashMap<>();

    public static record CameraPathFrame(Vec3d pos, Rotation rot, double fov) {}
    protected AnimationSerializer serializer = new AnimationSerializer();
    protected final MinecraftClient client = MinecraftClient.getInstance();

    public KeyBindingRegistry.Binding keySyncTime;
    private ExecutorService saveService;

    private ReplayHandler currentReplay;

    public CameraAnimationModule() {
        on(ReplayOpenedCallback.EVENT, this::onReplayOpened);
        on(ReplayClosingCallback.EVENT, this::onReplayClosing);
    }

    @Override
    public void register() {
        TimelineUpdateCallback.EVENT.register(this::onTimelineTick);
        super.register();
    }

    public void registerKeyBindings(ReplayMod replayMod) {
        replayMod.getKeyBindingRegistry().registerKeyBinding("worldexport.input.importcamera", 0, () -> {
            new GuiCameraManager(this, currentReplay).display();
        }, true);
    }

    /**
     * Get or create the animated camera entity with a specific camera ID.
     * @param world The world to search in.
     * @param id The camera ID.
     * @return This camera's entity.
     */
    public AnimatedCameraEntity getCameraEntity(ClientWorld world, int id) {
        int entId = getEntId(id);
        Entity entity = world.getEntityById(entId);
        if (entity != null) {
            if (!(entity instanceof AnimatedCameraEntity)) {
                throw new IllegalStateException("A client entity was found with the id " + entId
                        + " but it is not a camera! (" + entity.getClass().getName() + ")");
            }
            return (AnimatedCameraEntity) entity;
        }

        // Create the entity if it doesn't exist.
        AnimatedCameraEntity camera = ReplayExportMod.ANIMATED_CAMERA.create(world);
        camera.setId(entId);
        world.addEntity(camera);
        return camera;
    }

    /**
     * Get the animated camera entity with a specific id, only if it already exists.
     * @param world The world to search in.
     * @param id The camera ID.
     * @return This camera's entity.
     */
    public Optional<AnimatedCameraEntity> optCameraEntity(ClientWorld world, int id) {
        int entId = getEntId(id);
        Entity entity = world.getEntityById(entId);
        if (entity != null) {
            if (!(entity instanceof AnimatedCameraEntity)) {
                throw new IllegalStateException("A client entity was found with the id " + entId
                        + " but it is not a camera! (" + entity.getClass().getName() + ")");
            }
            return Optional.of((AnimatedCameraEntity) entity);
        }
        return Optional.empty();
    }

    private void onReplayOpened(ReplayHandler handler) throws IOException {
        currentReplay = handler;
        saveService = Executors.newSingleThreadExecutor();
        animsBroken = false;
        cacheAnimations(handler.getReplayFile());
    }

    private void onReplayClosing(ReplayHandler handler) {
        animCache.remove(handler.getReplayFile());
        if (handler == currentReplay) {
            currentReplay = null;
        } else {
            LOGGER.warn("Recieved onReplayClosing for replay that is not open!");
        }
        if (animCache.isEmpty()) closeSaveService();
    }

    private boolean animsBroken = false;

    private void onTimelineTick(Timeline timeline, Object handler, long time) {
        if (animsBroken) return;
        
        ReplayHandler replayHandler = (ReplayHandler) handler;
        Map<Integer, AbstractCameraAnimation> animations;
        try {
            animations = getAnimationsOrThrow(replayHandler.getReplayFile());
        } catch (IOException e) {
            LOGGER.error("Unable to load imported camera animations.", e);
            animsBroken = true; // Don't spam the console.
            return;
        }
        double timeSeconds = time / 1000d;
        for (int id : animations.keySet()) {
            AbstractCameraAnimation anim = animations.get(id);
            AnimatedCameraEntity camera = getCameraEntity(client.world, id);
            camera.setColor(RenderUtils.colorToARGB(anim.getColor()));

            Vec3d pos = anim.getPositionAt(timeSeconds);
            Vec3d offset = anim.getOffset();
            Rotation rot = anim.getRotationAt(timeSeconds);
            double fov = anim.getFovAt(timeSeconds);

            camera.setCameraPosition(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
            camera.setCameraRotation(rot);
            camera.setFov(fov);
        }
    }

    private void closeSaveService() {
        saveService.shutdown();
        try {
            saveService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        saveService = null;
    }

    /**
     * Remove a collection of camera animations from the file.
     * @param file The file to remove from.
     * @param animations The animations to remove.
     * @return The number of animations that were found.
     * @throws IOException If an IO exception occurs while modifying the file.
     */
    public int removeAnimations(ReplayFile file, Collection<AbstractCameraAnimation> animations) throws IOException {
        Map<Integer, AbstractCameraAnimation> anims = getAnimationsOrThrow(file);
        BiMap<Integer, AbstractCameraAnimation> newAnims = HashBiMap.create();

        int removed = 0;
        for (int id : anims.keySet()) {
            AbstractCameraAnimation anim = anims.get(id);
            if (!animations.contains(anim)) {
                newAnims.put(id, anim);
            } else {
                removed++;
            }
        }

        writeAnimations(file, newAnims); 
        return removed;
    }

    /**
     * Renive an animation from the file.
     * @param file The file to remove from.
     * @param animation The animation to remove.
     * @return If the animation was found in the file.
     * @throws IOException If an IO exception occurs while modifying the file.
     */
    public boolean removeAnimation(ReplayFile file, AbstractCameraAnimation animation) throws IOException {
        return removeAnimations(file, Collections.singleton(animation)) >= 1;
    }

    public AbstractCameraAnimation importAnimation(ReplayFile replayFile, File file) throws UnsupportedFileTypeException, IOException {
        String ext = FilenameUtils.getExtension(file.getName());
        if (ext.equals("xml")) {
            return importAnimXML(replayFile, file);
        } else {
            throw new UnsupportedFileTypeException(ext);
        }
    }
    
    private AbstractCameraAnimation importAnimXML(ReplayFile replayFile, File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        AbstractCameraAnimation anim = serializer.loadAnimation(in);
        in.close();

        return injestAnimation(replayFile, anim);
    }

    protected AbstractCameraAnimation injestAnimation(ReplayFile replayFile, AbstractCameraAnimation animation) throws IOException {
        Map<Integer, AbstractCameraAnimation> anims = getAnimationsOrThrow(replayFile);

        int id = 0;
        while (anims.get(id) != null) {
            id++;
        }
        animation.setId(id);

        var newAnims = HashBiMap.create(anims);
        newAnims.put(id, animation);
        writeAnimations(replayFile, newAnims);

        return animation;
    }

    /**
     * Get all the camera animations in a replay file.
     * @param file The replay file.
     * @return The animations and their IDs.
     * @throws IOException If an IO exception occurs while loading the animations.
     */
    public Map<Integer, AbstractCameraAnimation> getAnimationsOrThrow(ReplayFile file) throws IOException {
        synchronized (animCache) {
            BiMap<Integer, AbstractCameraAnimation> cached = animCache.get(file);
            if (cached == null) {
                cached = cacheAnimations(file);
            }
            return Collections.unmodifiableMap(cached);
        }
    }

    /**
     * Get all the camera animations in a replay file.
     * @param file The replay file.
     * @return The animations and their IDs.
     */
    public Map<Integer, AbstractCameraAnimation> getAnimations(ReplayFile file) {
        try {
            return getAnimationsOrThrow(file);
        } catch (IOException e) {
            LOGGER.error("Error loading animations for replay file.", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get an animation from a replay file by it's id.
     * @param file The replay file.
     * @param id The ID.
     * @return The animation, if it exists.
     */
    public Optional<AbstractCameraAnimation> getAnimation(ReplayFile file, int id) {
        return Optional.ofNullable(getAnimations(file).get(id));
    }

    /**
     * Save a set of animations to an existing replay file, in addition to the
     * animations that were already there. If there are conflicting IDs, the new
     * animations override the old animations.
     * 
     * @param file  The file.
     * @param anims The animations.
     * @throws IOException If an IO exception occurs while reading the file or writing the animations.
     */
    public void addAnimations(ReplayFile file, Map<Integer, AbstractCameraAnimation> anims) throws IOException {
        Map<Integer, AbstractCameraAnimation> map = HashBiMap.create();
        map.putAll(getAnimationsOrThrow(file));
        map.putAll(anims);
        writeAnimations(file, map);
    }

    /**
     * <p>
     * Save a set of animations to an existing replay file asynchronously, in
     * addition to the animations that were already there. If there are conflicting
     * IDs, the new animations override the old animations.
     * </p>
     * <p>
     * If the file is already cached, the changes are written to the cache instantly
     * and only the file write is deferred. If not, the entire method must be run
     * asynchronously.
     * </p>
     * 
     * @param file  The file.
     * @param anims The animations.
     * @return A future that completes when its finished writing and fails if
     *         there's an error.
     * @throws IllegalStateException If the save service is not running.
     */
    public CompletableFuture<Void> addAnimationsAsync(ReplayFile file, Map<Integer, AbstractCameraAnimation> anims) throws IllegalStateException {
        if (saveService == null) {
            throw new IllegalStateException("The save service is not running!");
        }
        synchronized(animCache) {
            if (animCache.containsKey(file)) {
                // If it's cached, we can retrieve (and update) immedietly without blocking.
                try {
                    Map<Integer, AbstractCameraAnimation> map = HashBiMap.create(getAnimationsOrThrow(file));
                    map.putAll(anims);
                    return writeAnimsAsync(file, map);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            } else {
                // If not, we need to do the entire thing on the save service.
                return FutureUtils.runAsync(() -> addAnimations(file, anims), saveService);
            }
        }
    }
    
    /**
     * Write all camera animations to a replay file.
     * @param file The file.
     * @param anims The animations.
     * @throws IOException If an IO exception occurs while writing the animations.
     */
    public void writeAnimations(ReplayFile file, Map<Integer, AbstractCameraAnimation> anims) throws IOException {
        try (OutputStream out = file.write(ENTRY_ANIMATIONS)) {
            serializer.writeAnimations(anims, out);
        }
        synchronized(animCache) {
            animCache.put(file, HashBiMap.create(anims));
        }
    }

    /**
     * Write all camera animations to a replay file asynchronously. Although it
     * delays writing to file, the cache updates instantly.
     * 
     * @param file  The file.
     * @param anims The animations.
     * @return A future that completes when its finished writing and fails if
     *         there's an error.
     * @throws IllegalStateException If the save service is not running.
     */
    public CompletableFuture<Void> writeAnimsAsync(ReplayFile file, Map<Integer, AbstractCameraAnimation> anims)
            throws IllegalStateException {
        if (saveService == null) {
            throw new IllegalStateException("The save service is not running!");
        }
        animCache.put(file, HashBiMap.create(anims));
        return FutureUtils.runAsync(() -> {
            OutputStream out = file.write(ENTRY_ANIMATIONS);
            serializer.writeAnimations(anims, out);
            out.close();
        }, client);
    }

    private BiMap<Integer, AbstractCameraAnimation> cacheAnimations(ReplayFile file) throws IOException {
        synchronized (animCache) {
            com.replaymod.replaystudio.lib.guava.base.Optional<InputStream> opt = file.get(ENTRY_ANIMATIONS);
            if (!opt.isPresent()) return HashBiMap.create(0);

            try (InputStream in = opt.get()) {
                BiMap<Integer, AbstractCameraAnimation> anims = serializer.loadAnimations(in);
                animCache.put(file, anims);
                return anims;
            }
        }
    }
}
