package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import java.util.AbstractList;

import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule.CameraPathFrame;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public abstract class AbstractCameraAnimation extends AbstractList<CameraPathFrame> {

    protected int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * Get the FPS of this animation.
     * @return Frames per second.
     */
    public abstract double getFps();

    /**
     * Get the number of frames in this animation.
     */
    public abstract int size();

    /**
     * Get the length of this animation.
     * @return The length in seconds.
     */
    public double length() {
        return size() * getFps();
    }

    /**
     * Get the frame at a given position.
     */
    public CameraPathFrame get(int index) {
        return new CameraPathFrame(getPosition(index), getRotation(index), getFov(index));
    }

    /**
     * Get the position of the camera at a given frame.
     * @param index Frame number.
     * @return The position.
     */
    public abstract Vec3d getPosition(int index);

    /**
     * Get the position of the camera at a given frame.
     * @param index Frame number.
     * @return The position.
     */
    public abstract Vec3d getRotation(int index);

    /**
     * Get the FOV of the camera at a given frame.
     * @param index Frame number.
     * @return The FOV.
     */
    public abstract float getFov(int index);

    /**
     * Calculate the camera transform a given time.
     * @param time The time in seconds.
     * @return The transform.
     */
    public CameraPathFrame getTime(double time) {
        return new CameraPathFrame(getPositionAt(time), getRotationAt(time), getFovAt(time));
    }

    /**
     * Calculate the position of the camera at a given time.
     * @param time The time in seconds.
     * @return The position.
     */
    public Vec3d getPositionAt(double time) {
        int frame = getFrameNumber(time);
        if (frame < 0) {
            return getPositionAt(0);
        } else if (frame >= size()) {
            return getPositionAt(size() - 1);
        }

        if (frame + 1 < size()) {
            double delta = getFrameDelta(time);
            Vec3d prev = getPosition(frame);
            Vec3d next = getPosition(frame + 1);

            return new Vec3d(
                    MathHelper.lerp(delta, prev.x, next.y),
                    MathHelper.lerp(delta, prev.y, next.y),
                    MathHelper.lerp(delta, prev.z, next.z));
        } else {
            return getPosition(frame);
        }
    }

    /**
     * Calculate the rotation of the camera at a given time.
     * @param time The time in seconds.
     * @return The rotation.
     */
    public Vec3d getRotationAt(double time) {
        int frame = getFrameNumber(time);
        if (frame < 0) {
            return getRotationAt(0);
        } else if (frame >= size()) {
            return getRotationAt(size() - 1);
        }

        if (frame + 1 < size()) {
            double delta = getFrameDelta(time);
            Vec3d prev = getRotation(frame);
            Vec3d next = getRotation(frame + 1);

            return new Vec3d(
                    MathHelper.lerp(delta, prev.x, next.y),
                    MathHelper.lerp(delta, prev.y, next.y),
                    MathHelper.lerp(delta, prev.z, next.z));
        } else {
            return getRotation(frame);
        }
    }

    /**
     * Calculate the FOV of the camera at a given time.
     * @param time The time in seconds.
     * @return The FOV.
     */
    public float getFovAt(double time) {
        int frame = getFrameNumber(time);
        if (frame < 0) {
            return getFov(0);
        } else if (frame >= size()) {
            return getFov(size() - 1);
        }

        if (frame + 1 < size()) {
            double delta = getFrameDelta(time);
            float prev = getFov(frame);
            float next = getFov(frame + 1);

            return (float) MathHelper.lerp(delta, prev, next);
        } else {
            return getFov(frame);
        }
    }

    public int getFrameNumber(double time) {
        return (int) Math.floor(time / getFps());
    }

    public double getFrameDelta(double time) {
        double fps = getFps();
        double framePrecise = time / fps;
        double prev = Math.floor(time / fps);
        double next = Math.ceil(time / fps);
        
        return (framePrecise - prev) / (next - prev);
    }
}
