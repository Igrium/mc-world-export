package com.igrium.worldexport.mesh;

import de.javagl.obj.Obj;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A vertex consumer that feeds vertices into an OBJ.
 */
public class ObjVertexConsumer implements VertexConsumer {

    @Getter
    private final Obj baseObj;

    private final Vector3f posCache = new Vector3f();

    @Getter
    private boolean enableNormals = true;

    public void setEnableNormals(boolean enableNormals) {
        if (isInitialized) {
            throw new IllegalStateException("Can't toggle normals after verts have been written");
        }
        this.enableNormals = enableNormals;
    }

    @Getter @Setter
    private boolean enableColors = true;

    @Getter @Setter
    private @Nullable String material;

    private boolean isInitialized;

    public final MatrixStack matrices = new MatrixStack();

    float[][] vertCache = new float[4][];
    float[][] colorCache = new float[4][];
    float[][] normalCache = new float[4][];
    float[][] texCache = new float[4][];
    private int head = -1;

    public ObjVertexConsumer(Obj baseObj, Vec3d offset) {
        this.baseObj = baseObj;
        matrices.translate(offset.x, offset.y, offset.z);
    }

    public ObjVertexConsumer(Obj baseObj) {
        this.baseObj = baseObj;
    }

    @Override
    public ObjVertexConsumer vertex(float x, float y, float z) {
        isInitialized = true;
        tryEndVertex();
        head++;
        posCache.set(x, y, z).mulPosition(matrices.peek().getPositionMatrix());
        vertCache[head] = new float[]{posCache.x(), posCache.y(), posCache.z()};
        return this;
    }

    @Override
    public ObjVertexConsumer color(int red, int green, int blue, int alpha) {
        colorCache[head] = new float[]{red, green, blue};
        return this;
    }

    @Override
    public ObjVertexConsumer texture(float u, float v) {
        texCache[head] = new float[]{u, v};
        return this;
    }

    @Override
    public ObjVertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public ObjVertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public ObjVertexConsumer normal(float x, float y, float z) {
        if (!enableNormals)
            return this;
        Vector3f vec = new Vector3f(x, y, z).mulDirection(matrices.peek().getPositionMatrix());
        vec = vec.normalize();
        normalCache[head] = new float[]{vec.x(), vec.y(), vec.z()};
        return this;
    }

    public void end() {
        tryEndVertex();
    }

    private void tryEndVertex() {
        if (head >= 3) {
            int objHead = baseObj.getNumVertices();
            int[] indices = new int[4];

            for (int i = 0; i < 4; i++) {
                indices[i] = objHead + i;
                // Per Blender standard, colors get stored with the vertex.
                if (enableColors) {
                    baseObj.addVertex(new ColoredVertex(vertCache[i][0], vertCache[i][1], vertCache[i][2],
                            colorCache[i][0], colorCache[i][1], colorCache[i][2]));
                } else {
                    baseObj.addVertex(vertCache[i][0], vertCache[i][1], vertCache[i][2]);
                }

                if (enableNormals) {
                    baseObj.addNormal(normalCache[i][0], normalCache[i][1], normalCache[i][2]);
                }
                baseObj.addTexCoord(texCache[i][0], 1 - texCache[i][1]);
            }


            if (enableNormals) {
                baseObj.addFace(indices, indices, indices);
            } else {
                baseObj.addFace(indices, indices, null);
            }
            head = -1;
        }
    }
}
