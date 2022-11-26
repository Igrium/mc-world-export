package org.scaffoldeditor.worldexport.vcap;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import de.javagl.obj.Obj;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;

/**
 * A vertex consumer that feeds vertices into an OBJ.
 */
public class ObjVertexConsumer implements VertexConsumer {
    
    public final Obj baseObj;
    private Matrix4dc transform;
    private Vector3d posCache = new Vector3d();

    float[][] vertCache = new float[4][];
    float[][] normalCache = new float[4][];
    float[][] texCache = new float[4][];
    private int head = 0;
    
    public ObjVertexConsumer(Obj baseObj, Vec3d offset) {
        this.baseObj = baseObj;
        Matrix4d transform = new Matrix4d();
        this.transform = transform.translate(offset.x, offset.y, offset.z);
    }

    public ObjVertexConsumer(Obj baseObj, Matrix4dc transform) {
        this.baseObj = baseObj;
        this.transform = transform;
    }

    public ObjVertexConsumer(Obj baseObj) {
        this.baseObj = baseObj;
        this.transform = new Matrix4d();
    }

    public Matrix4dc getTransform() {
        return transform;
    }

    public void setTransform(Matrix4dc transform) {
        this.transform = transform;
    }

    public void setTransform(Vec3d offset) {
        setTransform(offset.getX(), offset.getY(), offset.getZ());
    }

    public void setTransform(Vector3dc offset) {
        Matrix4d transform = new Matrix4d();
        this.transform = transform.translate(offset);
    }

    public void setTransform(double x, double y, double z) {
        Matrix4d transform = new Matrix4d();
        this.transform = transform.translate(x, y, z);
    }

    @Override
    public ObjVertexConsumer vertex(double x, double y, double z) {
        posCache.set(x, y, z).mulPosition(transform);
        vertCache[head] = new float[] { (float) posCache.x(), (float) posCache.y(), (float) posCache.z() };
        return this;
    }

    @Override
    public ObjVertexConsumer color(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public ObjVertexConsumer texture(float u, float v) {
        texCache[head] = new float[] { (float) u, (float) v };
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
        Vector3f vec = new Vector3f(x, y, z).mulDirection(transform);
        normalCache[head] = new float[] { vec.x(), vec.y(), vec.z() };
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
