package org.scaffoldeditor.worldexport.replaymod.render;

import org.joml.Quaternionf;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Renders a camera in the viewport.
 */
public class CameraModelRenderer {

    public static final Identifier TEXTURE = new Identifier("worldexport", "textures/camera.png");
    private final RenderLayer RENDER_LAYER = RenderLayer.getEntitySolid(TEXTURE);
    // private final RenderLayer RENDER_LAYER = RenderLayer.getEntityTranslucentEmissive(TEXTURE);

    private final ModelPart root;
    private final ModelPart main;
    private final ModelPart tinted;

    public CameraModelRenderer() {
        root = getTexturedModelData().createModel();
        main = root.getChild("main");
        tinted = root.getChild("tinted");
    }

    public void render(AbstractCameraAnimation animation, double time, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RENDER_LAYER);
        matrices.push();

        Vec3d pos = animation.getPositionAt(time);
        Rotation rot = animation.getRotationAt(time);

        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(new Quaternionf().rotateAxis((float) (rot.yaw()), 0, 1, 0));
        matrices.multiply(new Quaternionf().rotateAxis((float) (Math.toRadians(90) - rot.pitch()), -1, 0, 0));
        matrices.multiply(new Quaternionf().rotateAxis((float) rot.roll(), 0, 0, 1));
        // matrices.multiply(Vec3f.POSITIVE_Y.getRadialQuaternion((float) rot.yaw()));
        // matrices.multiply(Vec3f.NEGATIVE_X.getRadialQuaternion((float) (Math.toRadians(90) - rot.pitch())));
        // matrices.multiply(Vec3f.POSITIVE_Z.getRadialQuaternion((float) rot.roll()));
        
        ReadableColor color = animation.getColor();
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        // root.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);
        main.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
        tinted.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, r, g, b, 1f);

        matrices.pop();
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelPartData = modelData.getRoot();
        modelPartData.addChild("tinted",
                ModelPartBuilder.create().uv(0, 16).cuboid(-3.0F, -3.0F, 4.0F, 6.0F, 6.0F, 1.0F, new Dilation(0.0F)),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));

        modelPartData.addChild("main",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.0F)),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F));
        return TexturedModelData.of(modelData, 32, 32);
    }
}
