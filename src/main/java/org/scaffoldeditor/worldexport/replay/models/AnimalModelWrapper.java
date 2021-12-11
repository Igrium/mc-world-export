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
import net.minecraft.util.math.Vec3d;

/**
 * Wraps an {@link AnimalModel} in a replay model generator.
 */
public class AnimalModelWrapper<T extends LivingEntity> extends LivingModelGenerator<T> {
    public final AnimalModel<T> model;
    protected ReplayModel replayModel;

    /**
     * Maps model parts to their corrisponding bones, allowing the pose generator to
     * reference the proper replay bones.
     */
    protected Map<ModelPart, Bone> boneMapping = new HashMap<>();

    public AnimalModelWrapper(AnimalModel<T> model) {
        this.model = model;
        this.replayModel = captureBaseModel(model);
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
            
            pose.bones.put(name, new BoneTransform(position, rotation, scale));
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
            bone.pos = translation;

            Quaterniond rotation = new Quaterniond();
            transform.getNormalizedRotation(rotation);
            bone.rot = rotation;

            replayModel.bones.add(bone);
            boneMapping.put(part, bone);
        });

        ObjVertexConsumer consumer = new ObjVertexConsumer(replayModel.mesh, new Vec3d(0, 0, 0));

        model.render(new MatrixStack(), consumer, 255, 0, 255, 255, 255, 255);

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
        Matrix4dStack offset = new Matrix4dStack(1);

        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(part -> {
            forEachPart(part.toString(), part, consumer, offset);
        });

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(part -> {
            forEachPart(part.toString(), part, consumer, offset);
        });
    }

    protected interface ModelPartConsumer {
        void accept(String name, ModelPart part, Matrix4dc transform);
    }

    private void forEachPart(String name, ModelPart part, ModelPartConsumer consumer, Matrix4dStack offset) {
        offset.pushMatrix();

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