package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Math;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mixins.AnimalModelAccessor;
import org.scaffoldeditor.worldexport.mixins.ModelPartAccessor;
import org.scaffoldeditor.worldexport.mixins.QuadrupedModelAccessor;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.util.MathUtils;
import org.scaffoldeditor.worldexport.util.MeshUtils;
import org.scaffoldeditor.worldexport.util.ModelUtils;
import org.scaffoldeditor.worldexport.util.UtilFunctions;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

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

    protected interface ModelPartConsumer {
        /**
         * Called for every model part.
         * @param name The model part's name.
         * @param part The model part.
         * @param transform The part's transformation relative to the model root (y offset included)
         */
        void accept(String name, ModelPart part, MatrixStack transform, Matrix4dc localTransform);
    }

    /**
     * Maps model parts to their corrisponding bones, allowing the pose generator to
     * reference the proper replay bones.
     */
    protected BiMap<ModelPart, ReplayModelPart> boneMapping = HashBiMap.create();

    private AnimalModel<T> model;
    private MultipartReplayModel replayModel;

    protected Identifier texture;

    final Matrix4dc NEUTRAL_TRANSFORM = new Matrix4d();

    /**
     * Keep track of the previous frame's pose for quaternion compatibility.
     */
    protected Pose<ReplayModelPart> lastPose;

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
    public AnimalModelAdapter(T entity, Identifier texture) throws IllegalArgumentException {
        super(entity);

        try {
            LivingEntityRenderer<?, ?> renderer = (LivingEntityRenderer<?, ?>) client.getEntityRenderDispatcher().getRenderer(entity);
            model = (AnimalModel<T>) renderer.getModel();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Animal model adapters can only be used with entities that have LivingEntityRenderers with animal models!", e);
        }
        
        // replayModel = new ArmatureReplayModel();
        this.texture = texture;
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
        forEachPart((name, part, transform, localTransform) -> {
            ReplayModelPart bone = boneMapping.get(part);
            if (bone == null) {
                // LogManager.getLogger("Replay Models").error("Model part '"+name+"' not found in bone mapping!");
                return;
            }

            Vector3d translation = localTransform.getTranslation(new Vector3d());
            Vector3d scale = localTransform.getScale(new Vector3d());

            Quaterniond rotation = localTransform.getUnnormalizedRotation(new Quaterniond());
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

        forRootParts((generatedName, part, transform, localTransform) -> {
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

        return replayModel;
    }

    private void appendPartMesh(ReplayModelPart bone, ModelPart part) {
        bone.getMesh().setActiveMaterialGroupName(getMaterialName());
        part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
            if (!path.equals("")) return;
            MeshUtils.appendCuboid(cuboid, bone.getMesh(), NEUTRAL_TRANSFORM);
        });
    }

    protected Identifier getTexture() {
        return texture;
    }
    
    /**
     * Get the name of the material that will be used on this mesh.
     * @return Material name.
     */
    protected String getMaterialName() {
        return MaterialUtils.getTexName(texture);
    }

    /**
     * Create the material that will be used on this mesh.
     * @param materialName Material name to use.
     * @param file Material consumer to add to.
     */
    protected void writeMaterial(String materialName, MaterialConsumer file) {
        createMaterial(texture, file);
    }
    
    @Override
    public void generateMaterials(MaterialConsumer file) {
        writeMaterial(getMaterialName(), file);
    }

    /**
     * Execute a function for every model part, including child parts.
     * 
     * @param consumer The function, consuming the part and the transformation of
     *                 said point, in relation to the model root.
     */
    protected void forEachPart(org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter.ModelPartConsumer consumer) {
        MatrixStack offset = new MatrixStack();

        // If a bone is assigned to this part, and we're guessing part names anyway, use the bone's name.
        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(part -> {
            forEachPartInternal(boneMapping.containsKey(part) ? boneMapping.get(part).getName() : part.toString(), part, consumer, offset, true);
        });

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(part -> {
            forEachPartInternal(boneMapping.containsKey(part) ? boneMapping.get(part).getName() : part.toString(), part, consumer, offset, true);
        });
    }
    
    private void forEachPartInternal(String name, ModelPart part, ModelPartConsumer consumer, MatrixStack offset, boolean isRoot) {
        var localOffset = new Matrix4d();
        offset.push();

        // For some dumb reason, animal models are built exactly this far into the
        // ground, and fixing it is hardcoded into LivingEntityRenderer. Seriously
        // Mojang, clean up your rendering code.
        if (isRoot) {
            offset.translate(0, 1.501, 0);
            localOffset.translate(0, 1.501, 0);

            offset.multiply(new Quaternionf().rotateX((float) Math.PI));
            localOffset.rotateX(Math.PI);
        }

        ModelUtils.getPartTransform(part, localOffset);
        ModelUtils.getPartTransform(part, offset);

        consumer.accept(name, part, offset, localOffset);
        ((ModelPartAccessor) (Object) part).getChildren().forEach((key, child) -> {
            forEachPartInternal(key, child, consumer, offset, false);
        });
        offset.pop();
    }

    protected void forRootParts(ModelPartConsumer consumer) {
        final MatrixStack offset = new MatrixStack();
        Consumer<ModelPart> handlePart = (part) -> {
            Matrix4d localOffset = new Matrix4d();
            offset.push();

            ModelUtils.getPartTransform(part, localOffset);
            ModelUtils.getPartTransform(part, offset);


            ReplayModelPart modelPart = boneMapping.get(part);
            String name = UtilFunctions.validateName(modelPart != null ? modelPart.getName() : "unknown",
                    str -> boneMapping.values().stream().anyMatch(bone -> bone.getName().equals(str)));
            
            
            consumer.accept(name, part, offset, localOffset);

        };

        ((AnimalModelAccessor) model).retrieveHeadParts().forEach(handlePart);
        ((AnimalModelAccessor) model).retrieveBodyParts().forEach(handlePart);
    }
}
