package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A global abstraction for rotations of various types. Euler rotations use a Y -> X -> Z axis order.
 */
public interface Rotation {
    /**
     * The pitch in radians.
     */
    float pitch();

    /**
     * The yaw in radians.
     */
    float yaw();

    /**
     * The roll in radians.
     */
    float roll();

    // /**
    //  * This rotation as a quaternion.
    //  */
    // Quaternionfc quaternion();

    /**
     * Get this rotation as a quaternion and store it in the provided object.
     * @param dest Destination object.
     * @return <code>dest</code>
     */
    Quaternionf getQuaternion(Quaternionf dest);

    /**
     * Get this rotation as a quaternion.
     * @return The quaternion.
     */
    default Quaternionfc getQuaternion() {
        return getQuaternion(new Quaternionf());
    }

    /**
     * Get this rotation as euler angles and store it in the provided object.
     * @param dest Destination object.
     * @return <code>dest</code>
     */
    Vector3f getEuler(Vector3f dest);

    /**
     * Get this rotation as euler angles.
     * @return A vector where the XYZ values represent the pitch, yaw, and roll.
     */
    default Vector3fc getEuler() {
        return getEuler(new Vector3f());
    }

    /**
     * When given a choice, determine which representation will most accurately
     * represent this rotation.
     * 
     * @return <code>true</code> if euler; <code>false</code> if quaternion.
     */
    public boolean prefersEuler();

    /**
     * Create a rotation from YXZ euler angles.
     * @param euler A vector with the euler angles, in radians.
     * @return The rotation.
     */
    public static Rotation of(Vector3fc euler) {
        return new EulerRotation(euler);
    }

    /**
     * Create a rotation from YXZ euler angles.
     * @param pitch The pitch in radians.
     * @param yaw The yaw in radians.
     * @param roll The roll in radians.
     * @return The rotation.
     */
    public static Rotation of(float pitch, float yaw, float roll) {
        return new EulerRotation(pitch, yaw, roll);
    }

    /**
     * Create a rotation from a quaternion.
     * @param quaternion The quaternion to use.
     * @return The rotation.
     */
    public static Rotation of(Quaternionfc quaternion) {
        return new QuaternionRotation(quaternion);
    }

    /**
     * Create a rotation from a quaternion.
     * @param w W value.
     * @param x X value.
     * @param y Y value.
     * @param z Z value.
     * @return The rotation.
     */
    public static Rotation of(float w, float x, float y, float z) {
        return new QuaternionRotation(w, x, y, z);
    }
    

    static class EulerRotation implements Rotation {

        private final Vector3f euler;

        public EulerRotation(Vector3fc euler) {
            this.euler = new Vector3f(euler);
        }

        public EulerRotation(float pitch, float yaw, float roll) {
            this.euler = new Vector3f(pitch, yaw, roll);
        }

        @Override
        public float pitch() {
            return euler.x;
        }

        @Override
        public float yaw() {
            return euler.y;
        }

        @Override
        public float roll() {
            return euler.z;
        }

        @Override
        public Quaternionf getQuaternion(Quaternionf dest) {
            return dest.set(0, 0, 0, 0).rotateYXZ(euler.y, euler.x, euler.z);
        }

        @Override
        public Vector3f getEuler(Vector3f dest) {
            return dest.set(this.euler);
        }
        
        @Override
        public Vector3fc getEuler() {
            return euler;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Rotation val) {
                return this.euler.equals(val.getEuler());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.euler.hashCode();
        }

        @Override
        public boolean prefersEuler() {
            return true;
        }
    }

    static class QuaternionRotation implements Rotation {

        private final Quaternionf quaternion;
        private Vector3f euler;

        public QuaternionRotation(Quaternionfc quaternion) {
            this.quaternion = new Quaternionf(quaternion);
            calcEuler();
        }

        public QuaternionRotation(float w, float x, float y, float z) {
            this.quaternion = new Quaternionf(x, y, z, w);
            calcEuler();
        }

        private void calcEuler() {
            euler = quaternion.getEulerAnglesYXZ(new Vector3f());
        }

        @Override
        public float pitch() {
            return euler.x;
        }

        @Override
        public float yaw() {
            return euler.y;
        }

        @Override
        public float roll() {
            return euler.z;
        }

        @Override
        public Quaternionf getQuaternion(Quaternionf dest) {
            return dest.set(quaternion);
        }

        @Override
        public Quaternionfc getQuaternion() {
            return quaternion;
        }

        @Override
        public Vector3f getEuler(Vector3f dest) {
            return dest.set(euler);
        }

        @Override
        public Vector3fc getEuler() {
            return euler;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Rotation val) {
                return this.quaternion.equals(val.getQuaternion());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return quaternion.hashCode();
        }

        @Override
        public boolean prefersEuler() {
            return false;
        }
    }
}
