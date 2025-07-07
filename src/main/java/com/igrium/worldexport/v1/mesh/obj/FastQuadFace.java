package com.igrium.worldexport.v1.mesh.obj;

import de.javagl.obj.ObjFace;

public class FastQuadFace implements ObjFace {
    private static final int VERTEX_STRIDE = 3;

    private final int[] packedIndices;

    public FastQuadFace(int[] packedIndices) {
        assert packedIndices.length == VERTEX_STRIDE * 4;
        this.packedIndices = packedIndices;
    }

    @Override
    public int getNumVertices() {
        return 4;
    }

    @Override
    public boolean containsTexCoordIndices() {
        return true;
    }

    @Override
    public boolean containsNormalIndices() {
        return true;
    }

    @Override
    public int getVertexIndex(int number) {
        return packedIndices[number * VERTEX_STRIDE];
    }

    @Override
    public int getTexCoordIndex(int number) {
        return packedIndices[number * VERTEX_STRIDE + 1];
    }

    @Override
    public int getNormalIndex(int number) {
        return packedIndices[number * VERTEX_STRIDE + 2];
    }

    public static int[] pack(ObjFace face, int[] out) {
        if (out.length < VERTEX_STRIDE * 4) {
            throw new IllegalArgumentException("Insufficient room in output array");
        }
        if (face.getNumVertices() != 4) {
            throw new IllegalArgumentException("Only quads are supported in FastQuadObj");
        }

        for (int i = 0; i < 4; i++) {
            int start = i * VERTEX_STRIDE;
            out[start] = face.getVertexIndex(i);
            out[start + 1] = face.getTexCoordIndex(i);
            out[start + 2] = face.getNormalIndex(i);
        }

        return out;
    }

}
