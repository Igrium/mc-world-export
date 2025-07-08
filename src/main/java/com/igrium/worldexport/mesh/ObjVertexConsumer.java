package com.igrium.worldexport.mesh;

import lombok.Setter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;


import de.javagl.obj.Obj;
import lombok.Getter;
import net.minecraft.client.render.VertexConsumer;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * A vertex consumer that feeds vertices into an OBJ.
 */
public class ObjVertexConsumer implements VertexConsumer {

    @Getter
    private final Obj baseObj;

    private final Vector3f posCache = new Vector3f();

    @Getter @Setter
    private boolean enableNormals = true;

    @Getter @Setter
    private boolean enableColors = true;

    @Getter @Setter
    private @Nullable String material;

    @Getter
    private @Nullable String activeGroup;

    public void setActiveGroup(@Nullable String activeGroup) {
        if (Objects.equals(activeGroup, this.activeGroup))
            return;
        this.activeGroup = activeGroup;
        activeGroupSet = activeGroup != null ? Collections.singleton(activeGroup) : Collections.emptySet();
    }

    private Collection<String> activeGroupSet = Collections.emptySet();

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
        endVertex();
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
        Vector3f vec = new Vector3f(x, y, z).mulDirection(matrices.peek().getPositionMatrix());
        vec = vec.normalize();
        normalCache[head] = new float[]{vec.x(), vec.y(), vec.z()};
        return this;
    }

    public void endVertex() {
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

                // We're not tracking a separate normal index,
                // so we have to add normals anyway, even if we're not using them.
                baseObj.addNormal(normalCache[i][0], normalCache[i][1], normalCache[i][2]);
                baseObj.addTexCoord(texCache[i][0], 1 - texCache[i][1]);
            }

            baseObj.setActiveMaterialGroupName(material);
            baseObj.setActiveGroupNames(activeGroupSet);

            if (enableNormals) {
                baseObj.addFace(indices, indices, indices);
            } else {
                baseObj.addFace(indices, null, indices);
            }
            head = 0;
        } else {
            head++;
        }

    }

}
