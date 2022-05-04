package org.scaffoldeditor.worldexport.replay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.VcapExporter;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;
import org.scaffoldeditor.worldexport.mat.TextureExtractor;
import org.scaffoldeditor.worldexport.mat.Material.Field;
import org.scaffoldeditor.worldexport.mat.Material.Field.FieldType;
import org.scaffoldeditor.worldexport.mat.ReplayTexture.NativeImageReplayTexture;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;

public class ReplayFile implements MaterialConsumer {
    protected ClientWorld world;
    protected VcapExporter worldExporter;

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A set of all the replay entities in this file.
     */
    public final Set<ReplayEntity<?>> entities = new HashSet<>();

    public final Map<String, Material> materials = new HashMap<>();
    public final Map<String, ReplayTexture> textures = new HashMap<>();

    private float fps = 20f;

    public ReplayFile(ClientWorld world, ChunkPos minChunk, ChunkPos maxChunk) {
        this.world = world;
        this.worldExporter = new VcapExporter(world, minChunk, maxChunk);
    }

    public ReplayFile(ClientWorld world, VcapExporter exporter) {
        this.world = world;
        this.worldExporter = exporter;

        if (exporter.world != world) {
            throw new IllegalArgumentException(
                    "Replay file cannot be constructed using an exporter with a different world instance.");
        }
    }

    @Override
    public void putMaterial(String name, Material mat) {
        materials.put(name, mat);
    }

    @Override
    public Material getMaterial(String name) {
        return materials.get(name);
    }

    @Override
    public void putTexture(String name, ReplayTexture texture) {
        this.textures.put(name, texture);
    }

    @Override
    public ReplayTexture getTexture(String name) {
        return this.textures.get(name);
    }

    public final ClientWorld getWorld() {
        return world;
    }

    public final VcapExporter getWorldExporter() {
        return worldExporter;
    }

    /**
     * Get the frame rate of animations in this file.
     * @return Frames per second.
     */
    public final float getFps() {
        return fps;
    }

    /**
     * Set the frame rate of animations in this file. (does not actually refactor animations)
     * @param fps Frames per second.
     */
    public void setFps(float fps) {
        this.fps = fps;
    }
    
    /**
     * <p>
     * Save this replay to a file.
     * </p>
     * <b>Warning:</b> Due to Vcap's need to capture the world texture, this method
     * blocks untill the next frame is rendered if not called on the render thread.
     * 
     * @param os Output stream to write to.
     * @throws IOException If an IO exception occurs while writing the file.
     */
    public void save(OutputStream os) throws IOException {
        LOGGER.info("Initializing replay serialization...");
        ZipOutputStream out = new ZipOutputStream(os);

        // META
        ReplayMeta meta = new ReplayMeta();
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

        out.putNextEntry(new ZipEntry("meta.json"));
        PrintWriter writer = new PrintWriter(out);
        writer.print(gson.toJson(meta));
        writer.flush();
        out.closeEntry();


        LOGGER.info("Writing world...");
        out.putNextEntry(new ZipEntry("world.vcap"));
        worldExporter.save(out);
        out.closeEntry();        

        LOGGER.info("Serializing entities...");
        for (ReplayEntity<?> ent : entities) {
            ent.generateMaterials(this);
            out.putNextEntry(new ZipEntry("entities/"+ent.getName()+".xml"));
            ReplayIO.serializeEntity(ent, new OutputStreamWriter(out));
            out.closeEntry();
        }

        LOGGER.info("Saving Materials...");
        for (String id : materials.keySet()) {
            Material mat = materials.get(id);
            checkForTexture(mat.color, id);
            checkForTexture(mat.metallic, id);
            checkForTexture(mat.normal, id);
            checkForTexture(mat.roughness, id);

            out.putNextEntry(new ZipEntry("mat/"+id+".json"));
            mat.serialize(out);
            out.closeEntry();
        }

        for (String id : textures.keySet()) {
            ReplayTexture tex = textures.get(id);

            out.putNextEntry(new ZipEntry("tex/"+id+".png"));
            tex.save(out);
            out.closeEntry();
        }

        // Dump world texture into replay
        NativeImageReplayTexture worldTex = new NativeImageReplayTexture(TextureExtractor.getAtlas());
        out.putNextEntry(new ZipEntry("tex/world.png"));
        worldTex.save(out);
        out.closeEntry();

        LOGGER.info("Finished writing replay file.");
        out.finish();
    }

    private boolean checkForTexture(Field field, String matName) {
        if (field != null && field.mode == FieldType.TEXTURE) {
            if (!field.getTexture().equals("world") && !this.textures.containsKey(field.getTexture())) {
                LogManager.getLogger().warn("Material: '{}' references missing texture: {}", matName, field.getTexture());
                return false;
            }
        }
        return true;
    }

    public CompletableFuture<Void> saveAsync(OutputStream os) {
        return CompletableFuture.runAsync(() -> {
            try {
                save(os);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public void save(File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        save(out);
        out.close();
    }
}
