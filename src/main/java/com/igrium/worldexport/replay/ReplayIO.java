package com.igrium.worldexport.replay;

import com.google.gson.Gson;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.ObjWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class ReplayIO {

    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayIO.class);

    public static CompletableFuture<?> saveReplayAsync(Path root, CapturedReplay replay, Executor executor) {
        Path worldDir = root.resolve("world");
        try {
            Files.createDirectories(worldDir);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> worldFutures = new ArrayList<>(replay.getWorldMeshes().size());
        int meshIndex = 0;
        for (var mesh : replay.getWorldMeshes()) {
            String name = "mesh." + meshIndex;
            worldFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    saveMesh(worldDir, mesh, name);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, executor).exceptionally(e -> {
                LOGGER.error("Error saving world mesh {}", name, e);
                return null;
            }));
            meshIndex++;
        }

        return CompletableFuture.allOf(worldFutures.toArray(new CompletableFuture[0]));
    }

    @Deprecated
    public static void saveReplay(Path root, CapturedReplay replay) throws IOException {
        Path worldDir = root.resolve("world");
        Files.createDirectories(worldDir);

        int i = 0;
        for (var mesh : replay.getWorldMeshes()) {
            saveMesh(worldDir, mesh, "mesh." + i);
            i++;
        }
    }

    public static void saveMesh(Path worldDir, WorldMesh mesh, String name) throws IOException {
        if (mesh.obj().getNumVertices() == 0)
            return;

        // Save meta
        if (!mesh.meta().isEmpty()) {
            try(var jsonOut = Files.newBufferedWriter(worldDir.resolve(name + ".json"))) {
                GSON.toJson(mesh.meta(), jsonOut);
            }
        }

        try(var objOut = Files.newBufferedWriter(worldDir.resolve(name + ".obj"))) {
            ObjWriter.write(mesh.obj(), objOut);
        }
    }
}
