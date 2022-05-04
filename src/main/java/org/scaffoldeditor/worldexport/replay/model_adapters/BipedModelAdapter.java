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

    private ItemStack lastLeftHand = ItemStack.EMPTY;
    private ItemStack lastRightHand = ItemStack.EMPTY;

    private MaterialCache materialCache = new MaterialCache();
    
    @Override
    protected Pose<ReplayModelPart> writePose(float tickDelta) {
        Pose<ReplayModelPart> pose = super.writePose(tickDelta);
        T entity = getEntity();

        boolean invert = entity.getMainArm() == Arm.LEFT;
        ItemStack leftHand = invert ? entity.getMainHandStack() : entity.getOffHandStack();
        ItemStack rightHand = invert ? entity.getOffHandStack() : entity.getMainHandStack();

        // Clean up from last frame
        if (!leftHand.isItemEqual(lastLeftHand)) {
            if (!lastLeftHand.isEmpty()) {
                pose.bones.put(leftHandModels.get(lastLeftHand.getItem()), new Transform(false));
            }
            lastLeftHand = ItemStack.EMPTY;
        }

        if (!rightHand.isItemEqual(lastRightHand)) {
            if (!lastRightHand.isEmpty()) {
                pose.bones.put(leftHandModels.get(lastRightHand.getItem()), new Transform(false));
            }
            lastRightHand = ItemStack.EMPTY;
        }
        
        if (!leftHand.isEmpty()) {
            ReplayModelPart leftHandModel = leftHandModels.get(leftHand.getItem());
            if (leftHandModel == null) {
                leftHandModel = genItemModel(leftHand, Arm.LEFT);
            }
            pose.bones.put(leftHandModel, new Transform(leftItemOffset, true));
        }

        if (!rightHand.isEmpty()) {
            ReplayModelPart rightHandModel = rightHandModels.get(rightHand.getItem());
            if (rightHandModel == null) {
                rightHandModel = genItemModel(rightHand, Arm.RIGHT);
            }
            pose.bones.put(rightHandModel, new Transform(rightItemOffset, true));
        }
        
        lastLeftHand = leftHand;
        lastRightHand = rightHand;

        return pose;
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
