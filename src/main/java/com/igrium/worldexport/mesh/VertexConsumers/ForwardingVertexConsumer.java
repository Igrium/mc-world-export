package com.igrium.worldexport.mesh.VertexConsumers;

import lombok.Getter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
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
    public VertexConsumer vertex(float x, float y, float z) {
        base.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        base.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        base.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        base.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        base.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        base.normal(x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        base.vertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        base.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer color(int argb) {
        base.color(argb);
        return this;
    }

    @Override
    public VertexConsumer colorRgb(int rgb) {
        base.colorRgb(rgb);
        return this;
    }

    @Override
    public VertexConsumer light(int uv) {
        base.light(uv);
        return this;
    }

    @Override
    public VertexConsumer overlay(int uv) {
        base.overlay(uv);
        return this;
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float red, float green, float blue, float f, int i, int j) {
        base.quad(matrixEntry, quad, red, green, blue, f, i, j);
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green, float blue,
                     float f, int[] is, int i, boolean bl) {
        base.quad(matrixEntry, quad, brightnesses, red, green, blue, f, is, i, bl);
    }

    @Override
    public VertexConsumer vertex(Vector3f vec) {
        base.vertex(vec);
        return this;
    }

    @Override
    public VertexConsumer vertex(MatrixStack.Entry matrix, Vector3f vec) {
        base.vertex(matrix, vec);
        return this;
    }

    @Override
    public VertexConsumer vertex(MatrixStack.Entry matrix, float x, float y, float z) {
        base.vertex(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
        base.vertex(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer normal(MatrixStack.Entry matrix, float x, float y, float z) {
        base.normal(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer normal(MatrixStack.Entry matrix, Vector3f vec) {
        base.normal(matrix, vec);
        return this;
    }
}
