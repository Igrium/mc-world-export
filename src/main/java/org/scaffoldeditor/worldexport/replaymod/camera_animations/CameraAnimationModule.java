package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.scaffoldeditor.worldexport.replaymod.TimelineUpdateCallback;
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationSerializer;
import org.scaffoldeditor.worldexport.replaymod.gui.GuiCameraManager;
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

    public static record CameraPathFrame(Vec3d pos, Rotation rot, float fov) {}
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

    public void registerKeyBindings() {
        ReplayMod core = ReplayMod.instance;
        core.getKeyBindingRegistry().registerKeyBinding("worldexport.input.importcamera", 0, () -> {
            GuiCameraManager.openScreen(currentReplay);
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
        AnimatedCameraEntity camera = new AnimatedCameraEntity(client, world);
        camera.setId(entId);
        world.addEntity(entId, entity);

        return camera;
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

            Vec3d pos = anim.getPositionAt(timeSeconds);
            Rotation rot = anim.getRotationAt(timeSeconds);
            float fov = anim.getFovAt(timeSeconds);

            camera.setCameraPosition(pos.x, pos.y, pos.z);
            camera.setCameraRotation((float) rot.yaw(), (float) rot.pitch(), (float) rot.roll());
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
        CompletableFuture<Void> future = new CompletableFuture<>();

        animCache.put(file, HashBiMap.create(anims));
        saveService.submit(() -> {
            try (OutputStream out = file.write(ENTRY_ANIMATIONS)) {
                serializer.writeAnimations(anims, out);
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
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
