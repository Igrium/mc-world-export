package org.scaffoldeditor.worldexport.replay.models;

import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
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

public class PlayerModelAdapter extends AnimalModelWrapper<AbstractClientPlayerEntity> {

    static MinecraftClient client = MinecraftClient.getInstance();

    protected PlayerModelAdapter(AnimalModel<AbstractClientPlayerEntity> model, Identifier texture) {
        super(model, texture, ReplayModels.BIPED_Y_OFFSET);
    }

    public PlayerEntityModel<AbstractClientPlayerEntity> getModel() {
        return (PlayerEntityModel<AbstractClientPlayerEntity>) this.model;
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static PlayerModelAdapter newInstance(AbstractClientPlayerEntity player) {
        LivingEntityRenderer renderer = (LivingEntityRenderer) client.getEntityRenderDispatcher().getRenderer(player);
        return new PlayerModelAdapter((AnimalModel) renderer.getModel(), player.getSkinTexture());
    }

    @Override
    public Pose getPose(AbstractClientPlayerEntity entity, float yaw, float tickDelta) {
        setModelPose(entity);
        return super.getPose(entity, yaw, tickDelta);
    }

    private void setModelPose(AbstractClientPlayerEntity player) {
        PlayerEntityModel<AbstractClientPlayerEntity> model = getModel();
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
