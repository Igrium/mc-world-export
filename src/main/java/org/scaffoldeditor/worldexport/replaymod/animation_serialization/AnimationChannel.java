package org.scaffoldeditor.worldexport.replaymod.animation_serialization;

import org.joml.Quaternionfc;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;

import net.minecraft.util.math.Vec3d;

public interface AnimationChannel<T> {
    public int numValues();
    public T read(double... values) throws IndexOutOfBoundsException;
    public double[] write(T value);

    public Class<? extends T> getChannelType();
    
    @SuppressWarnings("unchecked")
    @Deprecated
    public static <T> AnimationChannel<T> castChannel(AnimationChannel<?> channel, Class<T> clazz)
            throws ClassCastException {
        if (!clazz.isAssignableFrom(channel.getChannelType())) {
            throw new ClassCastException(channel.getChannelType().getSimpleName()
                    + " is not compatible with an animation channel of type " + clazz.getSimpleName());
        }
        return (AnimationChannel<T>) channel;
    }

    public interface VectorProvidingChannel<E extends Vec3d> extends AnimationChannel<Vec3d> {
        E read(double... values);
    }

    public interface RotationProvidingChannel<E extends Rotation> extends AnimationChannel<Rotation> {
        E read(double... values);
    }

    public interface ScalarProvidingChannel<E extends Number> extends AnimationChannel<Number> {
        E read(double... values);
    }

    public static class VectorChannel implements VectorProvidingChannel<Vec3d> {

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

    public static class DoubleChannel implements ScalarProvidingChannel<Double> {

        @Override
        public int numValues() {
            return 1;
        }

        @Override
        public Double read(double... values) throws IndexOutOfBoundsException {
            return values[0];
        }

        @Override
        public double[] write(Number value) {
            return new double[] { value.doubleValue() };
        }

        @Override
        public Class<? extends Double> getChannelType() {
            return Double.class;
        }
        
    }

    public static class EulerChannel implements RotationProvidingChannel<Rotation> {

        @Override
        public int numValues() {
            return 3;
        }

        @Override
        public Rotation read(double... values) throws IndexOutOfBoundsException {
            return Rotation.of((float) values[0], (float) values[1], (float) values[2]);
        }

        @Override
        public Class<? extends Rotation> getChannelType() {
            return Rotation.class;
        }

        @Override
        public double[] write(Rotation value) {
            return new double[] { value.pitch(), value.yaw(), value.roll() };
        }
        
    }

    public static class QuaternionChannel implements RotationProvidingChannel<Rotation> {

        @Override
        public int numValues() {
            return 4;
        }

        @Override
        public Rotation read(double... values) throws IndexOutOfBoundsException {
            return Rotation.of((float) values[0], (float) values[1], (float) values[2], (float) values[3]);
        }

        @Override
        public Class<? extends Rotation> getChannelType() {
            return Rotation.class;
        }

        @Override
        public double[] write(Rotation value) {
            Quaternionfc quat = value.getQuaternion();
            return new double[] { quat.w(), quat.x(), quat.y(), quat.z() };
        }
    }
}
