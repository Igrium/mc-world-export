package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.models.ItemModelAdapter;
import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import com.igrium.worldexport.mixin.AccessorItemStackRenderState;
import com.igrium.worldexport.mixin.AccessorLayerRenderState;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayMtl;
import com.mojang.blaze3d.vertex.QuadInstance;
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
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.math.Axis;

import java.util.*;

public class HeldItemFeatureAdapter<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel<?>>
        extends FeatureAdapter<S, M> {

    public HeldItemFeatureAdapter(RenderLayer<S, M> renderer) {
        super(renderer);
    }

    private record HandedModel(HumanoidArm hand, List<BakedQuad> model) {
    }

    ;

    public static final String ITEM_MAT = "items";

    @SuppressWarnings({"unchecked", "rawtypes"})
    // M is only used internally, so as long as it's self-consistent, its value doesn't matter.
    public static <S extends ArmedEntityRenderState> FeatureAdapter<S, ?> create(RenderLayer<S, ?> renderer) {
        return new HeldItemFeatureAdapter<>((ItemInHandLayer) renderer); // If render properly casts to
        // ArmorFeatureRenderer, then S must be in-bounds.
    }

    @Override
    public void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle,
                        float limbDistance, int tick) {
        captureItem(capture, materials, state, state.rightHandItemState, HumanoidArm.RIGHT, tick);
        captureItem(capture, materials, state, state.leftHandItemState, HumanoidArm.LEFT, tick);
    }

    private final Map<HandedModel, String> itemModelNames = new HashMap<>();

    protected void captureItem(CapturedEntity capture, MaterialHolder materials, S state,
                               ItemStackRenderState itemState, HumanoidArm arm, int tick) {
        if (itemState.isEmpty())
            return;

        ReplayMtl mat = materials.getOrCreateMtl("entities.mtl", ItemModelAdapter.ITEM_MTL, n -> {
            ReplayMtl mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd("world.png");
            mtl.mtl().setMapD("world.png");
            mtl.properties().put("item", ReplayMtl.Property.of(true));
            return mtl;
        });

        PoseStack matrices = new PoseStack();
        matrices.mulPose(Axis.XP.rotationDegrees(-90.0F));
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
        // Magic numbers taken from feature renderer
        matrices.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F, 0.125F, -0.625F);

        ItemQuadCollector collector = new ItemQuadCollector();
        itemState.submit(matrices, collector, 1, OverlayTexture.NO_OVERLAY, 0);


        // TODO: does a submission represent an entire item or each item model part?
        for (var submission : collector.getSubmissions()) {
            String name = itemModelNames.computeIfAbsent(new HandedModel(arm, submission.quads()),
                    m -> "item." + itemModelNames.size());

            capture.getModelParts().computeIfAbsent(name, n -> {
                Obj obj = Objs.create();
                obj.setMtlFileNames(Set.of("entities.mtl"));
                obj.setActiveMaterialGroupName(mat.getName());

                ObjVertexConsumer consumer = new ObjVertexConsumer(obj);
                for (BakedQuad quad : submission.quads()) {
                    consumer.putBakedQuad(submission.stack().last(), quad, new QuadInstance());
                }
                consumer.pushFace();

                String parentName = arm == HumanoidArm.LEFT ? "root/left_arm" : "root/right_arm";
                capture.getParents().put(n, parentName);

                return obj;
            });
            // Bogus frame to make sure it stays visible
            capture.addFrame(name, tick, AnimationCurve.CurveFormat.EMPTY, null, null, null);
        }

    }
}
