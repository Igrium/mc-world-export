package com.igrium.worldexport.entity;

import org.joml.Matrix4fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Separate translation/rotation/scale without reading a scaled matrix as a unit rotation. */
public final class CaptureTransforms {
    private CaptureTransforms() {}
    public record Pose(Vector3f position,Quaternionf rotation,Vector3f scale,boolean sheared) {}
    public static Pose decompose(Matrix4fc matrix) {
        Vector3f position=matrix.getTranslation(new Vector3f());
        Vector3f scale=matrix.getScale(new Vector3f());
        if (matrix.determinant3x3()<0) scale.x=-scale.x;
        if (Math.abs(scale.x)<1e-8 || Math.abs(scale.y)<1e-8 || Math.abs(scale.z)<1e-8)
            return new Pose(position,new Quaternionf(),scale,false);
        var rotationMatrix=new Matrix4f(matrix).scale(1/scale.x,1/scale.y,1/scale.z);
        var x=rotationMatrix.getColumn(0,new Vector3f());
        var y=rotationMatrix.getColumn(1,new Vector3f());
        var z=rotationMatrix.getColumn(2,new Vector3f());
        boolean shear=Math.abs(x.dot(y))>1e-4 || Math.abs(x.dot(z))>1e-4 || Math.abs(y.dot(z))>1e-4;
        return new Pose(position,rotationMatrix.getNormalizedRotation(new Quaternionf()).normalize(),scale,shear);
    }
}
