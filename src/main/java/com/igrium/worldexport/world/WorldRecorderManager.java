package com.igrium.worldexport.world;

import com.igrium.worldexport.IgriumsReplayExporter;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldRecorderManager {
    /**
     * Shortcut for <code>IgriumsReplayExporter.getInstance().getWorldRecorderManager()</code>
     */
    public static WorldRecorderManager getInstance() {
        return IgriumsReplayExporter.getInstance().getWorldRecorderManager();
    }

    private final Set<WorldRecorder> activeRecorders = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Set<WorldRecorder> getActiveRecorders() {
        return Collections.unmodifiableSet(activeRecorders);
    }

    public WorldRecorder startRecording(World world, ChunkSectionPos minPos, ChunkSectionPos maxPos) {
        return startRecording(new WorldRecorder(world, minPos, maxPos));
    }

    public WorldRecorder startRecording(WorldRecorder recorder) {
        if (activeRecorders.add(recorder) && !recorder.isStartedRecording()) {
            recorder.onStartRecording();
        }
        if (activeRecorders.size() > 1) {
            IgriumsReplayExporter.LOGGER.warn("Multiple world recorders are active. This is supported, but can lead to performance issues.");
        }
        return recorder;
    }

    public boolean stopRecording(WorldRecorder recorder) {
        return activeRecorders.remove(recorder);
    }

    public void onChangeWorld(World newWorld) {
        activeRecorders.clear();
    }

    public void onEndClientTick() {
        for (var recorder : activeRecorders) {
            recorder.nextTick();
        }
    }

    public void onUpdateBlock(BlockPos pos, BlockState oldState, BlockState newState, World world) {
        for (var recorder : activeRecorders) {
            recorder.onUpdateBlock(pos, oldState, newState);
        }
    }

    public void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        for (var recorder : activeRecorders) {
            recorder.onLoadChunk(chunk.getPos(), chunk);
        }
    }
}

