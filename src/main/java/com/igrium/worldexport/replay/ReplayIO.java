package com.igrium.worldexport.replay;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.tex.PngReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.ReplayTexture;
import com.igrium.worldexport.util.ExceptionUtils;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class ReplayIO {
    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayIO.class);

    public record ZipFileRoot(FileSystem fileSystem, Path root) implements Closeable {

        @Override
        public void close() throws IOException {
            fileSystem.close();
        }
    }

    public static ZipFileRoot openZipFile(Path zipFile, boolean create) throws IOException {
        Map<String, String> env = new HashMap<>();
        if (create) {
            env.put("create", "true");
        }
        FileSystem fs = FileSystems.newFileSystem(zipFile, env);

        Iterator<Path> roots = fs.getRootDirectories().iterator();
        if (!roots.hasNext()) {
            throw new IOException("File system has no root directories.");
        }
        Path root = roots.next();

        return new ZipFileRoot(fs, root);
    }

    public static CompletableFuture<?> saveReplayAsync(Path root, CompiledReplay replay, Executor executor) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return saveWorldAsync(root, replay, executor)
                .thenCompose(v -> saveEntitiesAsync(root, replay, executor))
                .thenCompose(v -> saveTexturesAsync(root, replay, executor))
                .thenRun(() -> saveMtls(root, replay));
    }

    public static CompletableFuture<?> saveReplayZip(Path zipFile, CompiledReplay replay, Executor executor) {
        ZipFileRoot zip;
        try {
            zip = openZipFile(zipFile, true);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        return saveReplayAsync(zip.root, replay, executor).whenComplete((rex, ex) -> {
            try {
                zip.close();
            } catch (IOException e) {
                throw ExceptionUtils.sneakyThrow(e); // Will get handled by the completablefuture.
            }
        });
    }

    public static CompletableFuture<CompiledReplay> loadReplayAsync(Path root, Executor executor) {
        CompiledReplay replay = new CompiledReplay();
        return loadWorldAsync(root, replay, executor)
                .thenCompose(v -> loadEntitiesAsync(root, replay, executor))
                .thenCompose(v -> loadTexturesAsync(root, replay, executor))
                .thenRun(() -> loadMtls(root, replay))
                .thenApply(v -> replay);
    }

    public static CompletableFuture<CompiledReplay> loadReplayZip(Path zipFile, Executor executor) {
        ZipFileRoot zip;
        try {
            zip = openZipFile(zipFile, false);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        return loadReplayAsync(zip.root, executor).whenComplete((res, ex) -> {
            try {
                zip.close();
            } catch (IOException e) {
                throw ExceptionUtils.sneakyThrow(e); // Will get handled by the completablefuture.
            }
        });
    }



    /**
     * Maps world mesh names to their metadata. All meshes must be present, even with empty metadata.
     */
    public static TypeToken<Map<String, WorldMesh.Meta>> worldJsonType = new TypeToken<>() {};

    /**
     * Maps entity names to their parent declaration. All entities must be present, even with empty parents.
     */
    public static TypeToken<Map<String, Map<String, String>>> entityJsonType = new TypeToken<>() {};

    private static CompletableFuture<?> saveEntitiesAsync(Path dir, CompiledReplay replay, Executor executor) {
        List<CompletableFuture<?>> entityFutures = new ArrayList<>(replay.getEntities().size());
        Map<String, Map<String, String>>  entityJson = new ConcurrentHashMap<>();

        for (var entEntry : replay.getEntities().entrySet()) {
            entityFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    saveEntity(dir, entEntry.getValue(), entEntry.getKey(), entityJson);
                } catch (IOException e) {
                    LOGGER.error("Error saving entity {}:", entEntry.getKey(), e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(entityFutures.toArray(new CompletableFuture[0])).thenRunAsync(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("entities.json"))) {
                GSON.toJson(entityJson, writer);
            } catch (IOException e) {
                throw ExceptionUtils.sneakyThrow(e);
            }
        }, executor);
    }

    private static void saveEntity(Path dir, CapturedEntity entity, String name, Map<String, Map<String, String>> entityJson) throws IOException {

        if (!entity.getModelParts().isEmpty()) {
            try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve(name + ".obj"))) {
                entity.writeObj(writer);
            }
        }

        Path animPath = dir.resolve(name + ".anim");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(animPath)))) {
            entity.writeAnimFile(out);
        }

        entityJson.put(name, Map.copyOf(entity.getParents()));
    }

    private static CompletableFuture<?> loadEntitiesAsync(Path dir, CompiledReplay replay, Executor executor) {
        Map<String, Map<String, String>> entityJson;
        try (BufferedReader reader = Files.newBufferedReader(dir.resolve("entities.json"))) {
            entityJson = GSON.fromJson(reader, entityJsonType);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>(entityJson.size());
        Map<String, CapturedEntity> entities = new ConcurrentHashMap<>();

        for (var entEntry : entityJson.entrySet()) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    entities.put(entEntry.getKey(), loadEntity(dir, entEntry.getKey(), entEntry.getValue()));
                } catch (IOException e) {
                    LOGGER.error("Error loading entity {}: ", entEntry.getKey(), e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            replay.getEntities().putAll(entities);
        });
    }

    private static CapturedEntity loadEntity(Path dir, String name, Map<String, String> parents) throws IOException {
        Path objPath = dir.resolve(name + ".obj");
        CapturedEntity entity = new CapturedEntity();
        if (Files.isRegularFile(objPath)) {
            try (BufferedReader reader = Files.newBufferedReader(objPath)) {
                entity.readObj(reader);
            }
        }

        Path animPath = dir.resolve(name + ".anim");
        if (Files.isRegularFile(animPath)) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(animPath)))) {
                entity.readAnimFile(in);
            }
        }

        entity.getParents().putAll(parents);
        return entity;
    }

    private static CompletableFuture<?> saveWorldAsync(Path dir, CompiledReplay replay, Executor executor) {
        List<CompletableFuture<?>> worldFutures = new ArrayList<>(replay.getWorldMeshes().size());
        Map<String, WorldMesh.Meta> worldJson = new ConcurrentHashMap<>();

        for (var meshEntry : replay.getWorldMeshes().entrySet()) {
            String name = meshEntry.getKey();
            worldFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    saveWorldMesh(dir, meshEntry.getValue(), name, worldJson);
                } catch (Exception e) {
                    LOGGER.error("Error saving world mesh {}:", name, e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(worldFutures.toArray(new CompletableFuture[0])).thenRunAsync(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("world.json"))) {
                GSON.toJson(worldJson, writer);
            } catch (IOException e) {
                throw ExceptionUtils.sneakyThrow(e);
            }
        }, executor);
    }

    private static void saveWorldMesh(Path dir, WorldMesh mesh, String name, Map<String, WorldMesh.Meta> worldJson) throws IOException {
        if (mesh.obj().getNumVertices() == 0)
            return;

        try (var objOut = Files.newBufferedWriter(dir.resolve(name + ".obj"))) {
            ObjWriter.write(mesh.obj(), objOut);
        }

        worldJson.put(name, mesh.meta());
    }

    private static CompletableFuture<Map<String, WorldMesh>> loadWorldAsync(Path dir, CompiledReplay replay, Executor executor) {
        Map<String, WorldMesh.Meta> worldJson;
        try (BufferedReader reader = Files.newBufferedReader(dir.resolve("world.json"))) {
            worldJson = GSON.fromJson(reader, worldJsonType);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        if (worldJson.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        Map<String, WorldMesh> result = new ConcurrentHashMap<>();

        for (var meshEntry : worldJson.entrySet()) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    result.put(meshEntry.getKey(), loadWorldMesh(dir, meshEntry.getKey(), meshEntry.getValue()));
                } catch (IOException e) {
                    LOGGER.error("Error loading world mesh {}:", meshEntry.getKey(), e);
                }
            }, executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
            replay.getWorldMeshes().putAll(result);
            return result;
        });
    }

    private static WorldMesh loadWorldMesh(Path dir, String name, WorldMesh.Meta meta) throws IOException {
        Obj obj;
        try (BufferedReader reader = Files.newBufferedReader(dir.resolve(name + ".obj"))) {
            obj = ObjReader.read(reader);
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
            try {
                saveMtl(root, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.error("Error saving mtl {}: ", entry.getKey(), e);
            }
        }
    }

    private static void saveMtl(Path root, String name, Collection<? extends ReplayMtl> rMtls) throws IOException {
        if (!name.endsWith(".mtl")) {
            LOGGER.warn("Mtl {} should end with '.mtl'", name);
            name += ".mtl";
        }

        List<Mtl> mtls = new ArrayList<>(rMtls.size());
        Map<String, Map<String, ReplayMtl.Property<?>>> properties = new HashMap<>(rMtls.size());

        for (ReplayMtl rMtl : rMtls) {
            mtls.add(rMtl.mtl());
            if (!rMtl.properties().isEmpty()) {
                properties.put(rMtl.getName(), rMtl.properties());
            }
        }

        try(var writer = Files.newBufferedWriter(root.resolve(name))) {
            MtlWriter.write(mtls, writer);
        }

        if (!properties.isEmpty()) {
            try(var writer = Files.newBufferedWriter(root.resolve(name + ".json"))) {
                GSON.toJson(properties, writer);
            }
        }
    }

    private static final TypeToken<Map<String, Map<String, ReplayMtl.Property<?>>>> mtlPropertyType = new TypeToken<>() {};

    private static void loadMtls(Path root, CompiledReplay replay) {
        List<Path> mtlFiles;
        try (var s = Files.walk(root).filter(f -> f.toString().endsWith(".mtl") && Files.isRegularFile(f))) {
            mtlFiles = s.toList();
        } catch (IOException e) {
            // Will get caught by the completable future this is a part of, so no need to handle.
            // Also this code shouldn't even get called if the folder doesn't exist.
            throw ExceptionUtils.sneakyThrow(e);
        }

        for (Path mtlFile : mtlFiles) {
            String name = mtlFile.getFileName().toString();
            try {
                replay.getMtlLibs().put(name, loadMtl(root, name));
            } catch (Exception e) {
                LOGGER.error("Error loading mtl {}: ", name, e);
            }
        }
    }

    private static List<ReplayMtl> loadMtl(Path root, String name) throws IOException {
        List<Mtl> mtls;
        try(BufferedReader reader = Files.newBufferedReader(root.resolve(name))) {
            mtls = MtlReader.read(reader);
        }

        Map<String, Map<String, ReplayMtl.Property<?>>> properties;
        Path propertyFile = root.resolve(name + ".json");
        if (Files.exists(propertyFile)) {
            try (BufferedReader reader = Files.newBufferedReader(propertyFile)) {
                properties = GSON.fromJson(reader, mtlPropertyType);
            }
        } else {
            properties = Map.of();
        }

        List<ReplayMtl> result = new ArrayList<>(mtls.size());
        for (var mtl : mtls) {
            Map<String, ReplayMtl.Property<?>> props = properties.get(mtl.getName());
            result.add(new ReplayMtl(mtl, props != null ? props : new HashMap<>()));
        }
        return result;
    }

//    private static void loadMtls(Path root, CompiledReplay replay) {
//        List<Path> mtlPaths;
//        try (var stream = Files.walk(root)) {
//            mtlPaths = stream.filter(path -> path.toString().endsWith(".mtl")).toList();
//        } catch (IOException e) {
//            LOGGER.error("Failed to retrieve mtl directory listing: ", e);
//            return;
//        }
//
//        for (var path : mtlPaths) {
//            try (BufferedReader reader = Files.newBufferedReader(path)) {
//                replay.getMtlLibs().put(root.relativize(path).toString(), new ArrayList<>(MtlReader.read(reader)));
//
//            } catch (IOException e) {
//                LOGGER.error("Error loading MTL library {}: ", path, e);
//            }
//        }
//    }
}
