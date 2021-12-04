package org.scaffoldeditor.worldexport.replay.models;

import org.scaffoldeditor.worldexport.replay.models.ReplayModelManager.ReplayModelGenerator;

import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;

/**
 * Contains replay models for vanilla Minecraft entities.
 */
public final class ReplayModels {
    private ReplayModels() {};

    /**
     * Base class for model generators for living entities.
     */
    public static abstract class LivingModelGenerator<T extends LivingEntity, M extends EntityModel<T>> implements ReplayModelGenerator<T> {
        protected M model;

    }
}
