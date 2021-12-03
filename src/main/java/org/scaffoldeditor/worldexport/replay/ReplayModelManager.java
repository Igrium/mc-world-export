package org.scaffoldeditor.worldexport.replay;


import java.util.HashMap;
import java.util.Map;

import org.scaffoldeditor.worldexport.replay.ReplayModel.BoneTransform;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public final class ReplayModelManager {
    private ReplayModelManager() {};

    /**
     * Renders an entity into an animated mesh. Only one exists per entity.
     */
    public static interface ReplayModelGenerator<T extends Entity> {
        /**
         * Get the replay model for the entity. Represents all entities of this type.
         * @param file File to generate into.
         */
        public ReplayModel generateModel(ReplayFile file);
        /**
         * Get an entity's current pose (relative to the entity's root).
         * @param entity The entity to query.
         * @param yaw The entity's yaw.
         * @param tickDelta Time since the previous tick.
         * @return A mapping from the entity's bone names to their corrisponding transforms.
         */
        public Map<String, BoneTransform> getPose(T entity, float yaw, float tickDelta);
    }
    
    public static final Map<Identifier, ReplayModelGenerator<?>> REGISTRY = new HashMap<>();
}
