package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.models.ItemModelAdapter;
import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import com.igrium.worldexport.mesh.VertexConsumers.WrappedVertexConsumerProvider;
import com.igrium.worldexport.mixin.AccessorItemStackRenderState;
import com.igrium.worldexport.mixin.AccessorLayerRenderState;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayMtl;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.math.Axis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HeldItemFeatureAdapter<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel>
        extends FeatureAdapter<S, M> {

    private record HandedModel(HumanoidArm hand, BakedModel model) {};

    public static final String ITEM_MAT = "items";

    @SuppressWarnings({"unchecked", "rawtypes"}) // M is only used internally, so as long as it's self-consistent, its value doesn't matter.
    public static <S extends ArmedEntityRenderState> FeatureAdapter<S, ?> create(RenderLayer<S, ?> renderer) {
        return new HeldItemFeatureAdapter<>((ItemInHandLayer) renderer); // If render properly casts to ArmorFeatureRenderer, then S must be in-bounds.
    }

    public HeldItemFeatureAdapter(ItemInHandLayer<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle, float limbDistance, int tick) {
        captureItem(capture, materials, state, state.rightHandItem, HumanoidArm.RIGHT, tick);
        captureItem(capture, materials, state, state.leftHandItem, HumanoidArm.LEFT, tick);
    }

    private final Map<HandedModel, String> itemModelNames = new HashMap<>();

    protected void captureItem(CapturedEntity capture, MaterialHolder materials, S state, ItemStackRenderState itemState, HumanoidArm arm, int tick) {
        if (itemState.isEmpty())
            return;

        ReplayMtl mat = materials.getOrCreateMtl("entities.mtl", ItemModelAdapter.ITEM_MTL, n -> {
           ReplayMtl mtl = new ReplayMtl(Mtls.create(n));
           mtl.mtl().setMapKd("world.png");
           mtl.mtl().setMapD("world.png");
           mtl.properties().put("item", ReplayMtl.Property.of(true));
           return mtl;
        });

        for (var layer : ((AccessorItemStackRenderState) itemState).getLayers()) {
            BakedModel model = ((AccessorLayerRenderState) layer).getModel();
            if (model == null)
                continue; // TODO: Special models

            String name = itemModelNames.computeIfAbsent(new HandedModel(arm, model), m -> "item." + itemModelNames.size());

            capture.getModelParts().computeIfAbsent(name, n -> {
                PoseStack matrices = new PoseStack();
                matrices.mulPose(Axis.XP.rotationDegrees(-90.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
                matrices.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F, 0.125F, -0.625F);

                Obj obj = Objs.create();
                obj.setMtlFileNames(Collections.singleton("entities.mtl"));
                obj.setActiveMaterialGroupName(mat.getName());

                ObjVertexConsumer consumer = new ObjVertexConsumer(obj);
                itemState.render(matrices, new WrappedVertexConsumerProvider(consumer), 1, OverlayTexture.NO_OVERLAY);
                consumer.pushFace();

                // TODO: Is there a dynamic way to do this?
                String parentName = arm == HumanoidArm.LEFT ? "root/left_arm" : "root/right_arm";
                capture.getParents().put(n, parentName);

                return obj;
            });

            // Bogus frame to make sure it says visible
            capture.addFrame(name, tick, AnimationCurve.CurveFormat.EMPTY, null, null, null);


        }
    }
}
