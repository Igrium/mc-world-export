package org.scaffoldeditor.worldexport.replaymod.render;

import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntity;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class CameraEntityRenderer extends EntityRenderer<AnimatedCameraEntity> {

    public static final Identifier TEXTURE = new Identifier("replaymod", "camera_head.png");
    // private final RenderLayer RENDER_LAYER = RenderLayer.getEntitySolid(TEXTURE);

    // private final ModelPart model;
    
    public CameraEntityRenderer(Context ctx) {
        super(ctx);
        // model = ctx.getPart(ReplayExportMod.CAMERA_MODEL_LAYER);
    }

    @Override
    public void render(AnimatedCameraEntity entity, float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);

        // VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RENDER_LAYER);
        // matrices.push();
        
        // matrices.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(180 + entity.getYaw(tickDelta)));
        // matrices.multiply(Vec3f.NEGATIVE_X.getDegreesQuaternion(entity.getPitch(tickDelta)));
        // matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(entity.getRoll()));
        
        // int rgb = entity.getColor().getColorValue();
        // float r = ((rgb >> 16) & 0xFF) / 256f;
        // float g = ((rgb >> 8) & 0xFF) / 256f;
        // float b = (rgb & 0xFF) / 256f;

        // model.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, r, g, b, 1);

        // matrices.pop();
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelPartData = modelData.getRoot();
        modelPartData.addChild(EntityModelPartNames.ROOT,
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                ModelTransform.pivot(0, 0, 0));
        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public Identifier getTexture(AnimatedCameraEntity var1) {
        return TEXTURE;
    }
    
}
