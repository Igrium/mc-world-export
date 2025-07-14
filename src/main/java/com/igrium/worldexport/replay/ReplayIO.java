package com.igrium.worldexport.replay;

import com.google.gson.Gson;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.MtlWriter;
import de.javagl.obj.ObjWriter;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
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

    public static CompletableFuture<?> saveReplayAsync(Path root, CompiledReplay replay, Executor executor) {
        return saveWorldAsync(root, replay, executor)
                .thenCompose(v -> saveTexturesAsync(root, replay, executor))
                .thenRun(() -> saveMtls(root, replay));
    }

    private static CompletableFuture<?> saveWorldAsync(Path root, CompiledReplay replay, Executor executor) {
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

    private static CompletableFuture<?> saveTexturesAsync(Path root, CompiledReplay replay, Executor executor) {
        List<CompletableFuture<?>> textureFutures = new ArrayList<>(replay.getTextures().size());
        for (var entry : replay.getTextures().entrySet()) {
            textureFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    saveTexture(entry.getValue(), root.resolve(entry.getKey()));
                } catch (Exception e) {
                    LOGGER.error("Error saving texture {}: ", entry.getKey(), e);
                }
            }, executor));
        }
        return CompletableFuture.allOf(textureFutures.toArray(new CompletableFuture[0]));
    }

    private static void saveTexture(NativeImage texture, Path imagePath) throws IOException {
        Files.createDirectories(imagePath.getParent());
        texture.writeTo(imagePath);
    }

    // No need to overcomplicate this part with multithreading; these files are really small.
    private static void saveMtls(Path root, CompiledReplay replay) {
        for (var entry : replay.getMtlLibs().entrySet()) {
            Path mtlPath = root.resolve(entry.getKey());
            try {
                Files.createDirectories(mtlPath.getParent());
                try(BufferedWriter writer = Files.newBufferedWriter(mtlPath)) {
                    MtlWriter.write(entry.getValue(), writer);
                }

            } catch (IOException e) {
                LOGGER.error("Error saving MTL library {}: ", entry.getKey(), e);
            }
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
