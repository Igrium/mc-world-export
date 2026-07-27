package com.igrium.worldexport.mesh.VertexConsumers;

import lombok.Getter;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
    public VertexConsumer addVertex(float x, float y, float z) {
        base.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        base.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        base.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        base.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        base.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        base.setNormal(x, y, z);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        base.addVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer setColor(float red, float green, float blue, float alpha) {
        base.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        base.setColor(argb);
        return this;
    }

    @Override
    public VertexConsumer setWhiteAlpha(int rgb) {
        base.setWhiteAlpha(rgb);
        return this;
    }

    @Override
    public VertexConsumer setLight(int uv) {
        base.setLight(uv);
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int uv) {
        base.setOverlay(uv);
        return this;
    }

    @Override
    public void putBulkData(PoseStack.Pose matrixEntry, BakedQuad quad, float red, float green, float blue, float f, int i, int j) {
        base.putBulkData(matrixEntry, quad, red, green, blue, f, i, j);
    }

    @Override
    public void putBulkData(PoseStack.Pose matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue,
                            float f, int[] is, int i, boolean bl) {
        base.putBulkData(matrixEntry, quad, brightnesses, red, green, blue, f, is, i, bl);
    }

    @Override
    public VertexConsumer addVertex(Vector3f vec) {
        base.addVertex(vec);
        return this;
    }

    @Override
    public VertexConsumer addVertex(PoseStack.Pose matrix, Vector3f vec) {
        base.addVertex(matrix, vec);
        return this;
    }

    @Override
    public VertexConsumer addVertex(PoseStack.Pose matrix, float x, float y, float z) {
        base.addVertex(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer addVertex(Matrix4f matrix, float x, float y, float z) {
        base.addVertex(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setNormal(PoseStack.Pose matrix, float x, float y, float z) {
        base.setNormal(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setNormal(PoseStack.Pose matrix, Vector3f vec) {
        base.setNormal(matrix, vec);
        return this;
    }
}
