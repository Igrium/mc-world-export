package org.scaffoldeditor.worldexport.replaymod.animation_serialization;

import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation.Euler;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation.QuaternionRot;

import net.minecraft.util.math.Quaternion;
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

    public static class EulerChannel implements RotationProvidingChannel<Rotation.Euler> {

        @Override
        public int numValues() {
            return 3;
        }

        @Override
        public Euler read(double... values) throws IndexOutOfBoundsException {
            return new Euler(values[0], values[1], values[2]);
        }

        @Override
        public Class<? extends Euler> getChannelType() {
            return Euler.class;
        }

        @Override
        public double[] write(Rotation value) {
            return new double[] { value.pitch(), value.yaw(), value.roll() };
        }
        
    }

    public static class QuaternionChannel implements RotationProvidingChannel<Rotation.QuaternionRot> {

        @Override
        public int numValues() {
            return 4;
        }

        @Override
        public QuaternionRot read(double... values) throws IndexOutOfBoundsException {
            return new QuaternionRot(values[0], values[1], values[2], values[3]);
        }

        @Override
        public Class<? extends QuaternionRot> getChannelType() {
            return QuaternionRot.class;
        }

        @Override
        public double[] write(Rotation value) {
            Quaternion quat = value.quaternion();
            return new double[] { quat.getW(), quat.getX(), quat.getY(), quat.getZ() };
        }
    }
}
