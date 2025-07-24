package com.igrium.worldexport.replay;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.tex.PngReplayTexture;
import com.igrium.worldexport.tex.ReplayTexture;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.*;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class ReplayIO {

    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayIO.class);

    public static CompletableFuture<?> saveReplayAsync(Path root, CompiledReplay replay, Executor executor) {
        return saveWorldAsync(root, replay, executor)
                .thenCompose(v -> saveEntitiesAsync(root, replay, executor))
                .thenCompose(v -> saveTexturesAsync(root, replay, executor))
                .thenRun(() -> saveMtls(root, replay));
    }

    public static CompletableFuture<CompiledReplay> loadReplayAsync(Path root, Executor executor) {
        CompiledReplay replay = new CompiledReplay();
        return loadWorldAsync(root, replay, executor)
                .thenCompose(v -> loadEntitiesAsync(root, replay, executor))
                .thenCompose(v -> loadTexturesAsync(root, replay, executor))
                .thenRun(() -> loadMtls(root, replay))
                .thenApply(v -> replay);
    }

    private static CompletableFuture<?> saveEntitiesAsync(Path root, CompiledReplay replay, Executor executor) {
        Path entityDir = root.resolve("entities");
        try {
            Files.createDirectories(entityDir);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> entityFutures = new ArrayList<>(replay.getEntities().size());
        for (var entEntry : replay.getEntities().entrySet()) {
            entityFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    saveEntity(entityDir, entEntry.getValue(), entEntry.getKey());
                } catch (Exception e) {
                    LOGGER.error("Error saving entity {}: ", entEntry.getKey(), e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(entityFutures.toArray(new CompletableFuture[0]));
    }

    public static void saveEntity(Path entityDir, CapturedEntity entity, String name) throws IOException {
        Path objPath = entityDir.resolve(name + ".obj");
        try (BufferedWriter writer = Files.newBufferedWriter(objPath)) {
            entity.writeObj(writer);
        }

        Path jsonPath = entityDir.resolve(name + ".json");
        try (BufferedWriter writer = Files.newBufferedWriter(jsonPath)) {
            GSON.toJson(entity.getParents(), writer);
        }

        Path animPath = entityDir.resolve(name + ".anim");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(animPath)))) {
            entity.writeAnimFile(out);
        }
    }

    private static CompletableFuture<?> loadEntitiesAsync(Path root, CompiledReplay replay, Executor executor) {
        Path entityDir = root.resolve("entities");
        if (!Files.isDirectory(entityDir)) {
            LOGGER.error("Entity directory is not a directory.");
            return CompletableFuture.completedFuture(null);
        }

        List<String> entityNames;
        try (var fileStream = Files.list(entityDir)) {

            entityNames = fileStream
                    .filter(path -> path.toString().endsWith(".anim"))
                    .filter(Files::isRegularFile)
                    .map(path -> FilenameUtils.getBaseName(path.toString()))
                    .toList();

        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>(entityNames.size());
        Map<String, CapturedEntity> entities = new ConcurrentHashMap<>();

        for (var entName : entityNames) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    entities.put(entName, loadEntity(entityDir, entName));
                } catch (Exception e) {
                    LOGGER.error("Error loading entity {}: ", entName, e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            replay.getEntities().putAll(entities);
        });
    }

    public static CapturedEntity loadEntity(Path entityDir, String name) throws IOException {
        Path objPath = entityDir.resolve(name + ".obj");
        CapturedEntity entity = new CapturedEntity();
        if (Files.isRegularFile(objPath)) {
            try (BufferedReader reader = Files.newBufferedReader(objPath)) {
                entity.readObj(reader);
            }
        }

        Path jsonPath = entityDir.resolve(name + ".json");
        if (Files.isRegularFile(jsonPath)) {
            try (BufferedReader reader = Files.newBufferedReader(jsonPath)) {
                TypeToken<Map<String, String>> typeToken = new TypeToken<>() {};
                entity.getParents().putAll(GSON.fromJson(reader, typeToken));
            }
        }

        Path animPath = entityDir.resolve(name + ".anim");
        if (Files.isRegularFile(animPath)) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(animPath)))) {
                entity.readAnimFile(in);
            }
        }

        return entity;
    }

    private static CompletableFuture<?> saveWorldAsync(Path root, CompiledReplay replay, Executor executor) {
        Path worldDir = root.resolve("world");
        try {
            Files.createDirectories(worldDir);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> worldFutures = new ArrayList<>(replay.getWorldMeshes().size());
        for (var meshEntry: replay.getWorldMeshes().entrySet()) {
            String name = meshEntry.getKey();
            worldFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    saveWorldMesh(worldDir, meshEntry.getValue(), name);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor).exceptionally(e -> {
                LOGGER.error("Error saving world mesh {}", name, e);
                return null;
            }));
        }
        return CompletableFuture.allOf(worldFutures.toArray(new CompletableFuture[0]));
    }

    public static void saveWorldMesh(Path worldDir, WorldMesh mesh, String name) throws IOException {
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

    private static CompletableFuture<Map<String, WorldMesh>> loadWorldAsync(Path root, CompiledReplay replay, Executor executor) {
        Path worldDir = root.resolve("world");
        if (!Files.isDirectory(worldDir)) {
            LOGGER.error("World directory is not a directory.");
            return CompletableFuture.completedFuture(Map.of());
        }

        List<String> meshNames;
        try (var filestream = Files.list(worldDir)) {

            meshNames = filestream
                    .filter(path -> path.toString().endsWith(".obj"))
                    .filter(Files::isRegularFile)
                    .map(path -> FilenameUtils.getBaseName(path.toString()))
                    .toList();

        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>(meshNames.size());
        Map<String, WorldMesh> result = new ConcurrentHashMap<>();

        for (var meshName : meshNames) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    result.put(meshName, loadWorldMesh(worldDir, meshName));
                } catch (Exception e) {
                    LOGGER.error("Error loading world mesh {}", meshName, e);
                }
                return null;
            }, executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    replay.getWorldMeshes().putAll(result);
                    return result;
                });
    }

    public static WorldMesh loadWorldMesh(Path worldDir, String name) throws IOException {
        Obj obj;
        try (BufferedReader reader = Files.newBufferedReader(worldDir.resolve(name + ".obj"))) {
            obj = ObjReader.read(reader);
        }

        WorldMesh.Meta meta;
        Path metaPath = worldDir.resolve(name + ".json");
        if (Files.isRegularFile(metaPath)) {
            try (BufferedReader reader = Files.newBufferedReader(metaPath)) {
                meta = GSON.fromJson(reader, WorldMesh.Meta.class);
            }
        } else {
            meta = new WorldMesh.Meta();
        }

        return new WorldMesh(obj, meta);
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

    private static void saveTexture(ReplayTexture texture, Path imagePath) throws IOException {
        Files.createDirectories(imagePath.getParent());
        texture.writeToFile(imagePath);
    }

    private static CompletableFuture<?> loadTexturesAsync(Path root, CompiledReplay replay, Executor executor) {
        List<Path> texturePaths;
        try (var stream = Files.walk(root)) {
            texturePaths = stream.filter(p -> p.toString().endsWith(".png")).toList();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>(texturePaths.size());
        Map<String, ReplayTexture> result = new ConcurrentHashMap<>();

        for (var texPath : texturePaths) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    result.put(root.relativize(texPath).toString(), loadTexture(texPath));
                } catch (Exception e) {
                    LOGGER.error("Error loading replay texture {}:", texPath, e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> replay.getTextures().putAll(result));
    }

    private static ReplayTexture loadTexture(Path imagePath) throws IOException {
        byte[] data;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(imagePath))) {
            data = in.readAllBytes();
        }
        return new PngReplayTexture(data);
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

    private static void loadMtls(Path root, CompiledReplay replay) {
        List<Path> mtlPaths;
        try (var stream = Files.walk(root)) {
            mtlPaths = stream.filter(path -> path.toString().endsWith(".mtl")).toList();
        } catch (IOException e) {
            LOGGER.error("Failed to retrieve mtl directory listing: ", e);
            return;
        }

        for (var path : mtlPaths) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                replay.getMtlLibs().put(root.relativize(path).toString(), new ArrayList<>(MtlReader.read(reader)));

            } catch (IOException e) {
                LOGGER.error("Error loading MTL library {}: ", path, e);
            }
        }
    }
}
