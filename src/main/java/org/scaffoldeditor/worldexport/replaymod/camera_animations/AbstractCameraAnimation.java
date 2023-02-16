package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import java.util.AbstractList;

import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule.CameraPathFrame;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public abstract class AbstractCameraAnimation extends AbstractList<CameraPathFrame> {

    protected int id;
    protected Vec3d offset = new Vec3d(0, 0, 0);
    protected String name = "[unnamed]";
    protected double startTime = 0;


    // protected Formatting color = Formatting.WHITE;
    protected ReadableColor color = Colors.WHITE;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Vec3d getOffset() {
        return offset;
    }

    public void setOffset(Vec3d offset) {
        this.offset = offset;
    }

    public ReadableColor getColor() {
        return color;
    }

    public void setColor(ReadableColor color) {
        this.color = color;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
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
        return size() / getFps();
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
    public abstract Rotation getRotation(int index);

    /**
     * Get the FOV of the camera at a given frame.
     * @param index Frame number.
     * @return The FOV.
     */
    public abstract double getFov(int index);

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
            return getPosition(0);
        } else if (frame >= size()) {
            return getPosition(size() - 1);
        }

        if (frame + 1 < size()) {
            double delta = getFrameDelta(time);
            Vec3d prev = getPosition(frame);
            Vec3d next = getPosition(frame + 1);

            return new Vec3d(
                    MathHelper.lerp(delta, prev.x, next.x),
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
    public Rotation getRotationAt(double time) {
        int frame = getFrameNumber(time);
        if (frame < 0) {
            return getRotation(0);
        } else if (frame >= size()) {
            return getRotation(size() - 1);
        }

        if (frame + 1 < size()) {
            double delta = getFrameDelta(time);
            Rotation prev = getRotation(frame);
            Rotation next = getRotation(frame + 1);

            return Rotation.of(
                    (float) MathHelper.lerp(delta, prev.pitch(), next.pitch()),
                    (float) MathHelper.lerp(delta, prev.yaw(), next.yaw()),
                    (float) MathHelper.lerp(delta, prev.roll(), next.roll()));
        } else {
            return getRotation(frame);
        }
    }

    /**
     * Calculate the FOV of the camera at a given time.
     * @param time The time in seconds.
     * @return The FOV.
     */
    public double getFovAt(double time) {
        int frame = getFrameNumber(time);
        if (frame < 0) {
            return getFov(0);
        } else if (frame >= size()) {
            return getFov(size() - 1);
        }

        if (frame + 1 < size()) {
            double delta = getFrameDelta(time);
            double prev = getFov(frame);
            double next = getFov(frame + 1);

            return MathHelper.lerp(delta, prev, next);
        } else {
            return getFov(frame);
        }
    }

    public int getFrameNumber(double time) {
        return (int) Math.floor(time * getFps());
    }

    public double getFrameDelta(double time) {
        double fps = getFps();
        double framePrecise = time * fps;
        double prev = Math.floor(time * fps);
        double next = Math.ceil(time * fps);

        if ((next - prev) == 0) return 0;
        return (framePrecise - prev) / (next - prev);
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
