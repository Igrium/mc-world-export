package org.scaffoldeditor.worldexport.replay.models;


import java.util.HashMap;
import java.util.Map;

import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

/**
 * Renders an entity into an animated mesh. One exists per entity.
 */
public interface ReplayModelAdapter<T extends Entity> {

    public static interface ReplayModelAdapterFactory<T extends Entity> {
        public ReplayModelAdapter<T> create(T entity);
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

    public static final Map<Identifier, ReplayModelAdapterFactory<?>> REGISTRY = new HashMap<>();

    /**
     * Create a model adapter for a given entity.
     * @param entity The entity.
     * @return The generated model adapter.
     * @throws ModelNotFoundException If the entity type does not have a model adapter factory.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Entity> ReplayModelAdapter<E> getModelAdapter(E entity) throws ModelNotFoundException {
        Identifier id = EntityType.getId(entity.getType());
        ReplayModelAdapterFactory<E> factory = (ReplayModelAdapterFactory<E>) REGISTRY.get(id);
        if (factory == null) {
            throw new ModelNotFoundException(id);
        }

        return factory.create(entity);
    }
    
    /**
     * Generate the entity's replay model. 90% of the time, this returns the same
     * thing every time,
     * but some entities (like items) require different models on a per-entity
     * basis.
     * 
     * @param entity Entity to generate the model for.
     * @param file   Replay file to generate into.
     * @return Generated model.
     */
    public ReplayModel generateModel(T entity, ReplayFile file);

    /**
     * Get an entity's current pose (relative to the entity's root).
     * 
     * @param entity    The entity to query.
     * @param yaw       The entity's yaw.
     * @param tickDelta Time since the previous tick.
     * @return A mapping from the entity's bone names to their corrisponding
     *         transforms.
     */
    public Pose getPose(T entity, float yaw, float tickDelta);
}
