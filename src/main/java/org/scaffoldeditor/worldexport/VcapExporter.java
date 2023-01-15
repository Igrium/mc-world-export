package org.scaffoldeditor.worldexport;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.scaffoldeditor.worldexport.mat.TextureExtractor;
import org.scaffoldeditor.worldexport.vcap.ExportContext;
import org.scaffoldeditor.worldexport.vcap.Frame;
import org.scaffoldeditor.worldexport.vcap.IFrame;
import org.scaffoldeditor.worldexport.vcap.MeshWriter;
import org.scaffoldeditor.worldexport.vcap.PFrame;
import org.scaffoldeditor.worldexport.vcap.VcapMeta;
import org.scaffoldeditor.worldexport.vcap.VcapSettings;
import org.scaffoldeditor.worldexport.vcap.model.MaterialProvider;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider.ModelInfo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;

import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;
import de.javagl.obj.ReadableObj;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Captures and exports a voxel capture file. Each instance
 * represents one file being exported.
 */
public class VcapExporter {
    private static Logger LOGGER = LogManager.getLogger();

    /**
     * The world this exporter is exporting.
     */
    public final WorldAccess world;

    public final List<Frame> frames = new ArrayList<>();
    public final ExportContext context;
    
    public VcapSettings getSettings() {
        return context.getSettings();
    }

    /**
     * Create a new export instance.
     * 
     * @param world    World to capture.
     * @param minChunk Bounding box min (inclusive).
     * @param maxChunk Bounding box max (exclusive).
     */
    public VcapExporter(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk) {
        this.world = world;
        context = new ExportContext();        
        setBBox(minChunk, maxChunk);

    }

    /**
     * Get the bounding box min point.
     * @return Bounding box min (inclusive)
     */
    public ChunkPos getMinChunk() {
        return getSettings().getMinChunk();
    }
    
    /**
     * Get the bounding box max point.
     * @return Bounding box max (exclusive).
     */
    public ChunkPos getMaxChunk() {
        return getSettings().getMaxChunk();
    }
    
    /**
     * Set the bounding box.
     * @param minChunk Bounding box min (inclusive).
     * @param maxChunk Bounding box max (exclusive).
     */
    public void setBBox(ChunkPos minChunk, ChunkPos maxChunk) {
        getSettings().setBBox(minChunk, maxChunk);
    }

