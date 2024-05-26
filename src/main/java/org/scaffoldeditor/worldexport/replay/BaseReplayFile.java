package org.scaffoldeditor.worldexport.replay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;
import org.scaffoldeditor.worldexport.mat.TextureSerializer;
import org.scaffoldeditor.worldexport.Constants;
import org.scaffoldeditor.worldexport.mat.Field;
import org.scaffoldeditor.worldexport.mat.Field.FieldType;
import org.scaffoldeditor.worldexport.replaymod.util.ExportPhase;
import org.scaffoldeditor.worldexport.util.ZipEntryOutputStream;

public abstract class BaseReplayFile<T extends BaseReplayEntity> {
    public abstract Set<T> getEntities();
    public abstract Map<String, ? extends Material> getMaterials();
    public abstract Map<String, ? extends ReplayTexture> getTextures();
    
    protected abstract void preserializeEntity(T entity);
    
    // public abstract 
    public abstract ReplayMeta getMeta();

    protected abstract void saveWorld(OutputStream out, Consumer<String> phaseConsumer) throws IOException;

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
        save(os, null);
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
    public void save(OutputStream os, @Nullable Consumer<String> phaseConsumer) throws IOException {
        if (phaseConsumer == null) phaseConsumer = LOGGER::info;

        phaseConsumer.accept(ExportPhase.SERIALIZATION);
        ZipOutputStream out = new ZipOutputStream(os);


        // META
        ReplayMeta meta = new ReplayMeta(getMeta());
        meta.version = Constants.REPLAY_FORMAT_VERSION; // Make sure we export the right version.

        out.putNextEntry(new ZipEntry("meta.json"));
        PrintWriter writer = new PrintWriter(out);
        writer.print(ReplayMeta.toJson(meta));
        writer.flush();
        out.closeEntry();


        phaseConsumer.accept(ExportPhase.WORLD);
        out.putNextEntry(new ZipEntry("world.vcap"));
        saveWorld(out, phaseConsumer);
        out.closeEntry();        

        phaseConsumer.accept(ExportPhase.ENTITIES);
        for (T ent : getEntities()) {
            preserializeEntity(ent);
            out.putNextEntry(new ZipEntry("entities/"+ent.getName()+".xml"));
            ReplayIO.serializeEntity(ent, new OutputStreamWriter(out));
            out.closeEntry();
        }

        phaseConsumer.accept(ExportPhase.MATERIALS);
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

        TextureSerializer serializer = new TextureSerializer(
                filename -> new ZipEntryOutputStream(out, new ZipEntry("tex/" + filename)));
        serializer.save(getTextures());

        phaseConsumer.accept(ExportPhase.FINISHED);
        out.finish();
    }

    private void checkForTexture(Field field, String matName) {
        if (field != null && field.mode == FieldType.TEXTURE) {
            if (!field.getTexture().equals("world") && !this.getTextures().containsKey(field.getTexture())) {
                LogManager.getLogger().warn("Material: '{}' references missing texture: {}", matName, field.getTexture());
            }
        }
    }

    public void save(File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        save(out);
        out.close();
    }
}
