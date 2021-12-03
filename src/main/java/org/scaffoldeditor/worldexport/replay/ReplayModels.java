package org.scaffoldeditor.worldexport.replay;

import java.util.Map;

import org.scaffoldeditor.worldexport.replay.ReplayModel.BoneTransform;
import org.scaffoldeditor.worldexport.replay.ReplayModelManager.ReplayModelGenerator;

import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

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

        public LivingModelGenerator(M model) {
            this.model = model;
        }

        public M getModel() {
            return model;
        }

        @Override
        public Map<String, BoneTransform> getPose(T entity, float y, float tickDelta) {
            this.model.handSwingProgress = entity.getHandSwingProgress(tickDelta);
            this.model.riding = entity.hasVehicle();
            this.model.child = entity.isBaby();

            float yaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
            float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
            float headYawDiff = headYaw - yaw;

            if (entity.hasVehicle() && entity.getVehicle() instanceof LivingEntity) {
                LivingEntity parent = (LivingEntity) entity.getVehicle();
                yaw = MathHelper.lerpAngleDegrees(tickDelta, parent.prevBodyYaw, parent.bodyYaw);
                headYawDiff = headYaw - yaw;

                float wrapped = MathHelper.wrapDegrees(headYawDiff);
                if (wrapped < -85) wrapped = -85;
                if (wrapped >= 85) wrapped = 85;

                yaw = headYaw - wrapped;
                if (wrapped * wrapped > 2500) {
                    yaw += wrapped * .2;
                }

                headYawDiff = headYaw - yaw;
            }

            Vec3d basePositionRelative = new Vec3d(0d, 0d, 0d);
            if (entity.getPose() == EntityPose.SLEEPING) {
                Direction direction = entity.getSleepingDirection();
                if (direction != null) {
                    double height = entity.getEyeHeight(EntityPose.STANDING) - .1;
                    basePositionRelative = basePositionRelative.add(-direction.getOffsetX() * height, 0, -direction.getOffsetZ() * height);
                }
            }

            float animProgress = entity.age + tickDelta;
            return null;
        }

        protected boolean isShaking(T entity) {
            return entity.isFreezing();
        }

        protected void prepareTransforms(T entity, Matrix4f matrix, float animationProgress, float bodyYaw, float tickDelta) {
            if (isShaking(entity)) {
                bodyYaw += Math.cos(entity.age * 3.25d) * Math.PI * 0.4;
            }
            
            EntityPose pose = entity.getPose();
            if (pose != EntityPose.SLEEPING) {
                matrix.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(180 - bodyYaw));
            }

            if (entity.deathTime > 0) {
                
            }
        }
    }
}
