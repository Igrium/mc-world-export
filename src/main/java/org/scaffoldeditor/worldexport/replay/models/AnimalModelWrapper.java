package org.scaffoldeditor.worldexport.replay.models;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4dStack;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.export.ObjVertexConsumer;
import org.scaffoldeditor.worldexport.mixins.AnimalModelAccessor;
import org.scaffoldeditor.worldexport.mixins.ModelPartAccessor;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Bone;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.BoneTransform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/**
 * Wraps an {@link AnimalModel} in a replay model generator.
 */
public class AnimalModelWrapper<T extends LivingEntity> extends LivingModelGenerator<T> {
    public final AnimalModel<T> model;
    protected ReplayModel replayModel;
    protected float yOffset;

    /**
     * Maps model parts to their corrisponding bones, allowing the pose generator to
     * reference the proper replay bones.
     */
    protected Map<ModelPart, Bone> boneMapping = new HashMap<>();
    
    /**
     * Construct an animal model wrapper.
     * @param model The base model.
     */
    public AnimalModelWrapper(AnimalModel<T> model) {
        this(model, 0);
    }

    /**
     * Construct an animal model wrapper.
     * @param model The base model.
     * @param yOffset Vertical root offset. See {@link #getYOffset()} for more info.
     */
    public AnimalModelWrapper(AnimalModel<T> model, float yOffset) {
        this.model = model;
        this.yOffset = yOffset;
        this.replayModel = captureBaseModel(model);
    }

    /**
     * Some animal models (biped) have their roots at the neck instead of the feet.
     * This obviously doesn't work when animating entities, so this value designates
     * a vertical offset to apply to all values in order to fix this.
     * 
     * @return The vertical root offset in meters.
     */
    public float getYOffset() {
        return yOffset;
    }

    @Override
    public ReplayModel generateModel(T entity, ReplayFile file) {
        return replayModel;
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
        Pose pose = new Pose();

        forEachPart(model, (name, part, transform) -> {
            Vector3d position = new Vector3d();
            transform.getTranslation(position);
            position = position.add(0, yOffset, 0);

            Quaterniond rotation = new Quaterniond();
            transform.getNormalizedRotation(rotation);

            Vector3d scale = new Vector3d();
            transform.getScale(scale);

            Bone bone = boneMapping.get(part);
            if (bone == null) {
                LogManager.getLogger("Replay Models").error("Model part not found in bone mapping!");
                return;
            }

            position.sub(bone.pos);
            bone.rot.difference(rotation, rotation);
            
            pose.bones.put(bone, new BoneTransform(position, rotation, scale));
        });
        return pose;
    }

    /**
     * Capture the model in it's "bind pose".
     */
    protected ReplayModel captureBaseModel(AnimalModel<T> model) {
        ReplayModel replayModel = new ReplayModel();

        forEachPart(model, (name, part, transform) -> {
            Bone bone = new Bone(name);
            Vector3d translation = new Vector3d();
            transform.getTranslation(translation);
            translation = translation.add(0, yOffset, 0);
            bone.pos = translation;

            Quaterniond rotation = new Quaterniond();
            transform.getNormalizedRotation(rotation);
            bone.rot = rotation;

            replayModel.bones.add(bone);
            boneMapping.put(part, bone);
        });

        ObjVertexConsumer consumer = new ObjVertexConsumer(replayModel.mesh, new Vec3d(0, 0, 0));
        
        MatrixStack renderStack = new MatrixStack();
        renderStack.multiply(new Quaternion(Vec3f.POSITIVE_X, 180, true));
        renderStack.translate(0, -yOffset, 0);
        model.render(renderStack, consumer, 255, 0, 255, 255, 255, 255);

        return replayModel;
    }

    /**
     * Execute a function for every model part, including child parts.
     * 
     * @param model    Subject model.
     * @param consumer The function, consuming the part and the transformation of
     *                 said point, in relation to the model root.
     */
    protected void forEachPart(AnimalModel<T> model, ModelPartConsumer consumer) {
        Matrix4dStack offset = new Matrix4dStack(10);

        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(part -> {
            forEachPart(part.toString(), part, consumer, offset);
        });

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(part -> {
            forEachPart(part.toString(), part, consumer, offset);
        });
    }

    protected interface ModelPartConsumer {
        /**
         * Called for every model part.
         * @param name The model part's name.
         * @param part The model part.
         * @param transform The part's transformation relative to the model root (y offset not included)
         */
        void accept(String name, ModelPart part, Matrix4dc transform);
    }

    private void forEachPart(String name, ModelPart part, ModelPartConsumer consumer, Matrix4dStack offset) {
        offset.pushMatrix();
        offset.rotate(Math.PI, 1, 0, 0);
        offset.translate(part.pivotX / 16f, part.pivotY / 16f, part.pivotZ / 16f);

        if (part.pitch != 0)
            offset.rotateX(part.pitch * MathHelper.RADIANS_PER_DEGREE);
        if (part.yaw != 0)
            offset.rotateY(part.yaw * MathHelper.RADIANS_PER_DEGREE);
        if (part.roll != 0)
            offset.rotateZ(part.roll * MathHelper.RADIANS_PER_DEGREE);

        consumer.accept(name, part, offset);
        ((ModelPartAccessor) (Object) part).getChildren().forEach((key, child) -> {
            forEachPart(key, child, consumer, offset);
        });
        offset.popMatrix();
    }

}