package org.scaffoldeditor.worldexport.replay.model_adapters;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.TextureExtractor;
import org.scaffoldeditor.worldexport.mat.Material.Field;
import org.scaffoldeditor.worldexport.mat.ReplayTexture.NativeImageReplayTexture;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Transform;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/**
 * Base replay model generator for living entities (with living entity renderers).
 * 
 * @param <T> The type of entity this is an adapter for.
 * @param <M> The type of model that this adapter will use.
 */
public abstract class LivingModelAdapter<T extends LivingEntity, M extends ReplayModel<?>> implements ReplayModelAdapter<T, M> {

    private T entity;
    
    protected float handSwingProgress = 0;
    protected boolean riding = false;
    protected boolean child = false;
    
    public abstract void animateModel(float limbAngle, float limbDistance, float tickDelta);
    public abstract void setAngles(float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch);

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
     * @param model     The model to use to generate pose transforms.
     * @param tickDelta Tick delta.
     * @return The generated pose.
     */
    protected abstract Pose<?> writePose(float tickDelta);

    /**
     * Get the texture of this entity. Identical to {@link EntityRenderer#getTexture}
     * @return Texture identifier.
     */
    abstract Identifier getTexture();

    @Override
    public void generateMaterials(ReplayFile file) {
        Identifier texture = getTexture();
        String texName = getTexName(texture);
        if (file.materials.containsKey(texName)) return;
        Material mat = new Material();
        mat.color = new Field(texName);
        mat.transparent = isTransparent(entity);
        file.materials.put(texName, mat);

        RenderSystem.recordRenderCall(() -> {
            if (file.textures.containsKey(texName)) return;
            NativeImage tex = TextureExtractor.getTexture(texture);
            file.textures.put(texName, new NativeImageReplayTexture(tex));
        });
    }

    /**
     * Get the filename of a texture, excluding the extension.
     * @param texture Texture identifier.
     * @return Filename, without extension.
     */
    protected String getTexName(Identifier texture) {
        String name = texture.toString().replace(':', '/');
        int index = name.lastIndexOf('.');
        if (index > 0) {
            return name.substring(0, index);
        } else {
            return name;
        }
    }

    protected boolean isTransparent(T entity) {
        return true;
    }
    

    @Override
    public Pose<?> getPose(float tickDelta) {

        this.handSwingProgress = entity.getHandSwingProgress(tickDelta);
        this.riding = entity.hasVehicle();
        this.child = entity.isBaby();
        updateValues(handSwingProgress, riding, child);

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

        this.animateModel(limbAngle, limbDistance, tickDelta);
        this.setAngles(limbAngle, limbDistance, animProgress, headYawFinal, pitch);

        Pose<?> pose = writePose(tickDelta);
        // Root transform
        Vector3d pos = new Vector3d(pose.root.translation);
        Quaterniond rot = new Quaterniond();
        rot.rotateY(-Math.toRadians(entity.getYaw()));
        rot.transform(pos);

        Vec3d mcPos = entity.getPos();
        pos.add(mcPos.x, mcPos.y, mcPos.z);

        rot.mul(pose.root.rotation);

        pose.root = new Transform(pos, rot, pose.root.scale);
        return pose;
    }

    protected boolean isShaking() {
        return entity.isFreezing();
    }

    protected float getLyingAngle() {
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
        if (isShaking()) {
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

            matrix.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(angle * getLyingAngle()));
        } else if (entity.isUsingRiptide()) {
            matrix.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90 - entity.getPitch()));
            matrix.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((entity.age + tickDelta) * -75));
        } else if (pose == EntityPose.SLEEPING) {
            Direction direction = entity.getSleepingDirection();
            float rot = direction != null ? getYaw(direction) : bodyYaw;
            matrix.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(rot));
            matrix.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(getLyingAngle()));
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
