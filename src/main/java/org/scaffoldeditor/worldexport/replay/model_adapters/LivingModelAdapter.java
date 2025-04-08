package org.scaffoldeditor.worldexport.replay.model_adapters;

import javax.annotation.Nullable;

import com.replaymod.core.versions.MCVer;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mat.PromisedReplayTexture;
import org.scaffoldeditor.worldexport.mat.Material.BlendMode;
import org.scaffoldeditor.worldexport.replay.models.OverrideChannel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.OverrideChannel.OverrideChannelFrame;
import org.scaffoldeditor.worldexport.util.MathUtils;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.LoggerFactory;

/**
 * Base replay model generator for living entities (with living entity renderers).
 * 
 * @param <T> The type of entity this is an adapter for.
 * @param <M> The type of model that this adapter will use.
 */
public abstract class LivingModelAdapter<T extends LivingEntity, M extends ReplayModel<?>> implements ReplayModelAdapter<M> {

    public interface ModelPartConsumer {
        /**
         * Called for every model part.
         * @param name The model part's name.
         * @param part The model part.
         * @param transform The part's transformation relative to the model root (y offset included)
         */
        void accept(String name, ModelPart part, Matrix4dc transform);
    }

    private final T entity;
    
    protected float handSwingProgress = 0;
    protected boolean riding = false;
    protected boolean child = false;
    
    public abstract void animateModel(float limbAngle, float limbDistance, float tickDelta);
    public abstract void setAngles(float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch);

    protected final OverrideChannel tint = new OverrideChannel("tint", OverrideChannel.Mode.VECTOR);

    /**
     * For quaternion compatibility
     */
    private Quaterniondc prevRotation;

    public LivingModelAdapter(T entity) {
        this.entity = entity;
    }

    public T getEntity() {
        return entity;
    }

    /**
     * Update various values on the entity model.
     */
    protected abstract void updateValues(float handSwingProgress, boolean riding, boolean child);
    
    /**
     * Extract the pose from the underlying model. Note: Root transform data gets
     * applied in the calling function. The Pose's root transform should be in local
     * space.
     *
     * @param tickDelta Tick delta.
     * @return The generated pose.
     */
    protected abstract Pose<?> writePose(float tickDelta);

    /**
     * Get or create a material suitable for use with this model adapter.
     * @param texID Minecraft texture ID.
     * @param file Material consumer to use.
     * @return The material name.
     */
    protected String createMaterial(Identifier texID, MaterialConsumer file) {
        String texName = MaterialUtils.getTexName(texID);
        if (file.hasMaterial(texName)) return texName;

        Material mat = createMaterial(texName);

        file.addMaterial(texName, mat);
        file.addTexture(texName, new PromisedReplayTexture(texID));

        return texName;
    }

    /**
     * Create a material suitable for use with this model adapter.
     * @param texName Base texture name.
     * @return The material.
     */
    protected Material createMaterial(String texName) {
        Material mat = new Material();
        mat.setColor(texName);
        mat.setRoughness(1);
        mat.setTransparent(isTransparent(entity));
        mat.setColor2BlendMode(BlendMode.SOFT_LIGHT);
        mat.addOverride("color2", tint.getName());

        return mat;
    }

    protected boolean isTransparent(T entity) {
        return true;
    }

    protected float getAnimationProgress(float tickDelta) {
        return entity.age + tickDelta;
    }

    protected boolean waitForReady;

    /**
     * Some model adapters (the player) need a frame to process before they can begin export.
     * If this method returns false, don't attempt to get the pose yet.
     * @return If the model is ready.
     */
    protected boolean isReady() {
        return true;
    }

    private int unreadyTicks = 0;

