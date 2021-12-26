package org.scaffoldeditor.worldexport.replay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.VcapExporter;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;

public class ReplayFile {
    protected ClientWorld world;
    protected VcapExporter worldExporter;

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A set of all the replay entities in this file.
     */
    public final Set<ReplayEntity<?>> entities = new HashSet<>();
    private float fps = 30f;

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

        LOGGER.info("Writing world...");
        out.putNextEntry(new ZipEntry("world.vcap"));
        worldExporter.save(out);
        out.closeEntry();

        LOGGER.info("Serializing entities...");
        for (ReplayEntity<?> ent : entities) {
            out.putNextEntry(new ZipEntry("entities/"+ent.getName()+".xml"));
            ReplayIO.serializeEntity(ent, new OutputStreamWriter(out));
            out.closeEntry();
        }

        LOGGER.info("Finished writing replay file.");
        out.finish();
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
