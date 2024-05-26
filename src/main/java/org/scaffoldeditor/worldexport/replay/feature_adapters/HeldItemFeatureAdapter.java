package org.scaffoldeditor.worldexport.replay.feature_adapters;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.render.model.json.ModelTransformationMode;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayItemRenderer;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;

public class HeldItemFeatureAdapter implements ReplayFeatureAdapter<ReplayModelPart> {

    LivingEntity entity;

    private final Matrix4dc leftItemOffset;
    private final Matrix4dc rightItemOffset;

    protected Map<Item, ReplayModelPart> leftHandModels = new HashMap<>();
    protected Map<Item, ReplayModelPart> rightHandModels = new HashMap<>();

    private final Map<ReplayModelPart, Transform> prevTransforms = new HashMap<>();

    private ReplayModelPart lastLeftHand = null;
    private ReplayModelPart lastRightHand = null;

    String leftParent = EntityModelPartNames.LEFT_ARM;
    String rightParent = EntityModelPartNames.RIGHT_ARM;

    MultipartReplayModel model;

    public HeldItemFeatureAdapter(LivingEntity entity, MultipartReplayModel model) {
        this.entity = entity;
        this.model = model;

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

    public void writePose(Pose<ReplayModelPart> pose, float tickDelta) {
        boolean invert = entity.getMainArm() == Arm.LEFT;
        ItemStack leftHand = invert ? entity.getMainHandStack() : entity.getOffHandStack();
        ItemStack rightHand = invert ? entity.getOffHandStack() : entity.getMainHandStack();

        if (lastLeftHand != null) {
            pose.bones.put(lastLeftHand, getHidden(lastLeftHand));
        }

        if (lastRightHand != null) {
            pose.bones.put(lastRightHand, getHidden(lastRightHand));
        }

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

    }

    public void generateMaterials(MaterialConsumer consumer) {
        if (!rightHandModels.isEmpty() || !leftHandModels.isEmpty()) {
            ReplayItemRenderer.addMaterials(consumer);
        }
    }

    private Transform getHidden(ReplayModelPart part) {
        Transform prev = prevTransforms.get(part);
        return prev != null ? new Transform(prev, false) : new Transform(false);
    }

    private ReplayModelPart genItemModel(ItemStack item, Arm arm) {

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();

        ReplayModelPart part = new ReplayModelPart("item."+Registries.ITEM.getId(item.getItem())+"."+arm);

        ModelTransformationMode renderMode = arm == Arm.LEFT ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        BakedModel itemModel = itemRenderer.getModel(item, entity.getWorld(), entity, 0);
        ReplayItemRenderer.renderItem(item, renderMode, arm == Arm.LEFT, new MatrixStack(), part.getMesh(), itemModel);

        String parentName = arm == Arm.LEFT ? leftParent : rightParent;
        ReplayModelPart parent = model.getBone(parentName);
        if (parent == null) {
            throw new NullPointerException("No model part of name '"+parentName+"' found within the model!");
        }

        parent.children.add(part);

        if (arm == Arm.LEFT) {
            leftHandModels.put(item.getItem(), part);
        } else {
            rightHandModels.put(item.getItem(), part);
        }

        return part;
    }
}
