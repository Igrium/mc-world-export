package org.scaffoldeditor.worldexport.replay.model_adapters.specific;

import javax.annotation.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.replay.model_adapters.BipedModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

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
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class PlayerModelAdapter extends BipedModelAdapter<AbstractClientPlayerEntity> {

    protected PlayerModelAdapter(AbstractClientPlayerEntity player, CompletableFuture<SkinTextures> texture) {
        super(player, TextureManager.MISSING_IDENTIFIER);
        skinTextureFuture = texture;
        waitForReady = true;
    }
    
    public static PlayerModelAdapter newInstance(AbstractClientPlayerEntity player) {
        PlayerSkinProvider skinProvider = MinecraftClient.getInstance().getSkinProvider();
        CompletableFuture<SkinTextures> skin = skinProvider.fetchSkinTextures(player.getGameProfile())
                .exceptionally(e -> {
                    LoggerFactory.getLogger(PlayerModelAdapter.class).error("Error getting skin for {}", player.getName().getString(), e);
                    return DefaultSkinHelper.getSkinTextures(player.getGameProfile());
                });


        return new PlayerModelAdapter(player, skin);
    }

    private final CompletableFuture<SkinTextures> skinTextureFuture;

    public CompletableFuture<SkinTextures> getSkinTextureFuture() {
        return skinTextureFuture;
    }

    @Override
    protected boolean isReady() {
        return skinTextureFuture.isDone();
    }

    public SkinTextures getSkinTexture() {
        return skinTextureFuture.getNow(DefaultSkinHelper.getSkinTextures(getEntity().getGameProfile()));
    }

    @Override
    public Identifier getTexture() {
        return getSkinTexture().texture();
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

        Transform transform = super.prepareTransform(animationProgress, bodyYaw, tickDelta, translation, rotation, scale.mul(0.9375f));

        translation = new Vector3d(transform.translation);
        rotation = new Quaterniond(transform.rotation);

        float leaningPitch = getEntity().getLeaningPitch(tickDelta);
        float pitch = getEntity().getPitch(tickDelta);

        if (getEntity().isFallFlying()) {
            Matrix4d matrix = transform.toMatrix(new Matrix4d()); // Are we gonna have floating point issues turning this into a matrix?

            float roll = getEntity().getRoll() + tickDelta;
            float rollClamped = MathHelper.clamp(roll * roll / 100f, 0, 1);
            if (!getEntity().isUsingRiptide()) {
//                matrix.rotate(RotationAxis.POSITIVE_X.rotationDegrees(rollClamped * (-90 - pitch)));
                Quaternionf delta = RotationAxis.NEGATIVE_X.rotationDegrees(rollClamped * (-90 - pitch));
                rotation.mul(delta.x, delta.y, delta.z, delta.w);
            }
            Vec3d rotationVec = getEntity().getRotationVec(tickDelta);
            Vec3d velocityVec = getEntity().lerpVelocity(tickDelta);
            double rotationLengthSquared = rotationVec.horizontalLengthSquared();
            double velocityLengthSquared = velocityVec.horizontalLengthSquared();

            if (velocityLengthSquared > 0 && rotationLengthSquared > 0) {
                double dot = (velocityVec.x * rotationVec.x + velocityVec.z * rotationVec.z) / Math.sqrt(velocityLengthSquared * rotationLengthSquared);
                double cross = velocityVec.x * rotationVec.z - velocityVec.z * rotationVec.x;
                Quaternionf delta = RotationAxis.POSITIVE_Y.rotation((float)(Math.signum(cross) * Math.acos(dot)));
                rotation.mul(delta.x, delta.y, delta.z, delta.w);
            }
            transform = new Transform(translation, rotation, transform.scale, transform.visible);
        } else if (leaningPitch > 0) {
            Matrix4d matrix = transform.toMatrix(new Matrix4d());
            float swimAngle = getEntity().isTouchingWater() ? -90f - pitch : -90f;
            float lerpedSwimAngle = MathHelper.lerp(leaningPitch, 0, swimAngle);
            Quaternionf delta = RotationAxis.POSITIVE_X.rotationDegrees(lerpedSwimAngle);
            rotation.mul(delta.x, delta.y, delta.z, delta.w);
//            matrix.rotate(RotationAxis.POSITIVE_X.rotationDegrees(lerpedSwimAngle));
            if (getEntity().isInSwimmingPose()) {
//                matrix.translate(0, -1, 0.3);
                translation.add(0, -1, 0.3);
            }
            transform = new Transform(translation, rotation, transform.scale, transform.visible);
        }

        return transform;
    }

    @Override
    public boolean isSlim() {
        return getSkinTexture().model().equals(SkinTextures.Model.SLIM);
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
