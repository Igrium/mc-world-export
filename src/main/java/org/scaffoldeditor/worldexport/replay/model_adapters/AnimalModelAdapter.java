package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Matrix4d;
import org.joml.Matrix4dStack;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mixins.AnimalModelAccessor;
import org.scaffoldeditor.worldexport.mixins.ModelPartAccessor;
import org.scaffoldeditor.worldexport.mixins.QuadrupedModelAccessor;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.util.MathUtils;
import org.scaffoldeditor.worldexport.util.MeshUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Umbrella model adapter that works specifically with entities which use Animal Models.
 */
public class AnimalModelAdapter<T extends LivingEntity> extends LivingModelAdapter<T, MultipartReplayModel> {
    private MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Maps model parts to their corrisponding bones, allowing the pose generator to
     * reference the proper replay bones.
     */
    protected Map<ModelPart, ReplayModelPart> boneMapping = new HashMap<>();

    private AnimalModel<T> model;
    private MultipartReplayModel replayModel;

    protected float yOffset = 0;
    protected Identifier texture;

    final Matrix4d NEUTRAL_TRANSFORM = new Matrix4d();

    /**
     * Keep track of the previous frame's pose for quaternion compatibility.
     */
    protected Pose<ReplayModelPart> lastPose;

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
        this(entity, texture, ReplayModels.BIPED_Y_OFFSET);
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
    public MultipartReplayModel getModel() {
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
    protected Pose<ReplayModelPart> writePose(float tickDelta) {
        Pose<ReplayModelPart> pose = new Pose<>();
        forEachPart((name, part, transform) -> {
            ReplayModelPart bone = boneMapping.get(part);
            if (bone == null) {
                // LogManager.getLogger("Replay Models").error("Model part '"+name+"' not found in bone mapping!");
                return;
            }

            Vector3d translation = transform.getTranslation(new Vector3d());
            Vector3d scale = transform.getScale(new Vector3d());

            Quaterniond rotation = transform.getUnnormalizedRotation(new Quaterniond());
            if (lastPose != null) {
                Transform lastBone = lastPose.bones.get(bone);
                if (lastBone != null) MathUtils.makeQuatsCompatible(rotation, lastBone.rotation, .2, rotation);
            }

            pose.bones.put(bone, new Transform(translation, rotation, scale));

        });

        lastPose = pose;
        return pose;
    }

    /**
     * <p>
     * Animal models (problematically) don't store the part names of top-level model
     * parts themselves in a universal formats. By default, a part's
     * <code>toString()</code> method is used. However, if you know what type of
     * model you have, it's often possible to manually extract parts and assign them
     * the proper names.
     * </p>
     * <p>
     * This method tries to cast the animal model to all known subclasses and
     * extracts said model part names.
     * </p>
     * 
     * @param model     The model to operate on.
     * @param partNames A map of ModelParts and their names. Extracted part names
     *                  should be added here.
     */
    protected void extractPartNames(AnimalModel<T> model, Map<ModelPart, String> partNames) {
        if (model instanceof QuadrupedEntityModel) {
            QuadrupedModelAccessor accessor = (QuadrupedModelAccessor) model;
            partNames.put(accessor.getHead(), EntityModelPartNames.HEAD);
            partNames.put(accessor.getBody(), EntityModelPartNames.BODY);
            partNames.put(accessor.getRightHindLeg(), EntityModelPartNames.RIGHT_HIND_LEG);
            partNames.put(accessor.getLeftHindLeg(), EntityModelPartNames.LEFT_HIND_LEG);
            partNames.put(accessor.getRightFrontLeg(), EntityModelPartNames.RIGHT_FRONT_LEG);
            partNames.put(accessor.getLeftFrontLeg(), EntityModelPartNames.LEFT_FRONT_LEG);
        }

        if (model instanceof BipedEntityModel) {
            BipedEntityModel<T> biped = (BipedEntityModel<T>) model;
            partNames.put(biped.head, EntityModelPartNames.HEAD);
            partNames.put(biped.hat, EntityModelPartNames.HAT);
            partNames.put(biped.body, EntityModelPartNames.BODY);
            partNames.put(biped.rightArm, EntityModelPartNames.RIGHT_ARM);
            partNames.put(biped.leftArm, EntityModelPartNames.LEFT_ARM);
            partNames.put(biped.rightLeg, EntityModelPartNames.RIGHT_LEG);
            partNames.put(biped.leftLeg, EntityModelPartNames.LEFT_LEG);
        }
    }

    /**
     * Capture the model in it's "bind pose".
     */
    protected MultipartReplayModel captureBaseModel(AnimalModel<T> model) {
        // ArmatureReplayModel replayModel = new ArmatureReplayModel();
        MultipartReplayModel replayModel = new MultipartReplayModel();

        // Reset pose
        animateModel(0, 0, 0);
        setAngles(0, 0, 0, 0, 0);

        // Extracting known bones directly from the model allows us to use user-friendly names.
        Map<ModelPart, String> partNames = new HashMap<>();
        extractPartNames(model, partNames);

        forRootParts((generatedName, part, transform) -> {
            // Check if we need to override this name;
            String name = partNames.containsKey(part) ? partNames.get(part) : generatedName;

            ReplayModelPart bone = new ReplayModelPart(name);
            replayModel.bones.add(bone);
            boneMapping.put(part, bone);
            appendPartMesh(bone, part);

            Map<String, ModelPart> children = ((ModelPartAccessor)(Object) part).getChildren();
            for (String childName : children.keySet()) {
                ModelPart child = children.get(childName);

                ReplayModelPart childBone = new ReplayModelPart(childName);
                bone.children.add(childBone);
                boneMapping.put(child, childBone);
                appendPartMesh(childBone, child);
            }
        });

        // forEachPart((generatedName, part, transform) -> {
        //     // Check if we need to override this name.
        //     String name = partNames.containsKey(part) ? partNames.get(part) : generatedName;

        //     // Bone bone = new Bone(name);
        //     ReplayModelPart bone = new ReplayModelPart(name);

        //     // bone.pos = transform.getTranslation(new Vector3d());
        //     // bone.rot = transform.getUnnormalizedRotation(new Quaterniond());

        //     replayModel.bones.add(bone);
        //     boneMapping.put(part, bone);

        //     // replayModel.mesh.setActiveMaterialGroupName(getTexName(this.texture));
        //     bone.getMesh().setActiveMaterialGroupName(getTexName(this.texture));

        //     part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
        //         if (!path.equals("")) return; // We only want to get the cuboids from this part.
        //         MeshUtils.appendCuboid(cuboid, bone.getMesh(), NEUTRAL_TRANSFORM);
        //     });
        // });

        return replayModel;
    }

    private void appendPartMesh(ReplayModelPart bone, ModelPart part) {
        bone.getMesh().setActiveMaterialGroupName(getTexName(this.texture));
        part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
            if (!path.equals("")) return;
            MeshUtils.appendCuboid(cuboid, bone.getMesh(), NEUTRAL_TRANSFORM);
        });
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
    protected void forEachPart(org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter.ModelPartConsumer consumer) {
        Matrix4dStack offset = new Matrix4dStack(10);

        // If a bone is assigned to this part, and we're guessing part names anyway, use the bone's name.
        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(part -> {
            forEachPartInternal(boneMapping.containsKey(part) ? boneMapping.get(part).getName() : part.toString(), part, consumer, offset);
        });

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(part -> {
            forEachPartInternal(boneMapping.containsKey(part) ? boneMapping.get(part).getName() : part.toString(), part, consumer, offset);
        });
    }
    
    private void forEachPartInternal(String name, ModelPart part, org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter.ModelPartConsumer consumer, Matrix4dStack offset) {
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

    protected void forRootParts(org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter.ModelPartConsumer consumer) {

        Consumer<ModelPart> handlePart = (part) -> {
            Matrix4d offset = new Matrix4d();
            if (part.yaw != 0)
                offset.rotateY(part.yaw);
            if (part.pitch != 0)
                offset.rotateX(part.pitch);
            if (part.roll != 0)
                offset.rotateZ(part.roll);

            String name = boneMapping.containsKey(part) ? boneMapping.get(part).getName() : part.toString();
            consumer.accept(name, part, offset);
        };

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(handlePart);
        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(handlePart);
    }
}
