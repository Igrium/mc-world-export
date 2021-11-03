package org.scaffoldeditor.worldexport.export;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.render.model.BakedModel;

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

        /**
         * Create a model entry.
         * @param model The baked model to use.
         * @param faces A 6-element array dictating which faces are visible,
         * in the order NORTH, SOUTH, EAST, WEST, UP, DOWN
         */
        public ModelEntry(BakedModel model, boolean[] faces, boolean transparent) {
            this.model = model;
            this.faces = faces;
            this.transparent = transparent;
        }

        @Override
        public int hashCode() {
            return Objects.hash(model, Arrays.hashCode(faces));
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ModelEntry)) return false;
            ModelEntry other = (ModelEntry) obj;
            return (this.model.equals(other.model) 
                && (Arrays.hashCode(this.faces) == Arrays.hashCode(other.faces)));
        }
    }

    /**
     * The model entries in the cache and their IDs
     */
    public final Map<ModelEntry, String> models = new HashMap<>();

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
            models.put(entry, id);
        }
        return id;
    }

    public String getID(ModelEntry entry) {
        return getID(entry, null);
    }
}
