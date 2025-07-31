package com.igrium.worldexport.mesh;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Supplier;

/**
 * A vertex consumer that assembles vertices into faces and then handles them all at once.
 */
public abstract class FaceVertexConsumer implements VertexConsumer {

    public final MatrixStack matrices = new MatrixStack();

    private final Vector3f[] vertCache = new Vector3f[4];
    private final Vector3f[] colorCache = new Vector3f[4];
    private final Vector3f[] normalCache = new Vector3f[4];
    private final Vector2f[] texCache = new Vector2f[4];

    private int head = -1;


    public FaceVertexConsumer() {
        fillArray(vertCache, Vector3f::new);
        fillArray(colorCache, Vector3f::new);
        fillArray(normalCache, Vector3f::new);
        fillArray(texCache, Vector2f::new);
    }

    @Override
    public FaceVertexConsumer vertex(float x, float y, float z) {
        tryEndFace();
        head++;
        vertCache[head].set(x, y, z).mulPosition(matrices.peek().getPositionMatrix());
        return this;
    }

    @Override
    public FaceVertexConsumer color(int red, int green, int blue, int alpha) {
        return color(red / 255f, green / 255f, blue / 255f, alpha / 255f);
    }

    @Override
    public FaceVertexConsumer color(float red, float green, float blue, float alpha) {
        colorCache[head].set(red, green, blue);
        return this;
    }

    @Override
    public FaceVertexConsumer texture(float u, float v) {
        texCache[head].set(u, v);
        return this;
    }

    @Override
    public FaceVertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public FaceVertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public FaceVertexConsumer normal(float x, float y, float z) {
        normalCache[head].set(x, y, z).mulDirection(matrices.peek().getPositionMatrix()).normalize();
        return this;
    }

    /**
     * Called when four vertices have been added forming a face.
     *
     * @param vertices  The vertices in the face.
     * @param colors    The vertex colors in the face.
     * @param normals   The vertex normals.
     * @param texCoords The UV map of the face.
     * @implNote The supplied values are mutable references. Do not store without copying.
     */
    protected abstract void handleFace(Vector3fc[] vertices, Vector3fc[] colors, Vector3fc[] normals, Vector2fc[] texCoords);

    public void pushFace() {
        tryEndFace();
    }

    private void tryEndFace() {
        if (head >= 3) {
            handleFace(vertCache, colorCache, normalCache, texCache);
            head = -1;
        }
    }

    private static <T> void fillArray(T[] array, Supplier<? extends T> value) {
        for (int i = 0; i < array.length; i++) {
            array[i] = value.get();
        }
    }
}
