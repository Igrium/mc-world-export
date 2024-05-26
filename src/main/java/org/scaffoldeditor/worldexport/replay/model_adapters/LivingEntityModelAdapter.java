package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mixins.ModelPartAccessor;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.util.MathUtils;
import org.scaffoldeditor.worldexport.util.MeshUtils;
import org.scaffoldeditor.worldexport.util.ModelUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

/**
 * A living model adapter designed for entities with <code>EntityModel</code>s.
 */
public abstract class LivingEntityModelAdapter<T extends LivingEntity, M extends EntityModel<? super T>> extends LivingModelAdapter<T, MultipartReplayModel> {

    protected interface ModelPartConsumer {

        /**
         * Called for every model part.
         * 
         * @param name           The model part's name.
         * @param part           The model part.
         * @param transform      The part's transformation relative to the model root.
         * @param localTransform The part's transformation relative to its parent.
         */
        void accept(String name, ModelPart part, MatrixStack transform, Matrix4dc localTransform);
    }

    /**
     * Maps model parts to their corrisponding bones, allowing the pose generator to
     * reference the proper replay bones.
     */
    protected BiMap<ModelPart, ReplayModelPart> boneMapping = HashBiMap.create();

    protected final M model;
    protected MultipartReplayModel replayModel;


    /**
     * Keep track of the previous frame's pose for quaternion compatibility.
     */
    protected Pose<ReplayModelPart> lastPose;

    public LivingEntityModelAdapter(T entity) throws IllegalArgumentException {
        super(entity);

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            LivingEntityRenderer<? super T, ?> renderer = (LivingEntityRenderer<? super T, ?>) client.getEntityRenderDispatcher().getRenderer(entity);
            model = extractModel(renderer);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Supplied entity had an incorrect model.", e);
        }
        
    }

    protected abstract M extractModel(LivingEntityRenderer<? super T, ?> entityRenderer) throws ClassCastException;

    @Override
    public final MultipartReplayModel getModel() {
        if (replayModel == null) replayModel = captureBaseModel(model);
        return replayModel;
    }

    public final M getEntityModel() {
        return model;
    }

    public abstract Identifier getTexture();

    protected String getMaterialName() {
        return MaterialUtils.getTexName(getTexture());
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        createMaterial(getTexture(), file);
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
        if (replayModel == null)
            replayModel = captureBaseModel(model);

        Pose<ReplayModelPart> pose = new Pose<>();
        forEachPart((name, part, transform, localTransform) -> {
            ReplayModelPart bone = boneMapping.get(part);
            if (bone == null) return;

            Vector3d translation = localTransform.getTranslation(new Vector3d());
            Vector3d scale = localTransform.getScale(new Vector3d());

            Quaterniond rotation = localTransform.getUnnormalizedRotation(new Quaterniond());
            if (lastPose != null) {
                Transform lastBone = lastPose.bones.get(bone);
                if (lastBone != null)
                    MathUtils.makeQuatsCompatible(rotation, lastBone.rotation, .2, rotation);
            }

            // TODO: check if visibility is recursive
            pose.bones.put(bone, new Transform(translation, rotation, scale, part.visible));
        });

        lastPose = pose;
        return pose;
    }

    protected MultipartReplayModel captureBaseModel(M model) {
        MultipartReplayModel replayModel = new MultipartReplayModel();

        animateModel(0, 0, 0);
        setAngles(0, 0, 0, 0, 0);

        Map<ModelPart, String> partNames = new HashMap<>();
        extractPartNames(model, partNames);

        forRootParts((generatedName, part, transform, localTransform) -> {
            String name = partNames.getOrDefault(part, generatedName);

            ReplayModelPart bone = genPartRecursive(part, name);
            replayModel.bones.add(bone);
        });

        return replayModel;
    }

    private ReplayModelPart genPartRecursive(ModelPart part, String name) {
        ReplayModelPart bone = new ReplayModelPart(name);
        appendPartMesh(bone, part);
        boneMapping.put(part, bone);

        ((ModelPartAccessor) (Object) part).getChildren().forEach((childName, child) -> {
            bone.children.add(genPartRecursive(child, childName));
        });
        
        return bone;
    }

    private void appendPartMesh(ReplayModelPart bone, ModelPart part) {
        bone.getMesh().setActiveMaterialGroupName(getMaterialName());
        part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
            // We're gonna handle the children seperately.
            if (!path.equals("")) return;
            MeshUtils.appendCuboid(cuboid, bone.getMesh(), new Matrix4d());
        });
    }

     /**
     * <p>
     * Models (problematically) don't store the part names of top-level model
     * parts themselves in a universal formats. By default, a part's
     * <code>toString()</code> method is used. However, if you know what type of
     * model you have, it's often possible to manually extract parts and assign them
     * the proper names.
     * </p>
     * <p>
     * This method tries to cast the model to all known subclasses and
     * extracts said model part names.
     * </p>
     * 
     * @param model     The model to operate on.
     * @param partNames A map of ModelParts and their names. Extracted part names
     *                  should be added here.
     */
    protected abstract void extractPartNames(M model, Map<ModelPart, String> dest);
    
    /**
     * Execute a function for every model part recursively.
     * @param consumer Model part consumer.
     */
    protected void forEachPart(ModelPartConsumer consumer) {
        MatrixStack offset = new MatrixStack();
        for (var pair : getRootParts()) {
            forEachPartInternal(pair.getLeft(), pair.getRight(), consumer, offset, true);
        }
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
    }
 
    /**
     * Execute a function only for each root part.
     * @param consumer Model part consumer.
     */
    protected void forRootParts(ModelPartConsumer consumer) {
        final MatrixStack offset = new MatrixStack();
        for (var pair : getRootParts()) {
            offset.push();

            // For some dumb reason, animal models are built exactly this far into the
            // ground, and fixing it is hardcoded into LivingEntityRenderer. Seriously
            // Mojang, clean up your rendering code.
            offset.translate(0, 1.501, 0);
            offset.multiply(new Quaternionf().rotateX((float) Math.PI));
            ModelUtils.getPartTransform(pair.getRight(), offset);

            Matrix4d localTransform = new Matrix4d(offset.peek().getPositionMatrix());

            consumer.accept(pair.getLeft(), pair.getRight(), offset, localTransform);
            offset.pop();
        }
    }

    protected abstract Iterable<Pair<String, ModelPart>> getRootParts();

}
