package org.scaffoldeditor.worldexport.replay.models;

import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.scaffoldeditor.worldexport.mixins.AnimalModelAccessor;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.BoneTransform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.entity.LivingEntity;

/**
 * Contains replay models for vanilla Minecraft entities.
 */
public final class ReplayModels {
    private ReplayModels() {};

    /**
     * Wraps an {@link AnimalModel} in a replay model generator.
     */
    public static class AnimalModelWrapper<T extends LivingEntity> extends LivingModelGenerator<T> {
        public final AnimalModel<T> model;
        public AnimalModelWrapper(AnimalModel<T> model) {
            this.model = model;
        }
        @Override
        public ReplayModel generateModel(T entity, ReplayFile file) {
            // TODO Auto-generated method stub
            return null;
        }
        @Override
        public void animateModel(T entity, float limbAngle, float limbDistance, float tickDelta) {
            this.model.animateModel(entity, limbAngle, limbDistance, tickDelta); 
        }
        @Override
        public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
                float headPitch) {
            this.model.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            
        }
        @Override
        protected Pose writePose(T entity, float yaw, float tickDelta) {
            ((AnimalModelAccessor) model).retrieveHeadParts().forEach(part -> {
                Matrix4f transform = new Matrix4f();
            });
            return null;
        }

        private BoneTransform parseModelPart(ModelPart part) {
            return null;
        }

    }
}
