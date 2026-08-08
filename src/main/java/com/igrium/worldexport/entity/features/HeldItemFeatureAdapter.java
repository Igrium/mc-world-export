package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.models.ItemModelAdapter;
import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayMtl;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import com.mojang.math.Axis;

import java.util.*;

public class HeldItemFeatureAdapter<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel<S>>
        extends FeatureAdapter<S, M> {

    public HeldItemFeatureAdapter(RenderLayer<S, M> renderer) {
        super(renderer);
    }

    private record HandedModel(HumanoidArm hand, List<BakedQuad> model) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    // M is only used internally, so as long as it's self-consistent, its value doesn't matter.
    public static <S extends ArmedEntityRenderState> FeatureAdapter<S, ?> create(RenderLayer<S, ?> renderer) {
        return new HeldItemFeatureAdapter<>((ItemInHandLayer) renderer);
        // If render properly casts to ArmorFeatureRenderer, then S must be in-bounds.
    }

    @Override
    public void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle,
                        float limbDistance, int tick) {
        captureArmWithItem(capture, materials, state, state.rightHandItemState, state.rightHandItemStack, HumanoidArm.RIGHT, tick);
        captureArmWithItem(capture, materials, state, state.leftHandItemState, state.leftHandItemStack, HumanoidArm.LEFT, tick);
    }

    private final Map<HandedModel, String> itemModelNames = new HashMap<>();

    /**
     * Capture one arm's held item, mirroring {@code ItemInHandLayer.submitArmWithItem}.
     * <p>
     * Unlike the vanilla renderer, the hand transform isn't baked into the mesh: the item's
     * quads are captured once in item-display space, and the per-tick hand transform (baby
     * offset, spear thrust, item-use animation) is written to the item bone's own curve,
     * parented to the arm bone so it keeps following it in Blender.
     */
    protected void captureArmWithItem(CapturedEntity capture, MaterialHolder materials, S state,
                                      ItemStackRenderState renderState, ItemStack itemStack, HumanoidArm arm, int tick) {
        if (renderState.isEmpty())
            return;

        if (captureSpecial(capture, materials, state, renderState, itemStack, arm, tick))
            return;

        PoseStack matrices = new PoseStack();
        matrices.mulPose(Axis.XP.rotationDegrees(-90.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F));

        boolean isLeftHand = arm == HumanoidArm.LEFT;
        boolean useBabyOffset = state.isBaby && state.entityType != EntityTypes.ARMOR_STAND;
        float offsetX = useBabyOffset ? 0.0F : 1.0F;
        float offsetY = useBabyOffset ? 1.0F : 2.0F;
        float offsetZ = useBabyOffset ? -4.5F : -10.0F;
        matrices.translate((isLeftHand ? -1 : 1) * offsetX / 16.0F, offsetY / 16.0F, offsetZ / 16.0F);

        if (state.attackTime > 0.0F && state.attackArm == arm && state.swingAnimationType == SwingAnimationType.STAB) {
            SpearAnimations.thirdPersonAttackItem(state, matrices);
        }

        float ticksUsingItem = state.ticksUsingItem(arm);
        if (ticksUsingItem != 0.0F) {
            HumanoidModel.ArmPose armPose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
            armPose.animateUseItem(state, matrices, ticksUsingItem, arm, itemStack);
        }

        String parentName = arm == HumanoidArm.LEFT ? "root/left_arm" : "root/right_arm";
        if (!capture.getCurves().containsKey(parentName)) {
            // Non-humanoid ArmedModel whose arm bone isn't named/nested the usual way:
            // parent to root and fold the model's own hand placement into the local transform.
            parentName = CapturedEntity.ROOT_NAME;
            getContextModel().translateToHand(state, arm, matrices);
        }

        for (String name : captureItemMesh(capture, materials, renderState, arm)) {
            capture.getParents().put(name, parentName);
            capture.addFrame(name, tick, AnimationCurve.CurveFormat.POS_ROT, matrices.last().pose());
        }
    }

    /**
     * Override to provide custom rendering for held items.
     *
     * @return <code>true</code> to suppress the default in-hand capture.
     */
    protected boolean captureSpecial(CapturedEntity capture, MaterialHolder materials, S state,
                                     ItemStackRenderState renderState, ItemStack itemStack, HumanoidArm arm, int tick) {
        return false;
    }

    /**
     * Bakes the item's quads (in item-display space) into cached meshes and returns their names.
     */
    private List<String> captureItemMesh(CapturedEntity capture, MaterialHolder materials, ItemStackRenderState renderState,
                                         HumanoidArm arm) {
        PoseStack matrices = new PoseStack();
        ItemQuadCollector collector = new ItemQuadCollector();
        renderState.submit(matrices, collector, 1, OverlayTexture.NO_OVERLAY, 0);

        List<String> names = new ArrayList<>();
        // TODO: does a submission represent an entire item or each item model part?
        for (var submission : collector.getSubmissions()) {
            String name = itemModelNames.computeIfAbsent(new HandedModel(arm, submission.quads()),
                    m -> "item." + itemModelNames.size());
            names.add(name);

            capture.getModelParts().computeIfAbsent(name, n -> {
                //noinspection resource
                Identifier atlas = submission.quads().isEmpty() ? null
                        : submission.quads().getFirst().materialInfo().sprite().atlasLocation();
                ReplayMtl mat = ItemModelAdapter.getOrCreateItemMaterial(materials, atlas);

                Obj obj = Objs.create();
                obj.setMtlFileNames(Set.of("entities.mtl"));
                obj.setActiveMaterialGroupName(mat.getName());

                ObjVertexConsumer consumer = new ObjVertexConsumer(obj);
                for (BakedQuad quad : submission.quads()) {
                    consumer.putBakedQuad(submission.pose(), quad, new QuadInstance());
                }
                consumer.pushFace();

                return obj;
            });
        }
        return names;
    }
}
