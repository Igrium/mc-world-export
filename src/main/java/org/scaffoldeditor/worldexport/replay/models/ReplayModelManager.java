package org.scaffoldeditor.worldexport.replay.models;


import java.util.HashMap;
import java.util.Map;

import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public final class ReplayModelManager {
    private ReplayModelManager() {};

    /**
     * Renders an entity into an animated mesh. Only one exists per entity.
     */
    public static interface ReplayModelGenerator<T extends Entity> {
        /**
         * Generate the entity's replay model. 90% of the time, this returns the same thing every time,
         * but some entities (like items) require different models on a per-entity basis.
         * @param entity Entity to generate the model for.
         * @param file Replay file to generate into.
         * @return Generated model.
         */
        public ReplayModel generateModel(T entity, ReplayFile file);
        /**
         * Get an entity's current pose (relative to the entity's root).
         * @param entity The entity to query.
         * @param yaw The entity's yaw.
         * @param tickDelta Time since the previous tick.
         * @return A mapping from the entity's bone names to their corrisponding transforms.
         */
        public Pose getPose(T entity, float yaw, float tickDelta);
    }
    
    public static final Map<Identifier, ReplayModelGenerator<?>> REGISTRY = new HashMap<>();
}
