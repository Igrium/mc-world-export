package com.igrium.worldexport.v1.mesh;

import de.javagl.obj.FloatTuple;

public record ColoredVertex(float x, float y, float z, float r, float g, float b) implements FloatTuple {
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