    @Override
    public Pose<?> getPose(float tickDelta) {

        if (!isReady()) {
            if (waitForReady) {
                // This codebase is getting dumber and dumber
                while (!isReady()) {
                    ((MCVer.MinecraftMethodAccessor) MinecraftClient.getInstance()).replayModExecuteTaskQueue();
                }
            }
            else {
                unreadyTicks++;
                if (unreadyTicks % 20 == 0) {
                    LoggerFactory.getLogger(getClass()).warn("{} went {} ticks without being ready!",
                            entity.getName().getString(), unreadyTicks);
                }

                return new Pose<>();
            }
        }

        // Add override channel.
        boolean hasTint = false;
        for (OverrideChannel channel : getModel().getOverrideChannels()) {
            if (channel.equals(tint)) {
                hasTint = true;
                break;
            }
        }

        if (!hasTint) {
            getModel().addOverrideChannel(tint);
        }

        this.handSwingProgress = entity.getHandSwingProgress(tickDelta);
        this.riding = entity.hasVehicle();
        this.child = entity.isBaby();
        updateValues(handSwingProgress, riding, child);

        float animProgress = getAnimationProgress(tickDelta);

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
        float headYawFinal = headYaw - bodyYaw;

        if (entity.hasVehicle() && entity.getVehicle() instanceof LivingEntity parent) {
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

        // POSE
        float limbAngle = 0;
        float limbDistance = 0;
        float pitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        if (!entity.hasVehicle() && entity.isAlive()) {
            limbDistance = entity.limbAnimator.getSpeed(tickDelta);
            limbAngle = entity.limbAnimator.getPos(tickDelta);

            if (entity.isBaby()) {
                limbAngle *= 3f;
            }

            if (limbDistance > 1) {
                limbDistance = 1f;
            }
        }

        this.animateModel(limbAngle, limbDistance, tickDelta);
        this.setAngles(limbAngle, limbDistance, animProgress, headYawFinal, pitch);

        Pose<?> pose = writePose(tickDelta);

        // Root transform
        Vector3d pos = new Vector3d(pose.root.translation);
        Vec3d mcPos = entity.getPos();
        pos.add(mcPos.x, mcPos.y, mcPos.z);

        Quaterniond rotation = new Quaterniond(pose.root.rotation);

        Transform transform = prepareTransform(animProgress, bodyYaw, tickDelta, pos,
                rotation, new Vector3d(pose.root.scale));
        
        if (prevRotation != null) {
            MathUtils.makeQuatsCompatible(rotation, prevRotation, .2, rotation); // Transform still references rotation.
        }

        pose.root = transform;
        prevRotation = rotation;

        pose.overrideChannels.put(tint, new OverrideChannelFrame(getTint()));
        return pose;
    }

    protected boolean isShaking() {
        return entity.isFrozen();
    }

    protected float getLyingAngle() {
        return 90.0F;
    }

    /**
     * Get this entity's current tint color, applied with a "soft light" blending mode.
     * @return Tint color, represented as an RGB vector.
     */
    protected Vector3fc getTint() {
        boolean hurt = entity.hurtTime > 0 || entity.deathTime > 0;
        return hurt ? new Vector3f(2, 0, 0) : new Vector3f(.5f);
    }

    private static float getYaw(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
        };
    }

    /**
     * Prepare root transformations. Takes three optional vector (and quat) values.
     * If these are specified, the transforms are added on top of these values, and
     * the passed objects are used in the generated Transform. If not, zero-values
     * are created.
     * 
     * @param animationProgress Animation progress.
     * @param bodyYaw           World-space body yaw.
     * @param tickDelta         Time since the previous tick.
     * @param translation       Base translation.
     * @param rotation          Base rotation.
     * @param scale             Base scale.
     * @return Generated transform, optionally referencing the passed base objects.
     */
    protected Transform prepareTransform(float animationProgress, float bodyYaw, float tickDelta,
            @Nullable Vector3d translation, @Nullable Quaterniond rotation, @Nullable Vector3d scale) {
        if (translation == null) translation = new Vector3d();
        if (rotation == null) rotation = new Quaterniond();
        if (scale == null) scale = new Vector3d(1d);

        EntityPose pose = entity.getPose();

        if (pose == EntityPose.SLEEPING) {
            Direction direction = entity.getSleepingDirection();
            if (direction != null) {
                float height = entity.getEyeHeight(EntityPose.STANDING) - .1f;
                translation.add(-direction.getOffsetX() * height, 0, -direction.getOffsetZ() * height);
            }
        }

        if (isShaking()) {
            bodyYaw += (float) (Math.cos(entity.age * 3.25d) * Math.PI * 0.4);
        }

        if (pose != EntityPose.SLEEPING) {
            rotation.rotateY(Math.toRadians(0 - bodyYaw));
        }

        if (entity.deathTime > 0) {
            double angle = (entity.deathTime + tickDelta - 1) / 20d * 1.6d;
            angle = Math.sqrt(angle);
            if (angle > 1) {
                angle = 1;
            } 

            rotation.rotateZ(Math.toRadians(angle * getLyingAngle()));
        } else if (entity.isUsingRiptide()) {
            rotation.rotateX(Math.toRadians(-90 - entity.getPitch()));
            rotation.rotateY(Math.toRadians((entity.age + tickDelta) * -75));
        } else if (pose == EntityPose.SLEEPING) {
            Direction direction = entity.getSleepingDirection();
            float rot = direction != null ? getYaw(direction) : bodyYaw;
            rotation.rotateY(Math.toRadians(rot));
            rotation.rotateZ(Math.toRadians(getLyingAngle()));
            rotation.rotateY(Math.toRadians(270));
        } else if (entity.hasCustomName() || entity instanceof PlayerEntity) {
            String name = Formatting.strip(entity.getName().getString());
            if ((name.equals("Dinnerbone") || name.equals("Grumm")) && (!(entity instanceof PlayerEntity)
                    || ((PlayerEntity) entity).isPartVisible(PlayerModelPart.CAPE))) {
                translation.add(0, entity.getHeight() + .1f, 0);
                rotation.rotateZ(Math.toRadians(180));
            }
        }

        return new Transform(translation, rotation, scale);
    }
}
