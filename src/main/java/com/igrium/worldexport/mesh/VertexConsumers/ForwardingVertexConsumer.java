package com.igrium.worldexport.mesh.VertexConsumers;

import lombok.Getter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

/**
 * A simple wrapper over an existing vertex consumer. Used primarily to stop == checks from succeeding.
 */
@Getter
public class ForwardingVertexConsumer implements VertexConsumer {
    private final VertexConsumer base;

    public ForwardingVertexConsumer(VertexConsumer base) {
        this.base = base;
    }

    @Override
    public @NonNull VertexConsumer addVertex(float x, float y, float z) {
        base.addVertex(x, y, z);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
        base.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setColor(int argb) {
        base.setColor(argb);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv(float u, float v) {
        base.setUv(u, v);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv1(int u, int v) {
        base.setUv1(u, v);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv2(int u, int v) {
        base.setUv2(u, v);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setNormal(float x, float y, float z) {
        base.setNormal(x, y, z);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setLineWidth(float width) {
        base.setLineWidth(width);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
        base.addVertex(x, y, z, color, u, v, overlayCoords, lightCoords, nx, ny, nz);
    }

    @Override
    public @NonNull VertexConsumer setColor(float red, float green, float blue, float alpha) {
        base.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setLight(int packedLightCoords) {
        base.setLight(packedLightCoords);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setOverlay(int packedOverlayCoords) {
        base.setOverlay(packedOverlayCoords);
        return this;
    }

    @Override
    public void putBlockBakedQuad(float x, float y, float z, @NonNull BakedQuad quad, @NonNull QuadInstance instance) {
        base.putBlockBakedQuad(x, y, z, quad, instance);
    }

    @Override
    public void putBakedQuad(PoseStack.@NonNull Pose pose, @NonNull BakedQuad quad, @NonNull QuadInstance instance) {
        base.putBakedQuad(pose, quad, instance);
    }

    @Override
    public @NonNull VertexConsumer addVertex(@NonNull Vector3fc position) {
        base.addVertex(position);
        return this;
    }

    @Override
    public @NonNull VertexConsumer addVertex(PoseStack.@NonNull Pose pose, @NonNull Vector3fc position) {
        base.addVertex(pose, position);
        return this;
    }

    @Override
    public @NonNull VertexConsumer addVertex(PoseStack.@NonNull Pose pose, float x, float y, float z) {
        base.addVertex(pose, x, y, z);
        return this;
    }

    @Override
    public @NonNull VertexConsumer addVertex(@NonNull Matrix4fc pose, float x, float y, float z) {
        base.addVertex(pose, x, y, z);
        return this;
    }

    @Override
    public @NonNull VertexConsumer addVertexWith2DPose(@NonNull Matrix3x2fc pose, float x, float y) {
        base.addVertexWith2DPose(pose, x, y);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setNormal(PoseStack.@NonNull Pose pose, float x, float y, float z) {
        base.setNormal(pose, x, y, z);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setNormal(PoseStack.@NonNull Pose pose, @NonNull Vector3fc normal) {
        base.setNormal(pose, normal);
        return this;
    }
}