package org.scaffoldeditor.worldexport.vcap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;
import org.scaffoldeditor.worldexport.util.FloodFill;
import org.scaffoldeditor.worldexport.util.MeshComparator;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidBlockEntry;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;
import org.scaffoldeditor.worldexport.vcap.model.BlockModelProvider;
import org.scaffoldeditor.worldexport.vcap.model.MaterialProvider;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider.ModelInfo;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import de.javagl.obj.Obj;
import de.javagl.obj.ReadableObj;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.Registries;

/**
 * Contains various values passed around throughout the export process.
 */
public class ExportContext implements MaterialConsumer {

    /**
     * The models used in this vcap.
     */
    public final Map<String, ModelProvider> models = new HashMap<>();

    /**
     * A cache of model entries so they can be re-used.
     */
    private final Map<BlockModelEntry, String> modelCache = new HashMap<>();

    private static record FluidCacheEntry(ReadableObj obj, Fluid fluid) {};

    private final BiMap<FluidCacheEntry, String> fluidCache = HashBiMap.create();
    private final BiMap<FluidBlockEntry, String> newFluidCache = HashBiMap.create();

    /**
     * The materials used in this vcap.
     */
    public final Map<String, MaterialProvider> materials = new HashMap<>();

    /**
     * The textures to write to the vcap file.
     */
    public final Map<String, ReplayTexture> textures = new HashMap<>();

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
     * @return The name the model was given after name conflict resolution.
     */
    @Deprecated
    public synchronized String addExtraModel(String desiredName, Obj model) {
        String name = makeNameUnique(desiredName);
        ModelInfo info = new ModelInfo(model, 0, Collections.emptyMap());
        models.put(name, () -> info);
        return name;
    }

    /**
     * Add a model to the vcap file.
     * @param name The name to use.
     * @param model The model to add.
     * @return The name the model was given after name conflict resolution.
     */
    public synchronized String addModel(String name, ModelInfo model) {
        name = makeNameUnique(name);
        models.put(name, () -> model);
        return name;
    }

    /**
     * Add a block model to the vcap.
     * @param model Block model entry.
     * @return The name that was generated.
     */
    public synchronized String addBlock(BlockModelEntry model) {
        if (modelCache.containsKey(model)) return modelCache.get(model);
        String name = makeNameUnique(model.getID());
        models.put(name, new BlockModelProvider(model));
        modelCache.put(model, name);
        return name;
    }

    private MeshComparator comparator = new MeshComparator();

    /**
     * Add the mesh from a fluid domain to the vcap. Unlike simply adding the mesh
     * manually, this method also maintains a cache so meshes can be re-used when
     * appropriate.
     * 
     * @param fluid The fluid domain.
     * @return The name that was generated.
     */
    @Deprecated
    public synchronized String addFluid(FluidDomain fluid) {
        ModelInfo model = fluid.getModel();
        Optional<FluidCacheEntry> existing = fluidCache.keySet().stream().filter(entry -> {
            return entry.fluid().equals(fluid.getFluid())
            && comparator.meshEquals(entry.obj(), model.mesh(), .001f, 0);
        }).findAny();

        if (existing.isPresent()) {
            return fluidCache.get(existing.get());
        }

        String modelID = addModel("fluid.0", model); // Name conflict resolution will handle this.
        fluidCache.put(new FluidCacheEntry(model.mesh(), fluid.getFluid()), modelID);
        return modelID;
    }

    public synchronized String addFluid(FluidBlockEntry fluid) {
        var existing = newFluidCache.entrySet().stream().filter(entry -> {
            return entry.getKey().equals(fluid, comparator, .001f);
        }).findAny();

        if (existing.isPresent()) {
            return existing.get().getValue();
        }

        String modelID = addModel("fluid.0", fluid.getModel());
        newFluidCache.put(fluid, modelID);
        return modelID;
    }

    public synchronized String makeNameUnique(String name) {
        while (models.containsKey(name)) {
            name = iterateName(name);
        }
        return name;
    }

    /**
     * Get a mapping of model IDs and the blockstate they belong to. For use in
     * <code>meta.json</code>
     * 
     * @param map Map to add to.
     * @see ExportContext#getIDMapping()
     */
    public void getIDMapping(Map<String, String> map) {
        models.forEach((id, model) -> {
            Optional<BlockState> blockstate = model.getBlockstate();
            if (blockstate.isPresent()) {
                map.put(id, Registries.BLOCK.getId(blockstate.get().getBlock()).toString());
            }
        });
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

    @Override
    public void putMaterial(String name, Material mat) {
        materials.put(name, (textures) -> mat);
    }

    @Override
    public boolean hasMaterial(String name) {
        return materials.containsKey(name);
    }

    @Override
    public Material getMaterial(String name) {
        return materials.getOrDefault(name, (textures) -> null).writeMaterial((texName, tex) -> {});
    }

    @Override
    public void putTexture(String name, ReplayTexture texture) {
        textures.put(name, texture);
    }

    @Override
    public boolean hasTexture(String name) {
        return textures.containsKey(name);
    }

    @Override
    public ReplayTexture getTexture(String name) {
        return textures.get(name);
    }
    

}
