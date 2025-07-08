package com.igrium.worldexport.replay;

import com.google.gson.Gson;
import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.ObjWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplayIO {

    private static final Gson GSON = new Gson();

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
        try(var jsonOut = Files.newBufferedWriter(worldDir.resolve(name + ".json"))) {
            GSON.toJson(mesh.meta(), jsonOut);
        }

        try(var objOut = Files.newBufferedWriter(worldDir.resolve(name + ".obj"))) {
            ObjWriter.write(mesh.obj(), objOut);
        }
    }
}
