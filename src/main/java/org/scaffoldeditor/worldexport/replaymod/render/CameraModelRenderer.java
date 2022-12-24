package org.scaffoldeditor.worldexport.replaymod.render;

import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/**
 * Renders a camera in the viewport.
 */
public class CameraModelRenderer {

    public static final Identifier TEXTURE = new Identifier("replaymod", "camera_head.png");
    // private final RenderLayer RENDER_LAYER = RenderLayer.getEntitySolid(TEXTURE);
    private final RenderLayer RENDER_LAYER = RenderLayer.getEntityTranslucentEmissive(TEXTURE);

    private final ModelPart model;

    public CameraModelRenderer() {
        model = CameraEntityRenderer.getTexturedModelData().createModel();
    }

    public void render(AbstractCameraAnimation animation, double time, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RENDER_LAYER);
        matrices.push();

        Vec3d pos = animation.getPositionAt(time);
        Rotation rot = animation.getRotationAt(time);

        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(Vec3f.POSITIVE_Y.getRadialQuaternion((float) rot.yaw()));
        matrices.multiply(Vec3f.NEGATIVE_X.getRadialQuaternion((float) (Math.toRadians(90) - rot.pitch())));
        matrices.multiply(Vec3f.POSITIVE_Z.getRadialQuaternion((float) rot.roll()));
        
        // int rgb = animation.getColor().getColorValue();
        // float r = ((rgb >> 16) & 0xFF) / 256f;
        // float g = ((rgb >> 8) & 0xFF) / 256f;
        // float b = (rgb & 0xFF) / 256f;

        model.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);

        matrices.pop();
    }
}
