package org.scaffoldeditor.worldexport.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import de.javagl.obj.Obj;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

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
    public final Map<ModelEntry, String> models = new HashMap<>();

    public final List<Obj> fluidMeshes = new ArrayList<>();

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
            id = name+Arrays.toString(entry.faces);
            while (models.containsValue(id)) {
                id = iterateName(id);
            }
            models.put(entry, id);
        }
        return id;
    }

    /**
     * Get the ID of a fluid mesh.
     * @param index Index of the fluid mesh.
     * @return Fluid mesh ID.
     */
    public String getFluidID(int index) {
        return "fluid."+index;
    }

    public String getID(ModelEntry entry) {
        return getID(entry, null);
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
