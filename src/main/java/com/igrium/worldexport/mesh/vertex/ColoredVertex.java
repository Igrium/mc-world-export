package com.igrium.worldexport.mesh.vertex;

import de.javagl.obj.FloatTuple;
import org.joml.Vector3fc;

public record ColoredVertex(float x, float y, float z, float r, float g, float b) implements FloatTuple {

    public ColoredVertex(Vector3fc pos, Vector3fc color) {
        this(pos.x(), pos.y(), pos.z(), color.x(), color.y(), color.z());
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getZ() {
        return z;
    }

    @Override
    public float getW() {
        return x;
    }

    @Override
    public float get(int index) {
        return switch (index) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            case 3 -> r;
            case 4 -> g;
            case 5 -> b;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public int getDimensions() {
        return 6;
    }
}
