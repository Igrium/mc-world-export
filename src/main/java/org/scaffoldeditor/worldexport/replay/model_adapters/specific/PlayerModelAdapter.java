package org.scaffoldeditor.worldexport.replay.model_adapters.specific;

import javax.annotation.Nullable;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.replay.model_adapters.BipedModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;

public class PlayerModelAdapter extends BipedModelAdapter<AbstractClientPlayerEntity> {

    static MinecraftClient client = MinecraftClient.getInstance();

    protected PlayerModelAdapter(AbstractClientPlayerEntity player, Identifier texture) {
        super(player, texture);
    }

    
    public static PlayerModelAdapter newInstance(AbstractClientPlayerEntity player) {
        return new PlayerModelAdapter(player, player.getSkinTexture());
    }

    @Override
    protected Pose<ReplayModelPart> writePose(float tickDelta) {
        setModelPose();
        return super.writePose(tickDelta);
    }

    @Override
    protected MultipartReplayModel captureBaseModel(AnimalModel<AbstractClientPlayerEntity> model) {
        PlayerEntityModel<AbstractClientPlayerEntity> pModel = (PlayerEntityModel<AbstractClientPlayerEntity>) model;
        pModel.rightArmPose = ArmPose.EMPTY;
        pModel.leftArmPose = ArmPose.EMPTY;
        pModel.sneaking = false;
        
        return super.captureBaseModel(model);
    }
    
    @Override
    protected Transform prepareTransform(float animationProgress, float bodyYaw, float tickDelta,
            @Nullable Vector3d translation, @Nullable Quaterniond rotation, @Nullable Vector3d scale) {
        if (scale == null) scale = new Vector3d(1);
        return super.prepareTransform(animationProgress, bodyYaw, tickDelta, translation, rotation, scale.mul(0.9375f));
    }

    @Override
    public boolean isSlim() {
        return getEntity().getModel().equals("slim");
    }

    private void setModelPose() {
        PlayerEntityModel<AbstractClientPlayerEntity> model = (PlayerEntityModel<AbstractClientPlayerEntity>) getEntityModel();
        AbstractClientPlayerEntity player = getEntity();
        model.sneaking = player.isInSneakingPose();
        if (player.isSpectator()) {
            model.setVisible(false);
            model.head.visible = true;
            model.hat.visible = true;
        } else {
            model.setVisible(true);
            ArmPose mainPose = getArmPose(player, Hand.MAIN_HAND);
            ArmPose offPose = getArmPose(player, Hand.OFF_HAND);

            if (mainPose.isTwoHanded()) {
                offPose = player.getOffHandStack().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
            }

            if (player.getMainArm() == Arm.RIGHT) {
                model.rightArmPose = mainPose;
                model.leftArmPose = offPose;
            } else {
                model.leftArmPose = mainPose;
                model.rightArmPose = offPose;
            }
        }
    }

    public static ArmPose getArmPose(AbstractClientPlayerEntity player, Hand hand) {
        ItemStack item = player.getStackInHand(hand);
        if (item.isEmpty()) {
            return ArmPose.EMPTY;
        } else {
            if (player.getActiveHand() == hand && player.getItemUseTimeLeft() > 0) {
                UseAction action = item.getUseAction();

                if (action == UseAction.BLOCK) {
                    return ArmPose.BLOCK;
                } else if (action == UseAction.BOW) {
                    return ArmPose.BOW_AND_ARROW;
                } else if (action == UseAction.CROSSBOW) {
                    return ArmPose.CROSSBOW_CHARGE;
                } else if (action == UseAction.SPEAR) {
                    return ArmPose.THROW_SPEAR;
                } else if (action == UseAction.SPYGLASS) {
                    return ArmPose.SPYGLASS;
                }

            } else if (!player.handSwinging && item.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(item)) {
                return ArmPose.CROSSBOW_HOLD;
            }
        }
        
        return ArmPose.ITEM;
    }
}
