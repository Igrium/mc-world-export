package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4dStack;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mixins.ModelPartAccessor;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.util.MathUtils;
import org.scaffoldeditor.worldexport.util.MeshUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class SinglePartModelAdapter<T extends LivingEntity> extends LivingModelAdapter<T, MultipartReplayModel> {
    private MinecraftClient client = MinecraftClient.getInstance();

    private SinglePartEntityModel<T> model;
    private MultipartReplayModel replayModel;

    protected Identifier texture;

    protected Map<ModelPart, ReplayModelPart> boneMapping = new HashMap<>();

    /**
     * Keep track of the previous frame's pose for quaternion compatibility.
     */
    protected Pose<ReplayModelPart> lastPose;

    @SuppressWarnings("unchecked")
    public SinglePartModelAdapter(T entity) {
        super(entity);

        LivingEntityRenderer<T, ?> renderer;
        try {
            renderer = (LivingEntityRenderer<T, ?>) client.getEntityRenderDispatcher().getRenderer(entity);
            model = (SinglePartEntityModel<T>) renderer.getModel();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Single part model adapters can only be used with entities that have LivingEntityRenderers with single part models!", e);
        }
        this.texture = renderer.getTexture(getEntity());
        this.replayModel = captureBaseModel(model);
    }

    @Override
    public MultipartReplayModel getModel() {
        return replayModel;
    }
    
    public SinglePartEntityModel<T> getEntityModel() {
        return model;
    }

    @Override
    public void animateModel(float limbAngle, float limbDistance, float tickDelta) {
        getEntityModel().animateModel(getEntity(), limbAngle, limbDistance, tickDelta);
        
    }

    @Override
    public void setAngles(float limbAngle, float limbDistance, float animationProgress, float headYaw,
            float headPitch) {
        getEntityModel().setAngles(getEntity(), limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        
    }

    @Override
    protected void updateValues(float handSwingProgress, boolean riding, boolean child) {
        getEntityModel().handSwingProgress = handSwingProgress;
        getEntityModel().riding = riding;
        getEntityModel().child = child;
        
    }

    @Override
    protected Pose<?> writePose(float tickDelta) {
        Pose<ReplayModelPart> pose = new Pose<>();
        forEachPart((name, part, transform) -> {
            ReplayModelPart bone = boneMapping.get(part);
            if (bone == null) {
                LogManager.getLogger().error("Model part '"+name+"' not found in bone mapping!");
                return;
            }

            Vector3d translation = transform.getTranslation(new Vector3d());
            Vector3d scale = transform.getScale(new Vector3d());

            Quaterniond rotation = transform.getUnnormalizedRotation(new Quaterniond());
            if (lastPose != null) {
                Transform lastBone = lastPose.bones.get(bone);
                if (lastBone != null) MathUtils.makeQuatsCompatible(rotation, lastBone.rotation, rotation);
            }

            pose.bones.put(bone, new Transform(translation, rotation, scale));

        });

        lastPose = pose;
        return pose;
    }

    /**
     * Capture the model in it's "bind pose".
     */
    protected MultipartReplayModel captureBaseModel(SinglePartEntityModel<T> entityModel) {
        MultipartReplayModel model = new MultipartReplayModel();

        // Reset pose
        animateModel(0, 0, 0);
        setAngles(0, 0, 0, 0, 0);

        ModelPart root = entityModel.getPart();
        model.bones.add(addPartRecursive(root, "root"));

        return model;
    }

    private void appendPartMesh(ReplayModelPart bone, ModelPart part) {
        bone.getMesh().setActiveMaterialGroupName(getTexName(this.getTexture()));
        part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
            if (!path.equals("")) return;
            MeshUtils.appendCuboid(cuboid, bone.getMesh(), MeshUtils.NEUTRAL_TRANSFORM);
        });
    }

    private ReplayModelPart addPartRecursive(ModelPart part, String name) {
        ReplayModelPart bone = new ReplayModelPart(name);
        appendPartMesh(bone, part);
        boneMapping.put(part, bone);

        ((ModelPartAccessor)(Object) part).getChildren().forEach((childName, child) -> {
            bone.children.add(addPartRecursive(child, childName));
        });

        return bone;
    }

    /**
     * Execute a function for every model part, including child parts.
     * 
     * @param consumer The function, consuming the part and the transformation of
     *                 said point, in relation to the model root.
     */
    protected void forEachPart(ModelPartConsumer consumer) {
        Matrix4dStack offset = new Matrix4dStack(10);
        ModelPart root = getEntityModel().getPart();
        forEachPartInternal("root", root, consumer, offset);
    }

    private void forEachPartInternal(String name, ModelPart part, ModelPartConsumer consumer, Matrix4dStack offset) {
        offset.pushMatrix();
        offset.rotate(Math.PI, 1, 0, 0);

        // Temporary fix until I can figure out why all the models are too low down.
        // TODO: proper fix for this
        if (!name.equals("root")) {
            offset.translate(0, -ReplayModels.BIPED_Y_OFFSET, 0);
        }

        offset.translate(part.pivotX / 16f, part.pivotY / 16f, part.pivotZ / 16f);

        if (part.yaw != 0)
            offset.rotateY(part.yaw);
        if (part.pitch != 0)
            offset.rotateX(part.pitch);
        if (part.roll != 0)
            offset.rotateZ(part.roll);

        consumer.accept(name, part, offset);
        ((ModelPartAccessor) (Object) part).getChildren().forEach((key, child) -> {
            forEachPartInternal(key, child, consumer, offset);
        });
        offset.popMatrix();
    }

    @Override
    Identifier getTexture() {
        return texture;
    }
    
}
