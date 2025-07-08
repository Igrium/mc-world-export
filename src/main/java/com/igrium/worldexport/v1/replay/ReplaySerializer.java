package com.igrium.worldexport.v1.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.igrium.worldexport.util.FutureUtils;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.ObjWriter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ReplaySerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplaySerializer.class);

    private final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Getter @Setter @NonNull
    private Executor executor = Util.getMainWorkerExecutor();

    @Getter @Setter
    private int maxThreads = 0;

    public CompletableFuture<?> saveReplay(CapturedReplay replay, Path basePath) {
        return saveWorldMeshes(replay.getWorldMeshes(), basePath.resolve("world"));
    }

    public CompletableFuture<?> saveWorldMeshes(Map<ChunkSectionPos, List<WorldMesh>> worldMeshes, Path worldPath) {
        if (!Files.isDirectory(worldPath)) {
            try {
                Files.createDirectory(worldPath);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        List<Runnable> runnables = new ArrayList<>(worldMeshes.size());

        long startTime = Util.getMeasuringTimeMs();
        for (var entry : worldMeshes.entrySet()) {
            runnables.add(() -> {
                try {
                    saveSectionMesh(entry.getKey(), entry.getValue(), worldPath);
                } catch (Exception e) {
                    LOGGER.error("Exception writing mesh for section {}", entry.getKey(), e);
                }
            });
        }

        return CompletableFuture.allOf(FutureUtils.runAllAsync(runnables, executor, maxThreads)).thenRun(() -> {
            LOGGER.info("Saved {} world meshes in {}ms", runnables.size(), Util.getMeasuringTimeMs() - startTime);
        });
    }

    public void saveSectionMesh(ChunkSectionPos cPos, List<WorldMesh> objs, Path worldPath) throws IOException {
        String prefix = "world_%d_%d_%d.frame".formatted(cPos.getX(), cPos.getY(), cPos.getZ());

        int frameIndex = 0;
        for (var frame : objs) {
            Path objPath = worldPath.resolve(prefix + frameIndex + ".obj");
            try(BufferedWriter writer = Files.newBufferedWriter(objPath)) {
                ObjWriter.write(frame.obj(), writer);
            }

            Path metaPath = worldPath.resolve(prefix + frameIndex + ".json");
            try (BufferedWriter writer = Files.newBufferedWriter(metaPath)) {
                GSON.toJson(frame.meta(), writer);
            }

            LOGGER.info("wrote {}", prefix + frameIndex);
            frameIndex++;
        }
    }
}
