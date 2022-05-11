package org.scaffoldeditor.worldexport.replay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;
import org.scaffoldeditor.worldexport.mat.Field;
import org.scaffoldeditor.worldexport.mat.Field.FieldType;

public abstract class BaseReplayFile<T extends BaseReplayEntity> {
    public abstract Set<T> getEntities();
    public abstract Map<String, ? extends Material> getMaterials();
    public abstract Map<String, ? extends ReplayTexture> getTextures();
    
    protected abstract void preserializeEntity(T entity);
    
    // public abstract 
    public abstract ReplayMeta getMeta();

    protected abstract void saveWorld(OutputStream out) throws IOException;

    private Logger LOGGER = LogManager.getLogger();

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
        ReplayMeta meta = getMeta();
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
        saveWorld(out);
        out.closeEntry();        

        LOGGER.info("Serializing entities...");
        for (T ent : getEntities()) {
            preserializeEntity(ent);
            out.putNextEntry(new ZipEntry("entities/"+ent.getName()+".xml"));
            ReplayIO.serializeEntity(ent, new OutputStreamWriter(out));
            out.closeEntry();
        }

        LOGGER.info("Saving Materials...");
        for (String id : getMaterials().keySet()) {
            Material mat = getMaterials().get(id);
            checkForTexture(mat.getColor(), id);
            checkForTexture(mat.getMetallic(), id);
            checkForTexture(mat.getNormal(), id);
            checkForTexture(mat.getRoughness(), id);

            out.putNextEntry(new ZipEntry("mat/"+id+".json"));
            mat.serialize(out);
            out.closeEntry();
        }

        for (String id : getTextures().keySet()) {
            ReplayTexture tex = getTextures().get(id);

            out.putNextEntry(new ZipEntry("tex/"+id+".png"));
            tex.save(out);
            out.closeEntry();
        }

        LOGGER.info("Finished writing replay file.");
        out.finish();
    }

    private boolean checkForTexture(Field field, String matName) {
        if (field != null && field.mode == FieldType.TEXTURE) {
            if (!field.getTexture().equals("world") && !this.getTextures().containsKey(field.getTexture())) {
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
