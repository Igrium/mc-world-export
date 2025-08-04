package com.igrium.worldexport.entity.models;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelAdapter;
import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import com.igrium.worldexport.mesh.VertexConsumers.WrappedVertexConsumerProvider;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.tex.ReplayMtl;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;

public class ItemModelAdapter extends ModelAdapter<ItemEntity, ItemEntityRenderState> {

    private final ItemEntityRenderer renderer;

    private final Random random = Random.create();

    public ItemModelAdapter(ItemEntityRenderer renderer) {
        super(renderer);
        this.renderer = renderer;

    }

    @Override
    public void capture(ItemEntity entity, ItemEntityRenderState state, CapturedEntity capture, MaterialHolder materials, Vec3d offset, int tick) {
        Vec3d pos = entity.getPos().add(offset);
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS, pos.toVector3f(), null, null);

        if (state.itemRenderState.isEmpty())
            return;

        String partName = state.renderedAmount > 1 ? "item_" + state.renderedAmount : "item";

        float baseYOffset = 0.25f;
        float bobbing = MathHelper.sin(state.age / 10.0F + state.uniqueOffset) * 0.1f + 0.1f;
        float itemScaleY = state.itemRenderState.getTransformation().scale.y();

        Vector3f localPos = new Vector3f(0, bobbing + baseYOffset * itemScaleY, 0);
        float rotation = ItemEntity.getRotation(state.age, state.uniqueOffset);
        Quaternionf rot = new Quaternionf().rotateY(rotation);

        capture.addFrame(partName, tick, AnimationCurve.CurveFormat.POS_ROT, localPos, rot, null);

        setupMaterials(materials);

        // Capture mesh if needed.
        capture.getModelParts().computeIfAbsent(partName, n -> {
            Obj obj = Objs.create();
            obj.setMtlFileNames(Collections.singleton("entities.mtl"));
            obj.setActiveMaterialGroupName(ITEM_MTL); // TODO: Enable glint if needed

            MatrixStack matrices = new MatrixStack();
            ObjVertexConsumer objConsumer = new ObjVertexConsumer(obj);
            WrappedVertexConsumerProvider vertices = new WrappedVertexConsumerProvider(objConsumer);
            vertices.getBlacklist().add(RenderLayer.getGlint());
            vertices.getBlacklist().add(RenderLayer.getEntityGlint());

            ItemEntityRenderer.renderStack(matrices, vertices, 255, state, random);
            objConsumer.pushFace();

            return obj;
        });
    }

    public static String ITEM_MTL = "items";
    public static String ITEM_MTL_GLINT = "items_glint";

    /**
     * Add all the materials needed for rendering items.
     * @param materials Material consumer to add to.
     */
    public static void setupMaterials(MaterialHolder materials) {
        materials.getOrCreateMtl("entities.mtl", ITEM_MTL, n -> {
            var mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd(ReplayCapture.WORLD_TEX);
            mtl.mtl().setMapD(ReplayCapture.WORLD_TEX);
            return mtl;
        });

        materials.getOrCreateMtl("entities.mtl", ITEM_MTL_GLINT, n -> {
            var mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd(ReplayCapture.WORLD_TEX);
            mtl.mtl().setMapD(ReplayCapture.WORLD_TEX);

            mtl.properties().put("glint", ReplayMtl.Property.of(true));
            return mtl;
        });
    }

}
