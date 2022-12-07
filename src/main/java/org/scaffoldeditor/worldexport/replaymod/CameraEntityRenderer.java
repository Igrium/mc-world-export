package org.scaffoldeditor.worldexport.replaymod;

import net.minecraft.client.model.ModelPart.Cuboid;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;

public class CameraEntityRenderer extends EntityRenderer<AnimatedCameraEntity> {

    public static final Identifier TEXTURE = new Identifier("replaymod", "camera_head.png");
    EntityModel<?> model;

    public CameraEntityRenderer(Context ctx) {
        super(ctx);
    }

    @Override
    public void render(AnimatedCameraEntity entity, float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);

        matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(entity.getRoll()));
        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(entity.getPitch()));
        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(entity.getYaw()));

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE));

        Cuboid cuboid = new Cuboid(0, 0, -4, -4, -4, 8, 8, 8, 0, 0, 0, false, 64, 64);
        cuboid.renderCuboid(matrices.peek(), buffer, light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);

        // //back
        // buffer.vertex(r, r + cubeSize, r).texture(3 * 8 / 64f, 8 / 64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r + cubeSize, r).texture(4*8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r, r).texture(4*8/64f, 2*8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r, r).texture(3*8/64f, 2*8/64f).color(255, 255, 255, 200).next();

        // //front
        // buffer.vertex(r + cubeSize, r, r + cubeSize).texture(2 * 8 / 64f, 2*8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r + cubeSize, r + cubeSize).texture(2 * 8 / 64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r + cubeSize, r + cubeSize).texture(8 / 64f, 8 / 64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r, r + cubeSize).texture(8 / 64f, 2*8/64f).color(255, 255, 255, 200).next();

        // //left
        // buffer.vertex(r + cubeSize, r + cubeSize, r).texture(0, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r + cubeSize, r + cubeSize).texture(8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r, r + cubeSize).texture(8/64f, 2*8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r+cubeSize, r, r).texture(0, 2*8/64f).color(255, 255, 255, 200).next();

        // //right
        // buffer.vertex(r, r + cubeSize, r + cubeSize).texture(2*8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r + cubeSize, r).texture(3*8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r, r).texture(3*8/64f, 2*8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r, r + cubeSize).texture(2 * 8 / 64f, 2 * 8 / 64f).color(255, 255, 255, 200).next();

        // //bottom
        // buffer.vertex(r + cubeSize, r, r).texture(3*8/64f, 0).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r, r + cubeSize).texture(3*8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r, r + cubeSize).texture(2*8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r, r).texture(2 * 8 / 64f, 0).color(255, 255, 255, 200).next();

        // //top
        // buffer.vertex(r, r + cubeSize, r).texture(8/64f, 0).color(255, 255, 255, 200).next();
        // buffer.vertex(r, r + cubeSize, r + cubeSize).texture(8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r + cubeSize, r + cubeSize).texture(2*8/64f, 8/64f).color(255, 255, 255, 200).next();
        // buffer.vertex(r + cubeSize, r + cubeSize, r).texture(2 * 8 / 64f, 0).color(255, 255, 255, 200).next();
    }

    @Override
    public Identifier getTexture(AnimatedCameraEntity var1) {
        return TEXTURE;
    }
    
}
