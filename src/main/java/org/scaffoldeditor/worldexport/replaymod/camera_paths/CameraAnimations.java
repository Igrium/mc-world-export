package org.scaffoldeditor.worldexport.replaymod.camera_paths;

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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.events.ReplayClosingCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replaystudio.replay.ReplayFile;

import net.minecraft.util.math.Vec3d;

/**
 * A custom replay mod module allowing for camera paths to be imported from
 * external software.
 */
public class CameraAnimations extends EventRegistrations {

    public static final String ENTRY_ANIMATIONS = "animations.json";

    /**
     * For some (unknown) reason, the Replay mod doesn't store deserialized objects
     * in memory. Instead, it re-parses them every time it needs to access them.
     * This is stupid, so we cache our values here.
     */
    private Map<ReplayFile, BiMap<Integer, AbstractCameraAnimation>> animCache = new HashMap<>();

    public static record CameraPathFrame(Vec3d pos, Vec3d rot, float fov) {}
    protected AnimationSerializer serializer = new AnimationSerializer();

    private ExecutorService saveService;

    public CameraAnimations() {
        on(ReplayOpenedCallback.EVENT, this::onReplayOpened);
        on(ReplayClosingCallback.EVENT, this::onReplayClosing);
    }

    private void onReplayOpened(ReplayHandler handler) throws IOException {
        saveService = Executors.newSingleThreadExecutor();
        cacheAnimations(handler.getReplayFile());
    }

    private void onReplayClosing(ReplayHandler handler) {
        animCache.remove(handler.getReplayFile());

        if (animCache.isEmpty()) {
            saveService.shutdown();
            try {
                saveService.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            saveService = null;
        }
    }

    /**
     * Get all the camera animations in a replay file.
     * @param file The replay file.
     * @return The animations and their IDs.
     * @throws IOException If an IO exception occurs while loading the animations.
     */
    public Map<Integer, AbstractCameraAnimation> getAnimations(ReplayFile file) throws IOException {
        synchronized (animCache) {
            BiMap<Integer, AbstractCameraAnimation> cached = animCache.get(file);
            if (cached == null) {
                cacheAnimations(file);
            }
            return Collections.unmodifiableMap(cached);
        }
    }

    /**
     * Get an animation from a replay file by it's id.
     * @param file The replay file.
     * @param id The ID.
     * @return The animation, if it exists.
     * @throws IOException If an IO exception occurs while loading the animation.
     */
    public Optional<AbstractCameraAnimation> getAnimation(ReplayFile file, int id) throws IOException {
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
