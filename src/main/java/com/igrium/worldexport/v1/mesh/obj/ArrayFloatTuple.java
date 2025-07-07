package com.igrium.worldexport.v1.mesh.obj;

import de.javagl.obj.FloatTuple;

import java.util.Arrays;

public class ArrayFloatTuple implements FloatTuple {
    private final float[] array;

    public static ArrayFloatTuple of(float... values) {
        return new ArrayFloatTuple(values.clone());
    }

    ArrayFloatTuple(float... values) {
        this.array = values;
    }

    @Override
    public float getX() {
        return array[0];
    }

    @Override
    public float getY() {
        return array[1];
    }

    @Override
    public float getZ() {
        return array[2];
    }

    @Override
    public float getW() {
        return array[3];
    }

    @Override
    public float get(int index) {
        return array[index];
    }

    @Override
    public int getDimensions() {
        return array.length;
    }

    public float[] getValues() {
        return array.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof ArrayFloatTuple other) {
            return Arrays.equals(this.array, other.array);
        } else if (obj instanceof FloatTuple other) {
            if (other.getDimensions() != getDimensions())
                return false;

            for (int i = 0; i < getDimensions(); i++) {
                if (get(i) != other.get(i))
                    return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }
}
