package org.scaffoldeditor.worldexport.replay.models;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4dStack;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mixins.AnimalModelAccessor;
import org.scaffoldeditor.worldexport.mixins.ModelPartAccessor;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Bone;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.BoneTransform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.util.MeshUtils;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

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
    protected void updateValues(LivingModelValues values) {
        model.handSwingProgress = values.handSwingProgress;
        model.child = values.child;
        model.riding = values.riding;
    }

    @Override
    protected Pose writePose(T entity, float yaw, float tickDelta) {
        Pose pose = new Pose();

        forEachPart(model, (name, part, transform) -> {
            Bone bone = boneMapping.get(part);
            if (bone == null) {
                LogManager.getLogger("Replay Models").error("Model part not found in bone mapping!");
                return;
            }

            Quaterniond rotation = new Quaterniond();
            transform.getUnnormalizedRotation(rotation);

            Vector3d position = new Vector3d();
            transform.getTranslation(position);

            Vector3d scale = new Vector3d();
            transform.getScale(scale);

            rotation = bone.rot.difference(rotation, new Quaterniond());
            position.sub(bone.pos);
            
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
            bone.pos = translation;

            Quaterniond rotation = new Quaterniond();
            transform.getUnnormalizedRotation(rotation);
            bone.rot = rotation;

            replayModel.bones.add(bone);
            boneMapping.put(part, bone);

            part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
                if (!path.equals("")) return; // We only want to get the cuboids from this part.
                replayModel.mesh.setActiveGroupNames(Collections.singleton(name));
                MeshUtils.appendCuboid(cuboid, replayModel.mesh, transform);
            });
        });

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
         * @param transform The part's transformation relative to the model root (y offset included)
         */
        void accept(String name, ModelPart part, Matrix4dc transform);
    }

    private void forEachPart(String name, ModelPart part, ModelPartConsumer consumer, Matrix4dStack offset) {
        offset.pushMatrix();
        offset.rotate(Math.PI, 1, 0, 0);
        offset.translate(part.pivotX / 16f, part.pivotY / 16f, part.pivotZ / 16f);
        offset.translate(0, -yOffset, 0);

        if (part.yaw != 0)
            offset.rotateY(part.yaw);
        if (part.pitch != 0)
            offset.rotateX(part.pitch);
        if (part.roll != 0)
            offset.rotateZ(part.roll);

        consumer.accept(name, part, offset);
        ((ModelPartAccessor) (Object) part).getChildren().forEach((key, child) -> {
            forEachPart(key, child, consumer, offset);
        });
        offset.popMatrix();
    }

}