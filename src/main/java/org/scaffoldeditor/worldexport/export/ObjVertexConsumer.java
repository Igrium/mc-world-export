package org.scaffoldeditor.worldexport.export;

import de.javagl.obj.Obj;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;

/**
 * A vertex consumer that feeds vertices into an OBJ.
 */
public class ObjVertexConsumer implements VertexConsumer {
    
    public final Obj baseObj;
    public final Vec3d offset;

    float[][] vertCache = new float[4][];
    float[][] normalCache = new float[4][];
    float[][] texCache = new float[4][];
    private int head = 0;
    
    public ObjVertexConsumer(Obj baseObj, Vec3d offset) {
        this.baseObj = baseObj;
        this.offset = offset;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        vertCache[head] = new float[] { (float) (x + offset.x), (float) (y + offset.y), (float) (z + offset.z) };
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        texCache[head] = new float[] { (float) u, (float) v };
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        normalCache[head] = new float[] { x, y, z };
        return this;
    }

    @Override
    public void next() {
        if (head >= 3) {
            int objHead = baseObj.getNumVertices();
            int[] indices = new int[4];
            // baseObj.setActiveGroupNames(Arrays.asList(MeshWriter.genGroupName(0)));

            for (int i = 0; i < 4; i++) {
                indices[i] = objHead + i;
                baseObj.addVertex(vertCache[i][0], vertCache[i][1], vertCache[i][2]);
                baseObj.addNormal(normalCache[i][0], normalCache[i][1], normalCache[i][2]);
                baseObj.addTexCoord(texCache[i][0], 1 - texCache[i][1]);
            }

            baseObj.addFace(indices, indices, indices);

            head = 0;
        } else {
            head++;
        }
        
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {

    }

    @Override
    public void unfixColor() {

    }
    
}
