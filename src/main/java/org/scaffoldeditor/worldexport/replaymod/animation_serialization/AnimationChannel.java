package org.scaffoldeditor.worldexport.replaymod.animation_serialization;

import net.minecraft.util.math.Vec3d;

public interface AnimationChannel<T> {
    public int numValues();
    public T read(double... values) throws IndexOutOfBoundsException;
    public double[] write(T value);

    public Class<? extends T> getChannelType();
    
    @SuppressWarnings("unchecked")
    public static <T> AnimationChannel<T> castChannel(AnimationChannel<?> channel, Class<T> clazz)
            throws ClassCastException {
        if (!clazz.isAssignableFrom(channel.getChannelType())) {
            throw new ClassCastException(channel.getChannelType().getSimpleName()
                    + " is not compatible with an animation channel of type " + clazz.getSimpleName());
        }
        return (AnimationChannel<T>) channel;
    }

    public static class VectorChannel implements AnimationChannel<Vec3d> {

        @Override
        public int numValues() {
            return 3;
        }

        @Override
        public Vec3d read(double... values) throws IndexOutOfBoundsException {
            return new Vec3d(values[0], values[1], values[2]);
        }

        @Override
        public double[] write(Vec3d value) {
            return new double[] { value.x, value.y, value.z };
        }

        @Override
        public Class<? extends Vec3d> getChannelType() {
            return Vec3d.class;
        }
        
    }

    public static class FloatChannel implements AnimationChannel<Float> {

        @Override
        public int numValues() {
            return 1;
        }

        @Override
        public Float read(double... values) throws IndexOutOfBoundsException {
            return (float) values[0];
        }

        @Override
        public double[] write(Float value) {
            return new double[] { value };
        }

        @Override
        public Class<? extends Float> getChannelType() {
            return Float.class;
        }
        
    }
}
