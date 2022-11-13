package org.scaffoldeditor.worldexport.replay;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.scaffoldeditor.worldexport.VcapExporter;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;

public class ReplayFile extends BaseReplayFile<ReplayEntity<?>> implements MaterialConsumer {
    protected ClientWorld world;
    protected VcapExporter worldExporter;

    /**
     * A set of all the replay entities in this file.
     */
    public final Set<ReplayEntity<?>> entities = new HashSet<>();

    public final Map<String, Material> materials = new HashMap<>();
    public final Map<String, ReplayTexture> textures = new HashMap<>();
    public final ReplayMeta meta = new ReplayMeta();

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

    @Override
    protected void saveWorld(OutputStream out) throws IOException {
        worldExporter.save(out);
    }

    @Override
    protected void preserializeEntity(ReplayEntity<?> entity) {
        entity.generateMaterials(this);
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

    @Override
    public Set<ReplayEntity<?>> getEntities() {
        return entities;
    }

    @Override
    public Map<String, Material> getMaterials() {
        return materials;
    }

    @Override
    public Map<String, ReplayTexture> getTextures() {
        return textures;
    }

    @Override
    public ReplayMeta getMeta() {
        return meta;
    }
    
}
