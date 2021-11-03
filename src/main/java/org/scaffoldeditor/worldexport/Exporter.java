package org.scaffoldeditor.worldexport;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.export.BlockExporter;
import org.scaffoldeditor.worldexport.export.ExportContext;
import org.scaffoldeditor.worldexport.export.Material;
import org.scaffoldeditor.worldexport.export.MeshWriter;
import org.scaffoldeditor.worldexport.export.TextureExtractor;
import org.scaffoldeditor.worldexport.export.ExportContext.ModelEntry;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldAccess;

public final class Exporter {
    private Exporter() {};
    private static Logger LOGGER = LogManager.getLogger();

    public static enum FrameType {
        INTRACODED,
        PREDICTED
    }

    /**
     * Export a voxel capture.
     * <p><b>Note:</b> Due to the need to retrieve the atlas texture from the GPU,
     * this method blocks untill the next frame is rendered. Do not call from a thread
     * that blocks the render thread (like the main game thread), or the game will halt.</p>
     * @param world World to capture.
     * @param minChunk Bounding box min.
     * @param maxChunk Bounding box max.
     * @param os Output stream to write to.
     * @throws IOException If an IO exception occurs.
     * @throws TimeoutException If retrieving the atlas times out.
     * @throws ExecutionException If there's an error retrieving the atlas.
     */
    public static void Export(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk, OutputStream os) throws IOException, ExecutionException, TimeoutException {
        ExportContext context = new ExportContext();
        ZipOutputStream out = new ZipOutputStream(os);
        
        Future<NativeImage> atlasFuture = TextureExtractor.getAtlas();
        
        LOGGER.info("Reading world");
        NbtList frames = new NbtList();
        frames.add(writeFrame(BlockExporter.exportStill(world, minChunk, maxChunk, context), FrameType.INTRACODED, 0));
        NbtCompound worldData = new NbtCompound();
        worldData.put("frames", frames);
        
        ZipEntry worldEntry = new ZipEntry("world.dat");
        out.putNextEntry(worldEntry);
        NbtIo.write(worldData, new DataOutputStream(out));
        out.closeEntry();

        Random random = new Random();
        for (ModelEntry model : context.models.keySet()) {
            String id = context.models.get(model);
            LOGGER.info("Writing mesh: "+id);

            Obj mesh = MeshWriter.writeBlockMesh(model, random);

            ZipEntry modelEntry = new ZipEntry("mesh/"+id+".obj");
            out.putNextEntry(modelEntry);
            ObjWriter.write(mesh, out);
            out.closeEntry();
        }
        writeMats(out);
        writeAtlas(atlasFuture, out);
        out.close();
    }

    private static NbtCompound writeFrame(NbtList sections, FrameType type, double time) {
        NbtCompound frame = new NbtCompound();
        frame.put("sections", sections);
        frame.putByte("type", (byte) type.ordinal());
        frame.putDouble("time", time);
        return frame;
    }

    private static void writeAtlas(Future<NativeImage> atlasFuture, ZipOutputStream out) throws IOException, ExecutionException, TimeoutException {
        NativeImage atlas;
        File atlasTemp = File.createTempFile("atlas-", ".png"); // For some reason, NativeImage can only write to a file; not an output stream.
        atlasTemp.deleteOnExit();
        try {
            atlas = atlasFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        atlas.writeTo(atlasTemp);

        ZipEntry worldTex = new ZipEntry("tex/world.png");
        out.putNextEntry(worldTex);
        Files.copy(atlasTemp.toPath(), out);
        out.closeEntry();

        LogManager.getLogger().info("Transfered data from {}", atlasTemp);
    }

    private static void writeMats(ZipOutputStream out) throws IOException {
        Material opaque = new Material();
        opaque.color = new Material.Field("world");
        opaque.roughness = new Material.Field(.7);
        opaque.useVertexColors = true;
        
        out.putNextEntry(new ZipEntry("mat/"+MeshWriter.WORLD_MAT+".json"));
        opaque.serialize(out);
        out.closeEntry();

        Material transparent = new Material();
        transparent.color = new Material.Field("world");
        transparent.roughness = new Material.Field(.7);
        transparent.transparent = true;
        transparent.useVertexColors = true;

        out.putNextEntry(new ZipEntry("mat/"+MeshWriter.TRANSPARENT_MAT+".json"));
        transparent.serialize(out);
        out.closeEntry();

    }
}
