package org.scaffoldeditor.worldexport.vcap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.scaffoldeditor.worldexport.util.FloodFill;
import org.scaffoldeditor.worldexport.util.MeshComparator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import de.javagl.obj.Obj;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Contains various values passed around throughout the export process.
 */
public class ExportContext {
    public static class ModelEntry {
        /**
         * The baked model to use.
         */
        public final BakedModel model;

        /**
         * A 6-element array dictating which faces are visible,
         * in the order NORTH, SOUTH, EAST, WEST, UP, DOWN
         */
        public final boolean[] faces;

        public final boolean transparent;

        @Nullable
        public final BlockState blockState;

        /**
         * Create a model entry.
         * @param model The baked model to use.
         * @param faces A 6-element array dictating which faces are visible,
         * in the order NORTH, SOUTH, EAST, WEST, UP, DOWN
         * @param blockState The blockstate of the model.
         */
        public ModelEntry(BakedModel model, boolean[] faces, boolean transparent, @Nullable BlockState blockState) {
            this.model = model;
            this.faces = faces;
            this.transparent = transparent;
            this.blockState = blockState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(model, Arrays.hashCode(faces), hashBlock(blockState));
        }

        @Override
        public boolean equals(Object obj) {
            return hashCode() == obj.hashCode();
        }

        private static int hashBlock(BlockState state) {
            Identifier id = Registry.BLOCK.getId(state.getBlock());
            return Objects.hash(id, state.getEntries());
        }
    
    }

    /**
     * The model entries in the cache and their IDs.
     */
    public final BiMap<ModelEntry, String> models = HashBiMap.create();

    /**
     * The fluid meshes in the export context.
     */
    public final BiMap<String, Obj> extraModels = HashBiMap.create();

    private VcapSettings settings = new VcapSettings();
    private MeshComparator meshComparator = new MeshComparator();

    private FloodFill.Builder<?> floodFill = FloodFill.recursive();

    public VcapSettings getSettings() {
        return settings;
    }

    public void setSettings(VcapSettings settings) {
        this.settings = settings;
    }

    public MeshComparator getMeshComparator() {
        return meshComparator;
    }

    public void setMeshComparator(MeshComparator meshComparator) {
        this.meshComparator = meshComparator;
    }

    public FloodFill.Builder<?> getFloodFill() {
        return floodFill.copy();
    }

    public void setFloodFill(FloodFill.Builder<?> floodFill) {
        this.floodFill = floodFill;
    }

    /**
     * Add an "extra" model to the vcap file.
     * @param desiredName The name to use.
     * @param model The model to add.
     * @return The name the model was given.
     */
    public synchronized String addExtraModel(String desiredName, Obj model) {
        if (extraModels.containsValue(model)) {
            return extraModels.inverse().get(model);
        }
        String name = makeNameUnique(desiredName);
        extraModels.put(name, model);
        return name;
    }

    /**
     * Generate the ID of a model entry. Returns the current ID if it already exists.
     * @param entry Entry to generate the ID for.
     * @param name Base name of model.
     * @return ID.
     */
    public synchronized String getID(ModelEntry entry, String name) {
        String id = models.get(entry);
        if (id == null) {
            if (name == null) name = String.valueOf(entry.model.hashCode());
            id = makeNameUnique(name+Arrays.toString(entry.faces));
            models.put(entry, id);
        }
        return id;
    }

    public synchronized String makeNameUnique(String name) {
        while (models.containsValue(name) || extraModels.containsKey(name)) {
            name = iterateName(name);
        }
        return name;
    }

    public String getID(ModelEntry entry) {
        return getID(entry, null);
    }

    /**
     * Get a mapping of model IDs and the blockstate they belong to. For use in
     * <code>meta.json</code>
     * 
     * @param map Map to add to.
     * @see ExportContext#getIDMapping()
     */
    public void getIDMapping(Map<String, String> map) {
        for (ModelEntry entry : models.keySet()) {
            map.put(models.get(entry), Registry.BLOCK.getId(entry.blockState.getBlock()).toString());
        }
    }

    /**
     * Get a mapping of model IDs and the blockstate they belong to. For use in
     * <code>meta.json</code>
     * 
     * @return The generated map.
     * @see ExportContext#getIDMapping(Map)
     */
    public Map<String, String> getIDMapping() {
        Map<String, String> map = new HashMap<>();
        getIDMapping(map);
        return map;
    }

    private static Pattern lastIntPattern = Pattern.compile("[^0-9]+([0-9]+)$");

    private String iterateName(String name) {
        Matcher matcher = lastIntPattern.matcher(name);
        if (matcher.find()) {
            String intStr = matcher.group(1);
            return name.replace(intStr, String.valueOf(Integer.parseInt(intStr) + 1));
        } else {
            return name+'1';
        }
    }
    

}
