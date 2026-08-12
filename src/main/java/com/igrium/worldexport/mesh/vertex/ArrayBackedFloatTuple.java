package com.igrium.worldexport.mesh.vertex;

import de.javagl.obj.FloatTuple;
import lombok.EqualsAndHashCode;

/**
 * Shitty workaround for lack of external methods to construct float tuples
 */
@EqualsAndHashCode
public class ArrayBackedFloatTuple implements FloatTuple {
    private final float[] values;

    public ArrayBackedFloatTuple(float[] values) {
        this.values = values;
    }

    @Override
    public float getX() {
        return values[0];
    }

    @Override
    public float getY() {
        return values[1];
    }

    @Override
    public float getZ() {
        return values[2];
    }

    @Override
    public float getW() {
        return values[3];
    }

    @Override
    public float get(int index) {
        return values[index];
    }

    @Override
    public int getDimensions() {
        return values.length;
    }
}
