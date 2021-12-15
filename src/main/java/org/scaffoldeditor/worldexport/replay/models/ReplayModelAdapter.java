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

    public static final Map<Identifier, ReplayModelAdapterFactory<?>> REGISTRY = new HashMap<>();

    /**
     * Create a model adapter for a given entity.
     * @param entity The entity.
     * @return The generated model adapter.
     */
    @SuppressWarnings("unchecked")
    public static <E extends Entity> ReplayModelAdapter<E> getModelAdapter(E entity) {
        Identifier id = EntityType.getId(entity.getType());
        ReplayModelAdapterFactory<E> factory = (ReplayModelAdapterFactory<E>) REGISTRY.get(id);
        if (factory == null) {
            throw new IllegalStateException("No model adapter registered for ID "+id.toString());
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
