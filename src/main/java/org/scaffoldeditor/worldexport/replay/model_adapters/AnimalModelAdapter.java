package org.scaffoldeditor.worldexport.replay.model_adapters;

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
import org.scaffoldeditor.worldexport.replay.models.ArmatureReplayModel;
import org.scaffoldeditor.worldexport.replay.models.Bone;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Transform;
import org.scaffoldeditor.worldexport.util.MeshUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Umbrella model adapter that works specifically with entities which use Animal Models.
 */
public class AnimalModelAdapter<T extends LivingEntity> extends LivingModelAdapter<T, ArmatureReplayModel> {
    private MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Maps model parts to their corrisponding bones, allowing the pose generator to
     * reference the proper replay bones.
     */
    protected Map<ModelPart, Bone> boneMapping = new HashMap<>();

    private AnimalModel<T> model;
    private ArmatureReplayModel replayModel;

    protected float yOffset = 0;
    protected Identifier texture;

    /**
     * Construct an animal model wrapper.
     * 
     * @param entity  The entity to use.
     * @param texture The Minecraft texture to use on this model.
     * @throws IllegalArgumentException If you try to create an AnimalModelAdapter
     *                                  for an entity that doesn't use an animal
     *                                  model.
     */
    public AnimalModelAdapter(T entity, Identifier texture) throws IllegalArgumentException {
        this(entity, texture, 0);
    }

    /**
     * Construct an animal model wrapper.
     * 
     * @param entity  The entity to use.
     * @param texture The Minecraft texture to use on this model.
     * @param yOffset Vertical root offset. See {@link #getYOffset()} for more info.
     * @throws IllegalArgumentException If you try to create an AnimalModelAdapter
     *                                  for an entity that doesn't use an animal
     *                                  model.
     */
    @SuppressWarnings("unchecked")
    public AnimalModelAdapter(T entity, Identifier texture, float yOffset) throws IllegalArgumentException {
        super(entity);

        try {
            LivingEntityRenderer<?, ?> renderer = (LivingEntityRenderer<?, ?>) client.getEntityRenderDispatcher().getRenderer(entity);
            model = (AnimalModel<T>) renderer.getModel();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Animal model adapters can only be used with entities that have LivingEntityRenderers with animal models!", e);
        }
        
        // replayModel = new ArmatureReplayModel();
        this.texture = texture;
        this.yOffset = yOffset;
        replayModel = captureBaseModel(model);

    }

    // @Override
    public ArmatureReplayModel getModel() {
        return replayModel;
    }

    public AnimalModel<T> getEntityModel() {
        return model;
    }

    @Override
    public void animateModel(float limbAngle, float limbDistance, float tickDelta) {
        this.model.animateModel(getEntity(), limbAngle, limbDistance, tickDelta);
    }

    @Override
    public void setAngles(float limbAngle, float limbDistance, float animationProgress, float headYaw,
            float headPitch) {
        this.model.setAngles(getEntity(), limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        
    }

    @Override
    protected void updateValues(float handSwingProgress, boolean riding, boolean child) {
        model.handSwingProgress = handSwingProgress;
        model.riding = riding;
        model.child = child;
        
    }

    @Override
    protected Pose<Bone> writePose(float tickDelta) {
        Pose<Bone> pose = new Pose<>();
        forEachPart((name, part, transform) -> {
            Bone bone = boneMapping.get(part);
            if (bone == null) {
                LogManager.getLogger("Replay Models").error("Model part not found in bone mapping!");
                return;
            }

            Quaterniond rotation = transform.getUnnormalizedRotation(new Quaterniond());
            Vector3d translation = transform.getTranslation(new Vector3d());
            Vector3d scale = transform.getScale(new Vector3d());

            pose.bones.put(bone, replayModel.processCoordinateSpace(bone, new Transform(translation, rotation, scale)));

        });

        return pose;
    }

    /**
     * Capture the model in it's "bind pose".
     */
    protected ArmatureReplayModel captureBaseModel(AnimalModel<T> model) {
        ArmatureReplayModel replayModel = new ArmatureReplayModel();

        forEachPart((name, part, transform) -> {
            Bone bone = new Bone(name);

            bone.pos = transform.getTranslation(new Vector3d());
            bone.rot = transform.getUnnormalizedRotation(new Quaterniond());

            replayModel.bones.add(bone);
            boneMapping.put(part, bone);

            replayModel.mesh.setActiveMaterialGroupName(getTexName(this.texture));
            part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
                if (!path.equals("")) return; // We only want to get the cuboids from this part.
                replayModel.mesh.setActiveGroupNames(Collections.singleton(name));
                MeshUtils.appendCuboid(cuboid, replayModel.mesh, transform);
            });
        });

        return replayModel;
    }

    @Override
    Identifier getTexture() {
        return texture;
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

    protected interface ModelPartConsumer {
        /**
         * Called for every model part.
         * @param name The model part's name.
         * @param part The model part.
         * @param transform The part's transformation relative to the model root (y offset included)
         */
        void accept(String name, ModelPart part, Matrix4dc transform);
    }

    /**
     * Execute a function for every model part, including child parts.
     * 
     * @param consumer The function, consuming the part and the transformation of
     *                 said point, in relation to the model root.
     */
    protected void forEachPart(ModelPartConsumer consumer) {
        Matrix4dStack offset = new Matrix4dStack(10);

        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(part -> {
            forEachPartInternal(part.toString(), part, consumer, offset);
        });

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(part -> {
            forEachPartInternal(part.toString(), part, consumer, offset);
        });
    }
    
    private void forEachPartInternal(String name, ModelPart part, ModelPartConsumer consumer, Matrix4dStack offset) {
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
            forEachPartInternal(key, child, consumer, offset);
        });
        offset.popMatrix();
    }
}