    /**
     * <p>
     * Save the export instance to a file asynchronously.
     * </p>
     * <p>
     * <b>Warning:</b> Due to the need to extract the atlas texture from
     * the GPU, this future will not complete untill the next frame is rendered.
     * Do not block on a thread that will stop the rendering of the next frame.
     * 
     * @param os Output stream to write to.
     * @return A future that completes when the file has been saved.
     */
    public CompletableFuture<Void> saveAsync(OutputStream os) {
        return CompletableFuture.runAsync(() -> {
            try {
                save(os);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * <p>
     * Save the export instance to a file.
     * </p>
     * <p>
     * <b>Warning:</b> Due to the need to extract the atlas texture from
     * the GPU, if this method is not called on the render thread, it will block
     * until the next frame is rendered.
     * 
     * @param os Output stream to write to.
     * @throws IOException If an IO exception occurs while writing the file
     *                     or extracting the texture.
     */
    public void save(OutputStream os) throws IOException {
        ZipOutputStream out = new ZipOutputStream(os);

        // WORLD
        LOGGER.info("Compiling frames...");
        NbtList frames = new NbtList();
        this.frames.forEach(frame -> frames.add(frame.getFrameData()));
        NbtCompound worldData = new NbtCompound();
        worldData.put("frames", frames);

        out.putNextEntry(new ZipEntry("world.dat"));
        NbtIo.write(worldData, new DataOutputStream(out));
        out.closeEntry();

        Map<String, MaterialProvider> materials = new HashMap<>(context.materials);

        // MODELS
        int numLayers = 0;

        for (Map.Entry<String, ModelProvider> entry : context.models.entrySet()) {
            String id = entry.getKey();
            ModelProvider modelProvider = entry.getValue();

            LOGGER.debug("Writing mesh: "+id);
            ModelInfo model = modelProvider.writeMesh();
            writeMesh(model.mesh(), id, out);

            if (model.numLayers() > numLayers) {
                numLayers = model.numLayers();
            }

            model.materials().forEach(materials::putIfAbsent);
        }

        // for (BlockModelEntry model : context.models.keySet()) {
        //     String id = context.models.get(model);
        //     LOGGER.debug("Writing mesh: "+id);

        //     MeshInfo info = MeshWriter.writeBlockMesh(model, random, context.worldMaterials::add);
        //     writeMesh(info.mesh, id, out);

        //     if (info.numLayers > numLayers) {
        //         numLayers = info.numLayers;
        //     }
        // }

        // for (String id : context.extraModels.keySet()) {
        //     LOGGER.debug("Writing fluid mesh: "+id);
        //     writeMesh(context.extraModels.get(id), id, out);
        // }

        // Fluid meshes assume empty mesh is written.
        writeMesh(Objs.create(), MeshWriter.EMPTY_MESH, out);

        // MATERIALS
        // for (VcapWorldMaterial mat : context.worldMaterials) {
        //     out.putNextEntry(new ZipEntry("mat/"+mat.getName()+".json"));
        //     mat.writeMaterial().serialize(out);
        //     out.closeEntry();
        // }

        for (Map.Entry<String, MaterialProvider> entry : materials.entrySet()) {
            out.putNextEntry(new ZipEntry("mat/"+entry.getKey()+".json"));
            entry.getValue().writeMaterial().serialize(out);
            out.closeEntry();
        }

        // TEXTURE ATLAS
        CompletableFuture<NativeImage> textureExtraction = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            LOGGER.info("Extracting world texture...");
            textureExtraction.complete(TextureExtractor.getAtlas());
        });

        out.putNextEntry(new ZipEntry("tex/world.png"));
        try {
            TextureExtractor.writeTextureToFile(textureExtraction.get(), out);
        } catch (InterruptedException e) {
            throw new RuntimeException("Texture extractor was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IOException("Error extracting world atlas texture.", e);
        }
        out.closeEntry();
        
        // META
        LOGGER.info("Writing Vcap metadata.");
        VcapMeta meta = new VcapMeta(numLayers);
        context.getIDMapping(meta.blockTypes);
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        
        out.putNextEntry(new ZipEntry("meta.json"));
        PrintWriter writer = new PrintWriter(out);
        writer.print(gson.toJson(meta));
        writer.flush();
        out.closeEntry();
        

        LOGGER.info("Finished writing Vcap.");
        out.finish();
    }

    private static void writeMesh(ReadableObj mesh, String id, ZipOutputStream out) throws IOException {
        ZipEntry modelEntry = new ZipEntry("mesh/"+id+".obj");
        out.putNextEntry(modelEntry);   
        ObjWriter.write(mesh, out);
        out.closeEntry();
    }

    /**
     * <p>
     * Capture an intracoded frame and add it to the vcap.
     * </p>
     * <p>
     * Warning: depending on the size of the capture, this may
     * take multiple seconds.
     * </p>
     * 
     * @param time Time stamp of the frame, in seconds since the beginning
     *             of the animation.
     * @return The frame.
     */
    public IFrame captureIFrame(double time) {
        IFrame iFrame = IFrame.capture(world, getSettings().getMinChunk(), getSettings().getMaxChunk(), context, time);
        frames.add(iFrame);
        return iFrame;
    }

    /**
     * Capture a predicted frame and add it to the file.
     * 
     * @param time   Timestamp of the frame, in seconds since the beginning of the
     *               animation.
     * @param blocks A set of blocks to include data for in the frame.
     *               All ajacent blocks will be queried, and if they are found to
     *               have changed, they are also included in the frame.
     * @return The captured frame.
     */
    public PFrame capturePFrame(double time, Set<BlockPos> blocks) {
        return capturePFrame(time, blocks, world);
    }

    /**
     * Capture a predicted frame, sampling the blocks from the given world, and add
     * it to the file.
     * 
     * @param time   Timstamp of the frame, in seconds since the beginning of the
     *               animation.
     * @param blocks A set of blocks to include data for in the frame. All ajacent
     *               blocks will be queried, and if they are found to have changed,
     *               they are also included in the frame.
     * @param world  The world to query. Should contain a block structure equal to
     *               that in this exporter.
     * @return The captured frame.
     */
    public PFrame capturePFrame(double time, Set<BlockPos> blocks, WorldAccess world) {
        PFrame pFrame = PFrame.capture(world, blocks, time, frames.get(frames.size() - 1), context);
        frames.add(pFrame);
        return pFrame;
    }

    private Date captureStartTime;
    private Set<BlockPos> updateCache = new HashSet<>();
    private ClientBlockPlaceCallback listener = new ClientBlockPlaceCallback() {
        private boolean isCaptureQueued = false;

        @Override
        public void place(BlockPos t, BlockState u, World world) {
            updateCache.add(t);
            if (!isCaptureQueued) {
                RenderSystem.recordRenderCall(() -> {
                    capturePFrame((new Date().getTime() - captureStartTime.getTime()) / 1000d, updateCache);
                    updateCache.clear();
                    isCaptureQueued = false;
                });
            }
            isCaptureQueued = true;
        }
    };

    /**
     * Listen for and record changes to the world.
     * @param startTime Start time of the animation. Current time if null.
     */
    public void listen(@Nullable Date startTime) {
        if (startTime == null) startTime = new Date();

        if (captureStartTime == null) {
            captureStartTime = startTime;
        }

        ReplayExportMod.getInstance().onBlockUpdated(listener);
    }

    public void stopListen() {
        ReplayExportMod.getInstance().removeOnBlockUpdated(listener);
    }
}
