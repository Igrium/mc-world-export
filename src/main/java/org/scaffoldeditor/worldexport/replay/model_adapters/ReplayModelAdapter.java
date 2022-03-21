package org.scaffoldeditor.worldexport.replay.model_adapters;


import java.util.HashMap;
import java.util.Map;

import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

/**
 * Renders an entity into an animated mesh. One exists per entity.
 * @param <T> The type of entity this is an adapter for.
 * @param <B> The type of bones that this adapter's model will use.
 * @param <M> The type of model that this adapter will use.
 */
public interface ReplayModelAdapter<T extends Entity, B, M extends ReplayModel<B>> {

    public static interface ReplayModelAdapterFactory<T extends Entity, B, M extends ReplayModel<?>> {
        public ReplayModelAdapter<T, B, ? extends ReplayModel<B>> create(T entity);
    }

    /**
     * Thrown if a model adapter is attempted to be generated for an entity type with no factory registered.
     */
    public static class ModelNotFoundException extends Exception {
        
        /**
         * The ID of the entity that caused the exception. May be <code>null</code>.
         */
        public final Identifier id;

        public ModelNotFoundException(String message) {
            super(message);
            id = null;
        }

        public ModelNotFoundException(Identifier id) {
            super("No model adapter registered for "+id.toString());
            this.id = id;
        }

        public ModelNotFoundException(String message, Identifier id) {
            super(message);
            this.id = id;
        }
    }

    public static final Map<Identifier, ReplayModelAdapterFactory<?, ?, ?>> REGISTRY = new HashMap<>();

    /**
     * Create a model adapter for a given entity.
     * @param entity The entity.
     * @return The generated model adapter.
     * @throws ModelNotFoundException If the entity type does not have a model adapter factory.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Entity> ReplayModelAdapter<E, ?, ?> getModelAdapter(E entity) throws ModelNotFoundException {
        Identifier id = EntityType.getId(entity.getType());
        ReplayModelAdapterFactory<E, ?, ?> factory = (ReplayModelAdapterFactory<E, ?, ?>) REGISTRY.get(id);
        if (factory == null) {
            throw new ModelNotFoundException(id);
        }

        return factory.create(entity);
    }


    /**
     * Get the replay model that was generated when this adapter was created.
     * @return This adapter's model. May still be written to by this adapter.
     */
    public M getModel();
    
    /**
     * Generate the materials for this model. Doesn't do anything if the materials
     * already exist.
     * 
     * @param file Replay file to put materials/textures into.
     */
    public void generateMaterials(ReplayFile file);

    /**
     * Get an entity's current pose .
     * 
     * @param tickDelta Time since the previous tick.
     * @return The current pose.
     */
    public Pose<B> getPose(float tickDelta);
}
