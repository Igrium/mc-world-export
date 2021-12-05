package org.scaffoldeditor.worldexport.replay.models;

import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelManager.ReplayModelGenerator;

import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;

public abstract class LivingModelGenerator<T extends LivingEntity> implements ReplayModelGenerator<T> {
    
    protected float handSwingProgress = 0;
    protected boolean riding = false;
    protected boolean child = false;
    
    public abstract void animateModel(T entity, float limbAngle, float limbDistance, float tickDelta);
    public abstract void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch);
    
    /**
     * Extract the pose from the underlying model.
     * @param entity Target entity.
     * @param yaw Entity's yaw.
     * @param tickDelta Tick delta.
     * @return The generated pose.
     */
    protected abstract Pose writePose(T entity, float yaw, float tickDelta);

    @Override
    public Pose getPose(T entity, float y, float tickDelta) {

        this.handSwingProgress = entity.getHandSwingProgress(tickDelta);
        this.riding = entity.hasVehicle();
        this.child = entity.isBaby();

        // TRANSFORMATION
        Matrix4f transform = Matrix4f.translate(0, 0, 0);

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
        float headYawDiff = headYaw - bodyYaw;

        if (entity.hasVehicle() && entity.getVehicle() instanceof LivingEntity) {
            LivingEntity parent = (LivingEntity) entity.getVehicle();
            bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, parent.prevBodyYaw, parent.bodyYaw);
            headYawDiff = headYaw - bodyYaw;

            float wrapped = MathHelper.wrapDegrees(headYawDiff);
            if (wrapped < -85) wrapped = -85;
            if (wrapped >= 85) wrapped = 85;

            bodyYaw = headYaw - wrapped;
            if (wrapped * wrapped > 2500) {
                bodyYaw += wrapped * .2;
            }

            headYawDiff = headYaw - bodyYaw;
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
            limbAngle = MathHelper.lerp(tickDelta, entity.lastLimbDistance, entity.limbDistance);
            limbDistance = entity.limbAngle - entity.limbDistance * (1 - tickDelta);

            if (entity.isBaby()) {
                limbDistance *= 3;
            }

            if (limbAngle > 1) {
                limbAngle = 1;
            }
        }

        this.animateModel(entity, limbAngle, limbDistance, tickDelta);
        this.setAngles(entity, limbAngle, limbDistance, animProgress, headYaw, pitch);

        return null;
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
