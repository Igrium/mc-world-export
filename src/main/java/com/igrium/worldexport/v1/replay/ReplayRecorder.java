package com.igrium.worldexport.v1.replay;

import com.igrium.worldexport.v1.world.WorldRecorder;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ReplayRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayRecorder.class);

    /**
     * Shortcut for <code>IgriumsReplayExporter.getInstance().getActiveRecorder()</code>
     */
    public static ReplayRecorder getActiveRecorder() {
        return IgriumsReplayExporter.getInstance().getActiveRecorder();
    }

    @Getter
    private final ReplayRecordingSettings settings;

    @Getter
    private final World world;

    @Nullable @Getter
    private WorldRecorder worldRecorder;

    /**
     * If this recorder has started recording
     */
    @Getter
    private boolean recording;

    public ReplayRecorder(ReplayRecordingSettings settings, World world) {
        this.settings = Objects.requireNonNull(settings);
        this.world = world;
    }

    public void startRecording() {
        if (recording) {
            LOGGER.warn("Already recording.");
            return;
        }

        recording = true;

        if (settings.isRecordWorld()) {
            worldRecorder = createWorldRecorder();
            worldRecorder.startRecording();
        }

    }

    private WorldRecorder createWorldRecorder() {
        return new WorldRecorder(world, settings.getMinSection(), settings.getMaxSection());
    }

    public void onClientTick() {
        if (!recording)
            return;

        if (worldRecorder != null) {
            worldRecorder.nextTick();
        }
    }

    public void onUpdateBlock(BlockPos pos, @Nullable BlockState oldBlock, BlockState newBlock) {
        if (isRecording() && worldRecorder != null) {
            worldRecorder.onUpdateBlock(pos, oldBlock, newBlock);
        }
    }

    public void onLoadChunk(ChunkPos pos, WorldChunk chunk) {
        if (recording && worldRecorder != null) {
            worldRecorder.onLoadChunk(pos, chunk);
        }
    }

    /**
     * Tessellate all world meshes and bake entities in preparation to save on disk.
     * @param executor Executor to use.
     * @param maxThreads Max concurrent threads.
     * @return A future that completes with the captured replay, and fails if something goes wrong.
     */
    public CompletableFuture<CapturedReplay> compile(Executor executor, int maxThreads) {
        CapturedReplay replay = new CapturedReplay();
        if (worldRecorder != null) {
            return worldRecorder.getWorldTessellator().tessellateAllMeshes(executor, maxThreads).thenApply(r -> {
                replay.getWorldMeshes().putAll(r);
                return replay;
            });
        } else {
            return CompletableFuture.completedFuture(replay);
        }
    }
}
