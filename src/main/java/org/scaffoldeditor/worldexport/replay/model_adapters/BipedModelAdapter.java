package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer.MaterialCache;
import org.scaffoldeditor.worldexport.replay.models.ReplayItemRenderer;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * An animal model adapter that can render items and armor
 */
public class BipedModelAdapter<T extends LivingEntity> extends AnimalModelAdapter<T> {

    private Matrix4dc leftItemOffset;
    private Matrix4dc rightItemOffset;

    public BipedModelAdapter(T entity, Identifier texture, float yOffset) throws IllegalArgumentException {
        super(entity, texture, yOffset);

        Matrix4d basis = new Matrix4d();
        basis.rotateX(Math.toRadians(-90d));
        basis.rotateY(Math.toRadians(180d));

        Matrix4d left = new Matrix4d(basis);
        Matrix4d right = new Matrix4d(basis);

        left.translate(-1d / 16d, .125d, -.625d);
        right.translate(1d / 16d, .125d, -.625d);

        leftItemOffset = left;
        rightItemOffset = right;
    }

    protected Map<Item, ReplayModelPart> leftHandModels = new HashMap<>();
    protected Map<Item, ReplayModelPart> rightHandModels = new HashMap<>();

    private Map<ReplayModelPart, Transform> prevTransforms = new HashMap<>();

    private ReplayModelPart lastLeftHand = null;
    private ReplayModelPart lastRightHand = null;

    private MaterialCache materialCache = new MaterialCache();
    
    @Override
    protected Pose<ReplayModelPart> writePose(float tickDelta) {
        Pose<ReplayModelPart> pose = super.writePose(tickDelta);
        T entity = getEntity();

        boolean invert = entity.getMainArm() == Arm.LEFT;
        ItemStack leftHand = invert ? entity.getMainHandStack() : entity.getOffHandStack();
        ItemStack rightHand = invert ? entity.getOffHandStack() : entity.getMainHandStack();

        // Clean up from last frame. This will be overridden if item didn't change.
        if (lastLeftHand != null) {
            pose.bones.put(lastLeftHand, getHidden(lastLeftHand));
        }

        if (lastRightHand != null) {
            pose.bones.put(lastRightHand, getHidden(lastRightHand));
        }

        // for (ReplayModelPart part : leftHandModels.values()) {
        //     if 
        //     pose.bones.put(part, new Transform(false));
        // }
        // for (ReplayModelPart part : rightHandModels.values()) {
        //     pose.bones.put(part, new Transform(false));
        // }
        // Stream.concat(leftHandModels.values().stream(), rightHandModels.values().stream()).forEach(part -> {
        //     pose.bones.put(part, getHidden(part));
        // });
        
        if (!leftHand.isEmpty()) {
            ReplayModelPart leftHandModel = leftHandModels.get(leftHand.getItem());
            if (leftHandModel == null) {
                leftHandModel = genItemModel(leftHand, Arm.LEFT);
            }
            Transform trans = new Transform(leftItemOffset, true);
            pose.bones.put(leftHandModel, trans);

            prevTransforms.put(leftHandModel, trans);
            lastLeftHand = leftHandModel;
        } else {
            lastLeftHand = null;
        }

        if (!rightHand.isEmpty()) {
            ReplayModelPart rightHandModel = rightHandModels.get(rightHand.getItem());
            if (rightHandModel == null) {
                rightHandModel = genItemModel(rightHand, Arm.RIGHT);
            }
            Transform trans = new Transform(rightItemOffset, true);
            pose.bones.put(rightHandModel, trans);

            prevTransforms.put(rightHandModel, trans);
            lastRightHand = rightHandModel;
        } else {
            lastLeftHand = null;
        }

        return pose;
    }

    private Transform getHidden(ReplayModelPart part) {
        Transform prev = prevTransforms.get(part);
        return prev != null ? new Transform(prev, false) : new Transform(false);
    }

    private ReplayModelPart genItemModel(ItemStack item, Arm arm) {
        if (getModel() == null) {
            throw new IllegalStateException("Base model must be captured before item model can be added.");
        }

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();

        ReplayModelPart part = new ReplayModelPart("item."+Registry.ITEM.getId(item.getItem())+"."+arm);

        Mode renderMode = arm == Arm.LEFT ? Mode.THIRD_PERSON_LEFT_HAND : Mode.THIRD_PERSON_RIGHT_HAND;
        BakedModel itemModel = itemRenderer.getModel(item, getEntity().getWorld(), getEntity(), 0);
        ReplayItemRenderer.renderItem(item, renderMode, arm == Arm.LEFT, new MatrixStack(), part.getMesh(), itemModel, materialCache);

        String parentName = arm == Arm.LEFT ? EntityModelPartNames.LEFT_ARM : EntityModelPartNames.RIGHT_ARM;
        ReplayModelPart parent = getModel().getBone(parentName);
        parent.children.add(part);

        if (arm == Arm.LEFT) {
            leftHandModels.put(item.getItem(), part);
        } else {
            rightHandModels.put(item.getItem(), part);
        }

        return part;
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        super.generateMaterials(file);
        materialCache.dump(file);
    }
}
