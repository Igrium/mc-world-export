package com.igrium.worldexport.entity.models;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelAdapter;
import com.igrium.worldexport.mesh.vertex.ObjVertexConsumer;
import com.igrium.worldexport.entity.features.ItemQuadCollector;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

public class ItemModelAdapter extends ModelAdapter<ItemEntity, ItemEntityRenderState> {

    /**
     * Material for items whose baked quads reference the items atlas (flat, generated models).
     */
    public static final String ITEM_MTL = "items";

    /**
     * Material for items whose baked quads reference the blocks atlas (block-shaped item models).
     */
    public static final String BLOCK_ITEM_MTL = "items_block";

    public static final String ITEM_GLINT_MTL = "items_glint";

    private final RandomSource random = RandomSource.create();

    public ItemModelAdapter(EntityRenderer<? super ItemEntity, ? extends ItemEntityRenderState> renderer) {
        super(renderer);
    }

    /**
     * Get (or create) the material for a group of baked quads, keyed by the atlas they sample from.
     */
    public static ReplayMtl getOrCreateItemMaterial(MaterialHolder materials, boolean glint, @Nullable Identifier atlas) {
        //noinspection deprecation
        boolean blockAtlas = TextureAtlas.LOCATION_BLOCKS.equals(atlas);

        String mat = glint ? ITEM_GLINT_MTL : blockAtlas ? BLOCK_ITEM_MTL : ITEM_MTL;
        String tex = blockAtlas ? ReplayCapture.WORLD_TEX : ReplayCapture.ITEM_TEX;

        return materials.getOrCreateMtl("entities.mtl", mat, n -> {
            var mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd(tex);
            mtl.mtl().setMapD(tex);
            mtl.properties().put("item", ReplayMtl.Property.of(true));
            if (glint) {
                mtl.properties().put("glint", ReplayMtl.Property.of(true));
            }
            return mtl;
        });
    }

    @Override
    public void capture(ItemEntity entity, ItemEntityRenderState state, CapturedEntity capture, MaterialHolder materials, Vec3 offset, int tick) {
        Vec3 pos = entity.position().add(offset);
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS, pos.toVector3f(), null, null);
        if (state.item.isEmpty()) return;

        String partName = state.count > 1 ? "item_" + state.count : "item";

        AABB boundingBox = state.item.getModelBoundingBox();
        float minOffsetY = -((float) boundingBox.minY) + 0.0625f;
        float bobbing = Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1f + 0.1f;

        Vector3f localPos = new Vector3f(0, bobbing + minOffsetY, 0);
        float rotation = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
        Quaternionf rot = new Quaternionf().rotateY(rotation);

        capture.addFrame(partName, tick, AnimationCurve.CurveFormat.POS_ROT, localPos, rot, null);

        // Capture mesh if needed.
        capture.getModelParts().computeIfAbsent(partName, n -> {
            Obj obj = Objs.create();
            obj.setMtlFileNames(Collections.singleton("entities.mtl"));

            ObjVertexConsumer consumer = new ObjVertexConsumer(obj);
            PoseStack matrices = new PoseStack();
            writeItemCluster(materials, matrices, consumer, state, random, boundingBox);
            consumer.pushFace();

            return obj;
        });
    }

    private static void writeItemCluster(MaterialHolder materials, PoseStack poseStack, ObjVertexConsumer consumer,
                                         ItemEntityRenderState state, RandomSource random, AABB modelBoundingBox) {
        int amount = state.count;
        if (amount == 0) return;

        random.setSeed(state.seed);
        float modelDepth = (float) modelBoundingBox.getZsize();

        if (modelDepth > 0.0625F) {
            writeItem(materials, poseStack, consumer, state.item);

            for (int i = 1; i < amount; i++) {
                poseStack.pushPose();
                float xo = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                float yo = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                float zo = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                poseStack.translate(xo, yo, zo);
                writeItem(materials, poseStack, consumer, state.item);
                poseStack.popPose();
            }
        } else {
            float offsetZ = modelDepth * 1.5F;
            poseStack.translate(0.0F, 0.0F, -(offsetZ * (amount - 1) / 2.0F));
            writeItem(materials, poseStack, consumer, state.item);
            poseStack.translate(0.0F, 0.0F, offsetZ);

            for (int i = 1; i < amount; i++) {
                poseStack.pushPose();
                float xo = (random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                float yo = (random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                poseStack.translate(xo, yo, 0.0F);
                writeItem(materials, poseStack, consumer, state.item);
                poseStack.popPose();
                poseStack.translate(0.0F, 0.0F, offsetZ);
            }
        }
    }

    private static void writeItem(MaterialHolder materials, PoseStack poseStack, ObjVertexConsumer consumer, ItemStackRenderState item) {
        ItemQuadCollector collector = new ItemQuadCollector();
        item.submit(poseStack, collector, 1, OverlayTexture.NO_OVERLAY, 0);

        for (var submission : collector.getSubmissions()) {
            List<BakedQuad> quads = submission.quads();
            if (quads.isEmpty())
                continue;

            //noinspection resource
            Identifier atlas = quads.getFirst().materialInfo().sprite().atlasLocation();
            ReplayMtl mat = getOrCreateItemMaterial(materials, submission.foilType() != ItemStackRenderState.FoilType.NONE, atlas);
            consumer.getObj().setActiveMaterialGroupName(mat.getName());

            for (BakedQuad quad : quads) {
                consumer.putBakedQuad(submission.pose(), quad, new QuadInstance());
            }
            // Flush this submission's faces now, before the next one switches the active material.
            consumer.pushFace();
        }
    }
}
