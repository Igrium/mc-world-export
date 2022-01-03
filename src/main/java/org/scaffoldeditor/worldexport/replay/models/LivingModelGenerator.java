package org.scaffoldeditor.worldexport.replay.models;

import org.joml.Quaterniond;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;

/**
 * Base replay model generator for living entities (with living entity renderers).
 */
public abstract class LivingModelGenerator<T extends LivingEntity> implements ReplayModelAdapter<T> {
    
    /**
     * Wrapper class around various <code>LivingEntityRenderer</code> values for use in function calls.
     */
    public static class LivingModelValues {
        public final float handSwingProgress;
        public final boolean riding;
        public final boolean child;

        public LivingModelValues(float handSwingProgress, boolean riding, boolean child) {
            this.handSwingProgress = handSwingProgress;
            this.riding = riding;
            this.child = child;
        }
    }
    
    protected float handSwingProgress = 0;
    protected boolean riding = false;
    protected boolean child = false;
    
    public abstract void animateModel(T entity, float limbAngle, float limbDistance, float tickDelta);
    public abstract void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch);

    /**
     * Update various values on the entity model.
     * @param values An object wrapper around the values.
     */
    protected abstract void updateValues(LivingModelValues values);
    
    /**
     * Extract the pose from the underlying model.
     * 
     * @param entity    Target entity.
     * @param yaw       Entity's yaw. Note: yaw is applied in the parent class.
     *                  Don't rotate the whole model here.
     * @param tickDelta Tick delta.
     * @return The generated pose.
     */
    protected abstract Pose writePose(T entity, float yaw, float tickDelta);

    @Override
    public Pose getPose(T entity, float yaw, float tickDelta) {

        this.handSwingProgress = entity.getHandSwingProgress(tickDelta);
        this.riding = entity.hasVehicle();
        this.child = entity.isBaby();
        updateValues(new LivingModelValues(handSwingProgress, riding, child));

        // TRANSFORMATION
        Matrix4f transform = Matrix4f.translate(0, 0, 0);

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
        float headYawFinal = headYaw - bodyYaw;

        if (entity.hasVehicle() && entity.getVehicle() instanceof LivingEntity) {
            LivingEntity parent = (LivingEntity) entity.getVehicle();
            bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, parent.prevBodyYaw, parent.bodyYaw);
            headYawFinal = headYaw - bodyYaw;

            float wrapped = MathHelper.wrapDegrees(headYawFinal);
            if (wrapped < -85) wrapped = -85;
            if (wrapped >= 85) wrapped = 85;

            bodyYaw = headYaw - wrapped;
            if (wrapped * wrapped > 2500) {
                bodyYaw += wrapped * .2f;
            }

            headYawFinal = headYaw - bodyYaw;
        }

        if (entity.getPose() == EntityPose.SLEEPING) {
            Direction direction = entity.getSleepingDirection();
            if (direction != null) {
                float height = entity.getEyeHeight(EntityPose.STANDING) - .1f;
                transform.multiplyByTranslation(-direction.getOffsetX() * height, 0, -direction.getOffsetZ() * height);
            }
        }

        float animProgress = entity.age + tickDelta;
        prepareTransforms(entity, transform, animProgress, bodyYaw, tickDelta);
        transform.multiply(Matrix4f.scale(-1, -1, 1));
        scale(entity, transform, tickDelta);
        transform.multiplyByTranslation(0, -1.5010000467300415f, 0);

        // POSE
        float limbAngle = 0;
        float limbDistance = 0;
        float pitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        if (!entity.hasVehicle() && entity.isAlive()) {
            limbDistance = MathHelper.lerp(tickDelta, entity.lastLimbDistance, entity.limbDistance);
            limbAngle = entity.limbAngle - entity.limbDistance * (1 - tickDelta);

            if (entity.isBaby()) {
                limbAngle *= 3f;
            }

            if (limbDistance > 1) {
                limbDistance = 1f;
            }
        }

        this.animateModel(entity, limbAngle, limbDistance, tickDelta);
        this.setAngles(entity, limbAngle, limbDistance, animProgress, headYawFinal, pitch);

        Pose pose = writePose(entity, yaw, tickDelta);
        pose.rot = pose.rot.rotateY(-Math.toRadians(yaw), new Quaterniond());
        return pose;
    }

    protected boolean isShaking(T entity) {
        return entity.isFreezing();
    }

    protected float getLyingAngle(T entity) {
        return 90.0F;
    }

    private static float getYaw(Direction direction) {
        switch(direction) {
        case SOUTH:
            return 90.0F;
        case WEST:
            return 0.0F;
        case NORTH:
            return 270.0F;
        case EAST:
            return 180.0F;
        default:
            return 0.0F;
        }
    }

    protected void scale(T entity, Matrix4f matrix, float amount) {
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
            float angle = (entity.deathTime + tickDelta - 1) / 20f * 1.6f;
            angle = MathHelper.sqrt(angle);
            if (angle > 1) {
                angle = 1;
            }

            matrix.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(angle * getLyingAngle(entity)));
        } else if (entity.isUsingRiptide()) {
            matrix.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90 - entity.getPitch()));
            matrix.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((entity.age + tickDelta) * -75));
        } else if (pose == EntityPose.SLEEPING) {
            Direction direction = entity.getSleepingDirection();
            float rot = direction != null ? getYaw(direction) : bodyYaw;
            matrix.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(rot));
            matrix.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(getLyingAngle(entity)));
            matrix.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(270));
        } else if (entity.hasCustomName() || entity instanceof PlayerEntity) {
            String name = Formatting.strip(entity.getName().getString());
            if ((name.equals("Dinnerbone") || name.equals("Grumm")) && (!(entity instanceof PlayerEntity)
                    || ((PlayerEntity) entity).isPartVisible(PlayerModelPart.CAPE))) {
                matrix.multiplyByTranslation(0, entity.getHeight() + .1f, 0);
                matrix.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(180));
            }
        }
    }
}
