package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel;

import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;

/**
 * A global abstraction for rotations of various types. Euler rotations use a Y -> X -> Z axis order.
 */
public interface Rotation {
    /**
     * The pitch in radians.
     */
    double pitch();

    /**
     * The yaw in radians.
     */
    double yaw();

    /**
     * The roll in radians.
     */
    double roll();

    /**
     * This rotation as a quaternion.
     */
    Quaternion quaternion();

    public static class Euler implements Rotation {

        private final double pitch;
        private final double yaw;
        private final double roll;

        private Quaternion meAsQuat;

        public Euler(double pitch, double yaw, double roll) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;

            meAsQuat = Quaternion.fromEulerYxz((float) pitch(), (float) yaw(), (float) roll());
        }

        @Override
        public double pitch() {
            return pitch;
        }

        @Override
        public double yaw() {
            return yaw;
        }

        @Override
        public double roll() {
            return roll;
        }

        @Override
        public Quaternion quaternion() {
            return meAsQuat;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Euler)) return false;
            Euler rot = (Euler) obj;

            return (
                pitch == rot.pitch()
                && yaw == rot.yaw()
                && roll == rot.roll()
            );
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash *= 31 + Float.floatToIntBits((float) pitch);
            hash *= 31 + Float.floatToIntBits((float) yaw);
            hash *= 31 + Float.floatToIntBits((float) roll);
            return hash;
        }
        
    }

    public static class QuaternionRot implements Rotation {
        private final Quaternion value;
        
        private Vec3f euler;

        public QuaternionRot(Quaternion value) {
            this.value = value;
            euler = value.toEulerYxz();
        }

        public QuaternionRot(double w, double x, double y, double z) {
            this(new Quaternion((float) x, (float) y, (float) z, (float) w));
        }

        @Override
        public double pitch() {
            return euler.getX();
        }

        @Override
        public double yaw() {
            return euler.getY();
        }

        @Override
        public double roll() {
            return euler.getZ();
        }

        @Override
        public Quaternion quaternion() {
            return value;
        }
    }

    public static abstract class RotationChannel<T extends Rotation> implements AnimationChannel<T> {
        public abstract double[] write(Rotation value);
    }

    public static class EulerChannel extends RotationChannel<Euler> {

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

    public static class QuaternionChannel extends RotationChannel<QuaternionRot> {
        
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
