package com.igrium.worldexport.replay;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.igrium.worldexport.world.WorldCapture;
import com.igrium.worldexport.world.WorldTessellator;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Responsible for capturing a replay.
 */
public class ReplayCapture {

    public enum ReplayCaptureState {
        /**
         * The ReplayCapture object has been created, but it hasn't begun capture yet.
         */
        NEW,
        /**
         * The ReplayCapture is currently recording.
         */
        RUNNING,
        /**
         * The ReplayCapture has finished recording.
         */
        FINISHED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayCapture.class);

    private static final Set<ReplayCapture> activeCaptures = new HashSet<>();
    private static final Set<ReplayCapture> activeCapturesUnmodifiable = Collections.unmodifiableSet(activeCaptures);


    public static Set<ReplayCapture> getActiveCaptures() {
        return activeCapturesUnmodifiable;
    }

    /**
     * Event listener for <code>ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE</code>
     */
    public static void globalEndClientTick(MinecraftClient client) {
        // Duplicate to avoid concurrent modification if capture decides to end.
        for (var cap : activeCaptures.toArray(ReplayCapture[]::new)) {
            cap.onEndTick();
        }
    }

    /**
     * Event listener for <code>ClientBlockUpdatedEvent</code>
     */
    public static void globalClientBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, World world) {
        for (var cap : activeCaptures) {
            cap.onUpdateBlock(pos, newState, world);
        }
    }

    /**
     * Event listener for <code>ClientChunkEvents.CHUNK_LOAD</code>
     */
    public static void globalClientChunkLoad(ClientWorld world, WorldChunk chunk) {
        for (var cap : activeCaptures) {
            cap.onLoadChunk(world, chunk);
        }
    }

    /**
     * Event listener for <code>ClientWorldEvents.SET_WORLD</code>
     */
    public static void globalClientWorldChange(MinecraftClient client, World world) {
        for (var cap : activeCaptures.toArray(ReplayCapture[]::new)) {
            cap.finish();
        }
    }

    @Getter
    private final World world;

    @Getter
    private final ReplaySettings settings;

    @Getter
    private final WorldCapture worldCapture;

    @Getter
    private final WorldTessellator worldTessellator;

    @Getter
    private final Executor executor;

    @Getter
    private ReplayCaptureState state = ReplayCaptureState.NEW;

    private int gameTick;
    private int replayTick;

    public ReplayCapture(World world, ReplaySettings settings) {
        this.world = world;
        this.settings = settings;

        executor = Util.getMainWorkerExecutor(); // Make our own as to not starve this.

        worldCapture = new WorldCapture(settings.getBounds());

        worldTessellator = new WorldTessellator(worldCapture, world);
        worldTessellator.setExecutor(executor);
        worldTessellator.setOffset(settings.getOffset());
        worldTessellator.setSplitBlocks(settings.isSplitBlocks());
        worldTessellator.setMergeBaseMeshes(settings.isMergeBaseMeshes());
        worldTessellator.setMergeDoubleVertices(settings.isMergeDoubleVertices());
    }

    /**
     * Capture the base world and begin tessellating base meshes.
     */
    public void beginCapture() {
        if (state != ReplayCaptureState.NEW) {
            LOGGER.warn("Capture has already begun.");
            return;
        }

        if (!MinecraftClient.getInstance().isOnThread()) {
            throw new IllegalStateException("beginCapture can only be called from the primary client thread.");
        }

        long captureStart = Util.getMeasuringTimeMs();
        worldCapture.captureBaseWorld(world);
        LOGGER.info("Cloned base world in {}ms", Util.getMeasuringTimeMs() - captureStart);

        long meshStartTime = Util.getMeasuringTimeMs();
        worldTessellator.tessellateBaseWorld();
        worldTessellator.awaitBaseTessellationFinished().thenRun(() -> {
            LOGGER.info("Finished tessellating base world in {}ms", Util.getMeasuringTimeMs() - meshStartTime);
        });
        gameTick = 0;

        activeCaptures.add(this);
        state = ReplayCaptureState.RUNNING;
    }

    public void onEndTick() {
        int stride = settings.getTickStride();
        if (gameTick % stride == 0) {
            // do tick logic
            replayTick++;
        }
        gameTick++;
    }

    public void onUpdateBlock(BlockPos globalPos, BlockState newBlock, World world) {
        if (world == this.world) {
            worldCapture.addBlockUpdate(globalPos, newBlock, replayTick);
        }
    }

    public void onLoadChunk(World world, WorldChunk chunk) {
        if (world == this.world) {
            worldCapture.onChunkLoaded(chunk, replayTick);
        }
    }

    public CompletableFuture<CapturedReplay> compile() {
        if (state != ReplayCaptureState.FINISHED) {
            LOGGER.warn("Replay capture is not finished. Compilation could exhibit unwanted behavior.");
        }

        long startTime = Util.getMeasuringTimeMs();
        return worldTessellator.tessellateAllMeshes(null).thenApply(meshes -> {
            CapturedReplay replay = new CapturedReplay();
            replay.getWorldMeshes().addAll(Arrays.asList(meshes));
            LOGGER.info("Compiled replay in {}ms", Util.getMeasuringTimeMs() - startTime);
            return replay;
        });
    }

    /**
     * Finish recording this replay.
     */
    public void finish() {
        if (state != ReplayCaptureState.RUNNING) {
            LOGGER.warn("Replay capture must be running to call finish()");
            return;
        }

        if (!MinecraftClient.getInstance().isOnThread()) {
            throw new IllegalStateException("finish() can only be called on the primary client thread.");
        }

        activeCaptures.remove(this);
        state = ReplayCaptureState.FINISHED;
    }
}
