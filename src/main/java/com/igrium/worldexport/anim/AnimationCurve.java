package com.igrium.worldexport.anim;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatIterator;
import it.unimi.dsi.fastutil.floats.FloatList;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.io.*;

/**
 * Represents a 3D point transformation that is animated.
 */
public class AnimationCurve {

    public static final int NUM_CHANNELS = 10;

    /**
     * The formatting of this curve. Determines the number of channels and what data can be stored.
     */
    @Getter
    public enum CurveFormat {
        /**
         * The curve only stores position data.
         */
        POS(3),

        /**
         * The curve stores position and rotation (quaternion) data, but no scale.
         */
        POS_ROT(7),

        /**
         * The curve stores position, rotation, and scale data.
         */
        POS_ROT_SCALE(10);

        private final int numChannels;

        CurveFormat(int numChannels) {
            this.numChannels = numChannels;
        }

        public boolean hasRotation() {
            return this == POS_ROT || this == POS_ROT_SCALE;
        }

        public boolean hasScale() {
            return this == POS_ROT_SCALE;
        }
    }


    // Important implementation detail: each in-use list must always be the same length.
    private final FloatList xPosChannel = new FloatArrayList();
    private final FloatList yPosChannel = new FloatArrayList();
    private final FloatList zPosChannel = new FloatArrayList();

    private final FloatList wRotChannel = new FloatArrayList();
    private final FloatList xRotChannel = new FloatArrayList();
    private final FloatList yRotChannel = new FloatArrayList();
    private final FloatList zRotChannel = new FloatArrayList();

    private final FloatList xScaleChannel = new FloatArrayList();
    private final FloatList yScaleChannel = new FloatArrayList();
    private final FloatList zScaleChannel = new FloatArrayList();

    /**
     * Get a channel by its index.
     * @param index Channel index.
     * @return A snapshot of the channel's data.
     * @throws IndexOutOfBoundsException If the supplied index is not valid for the curve's format.
     */
    public final float[] getChannel(int index) throws IndexOutOfBoundsException {
        if (index >= numChannels()) {
            throw new IndexOutOfBoundsException(illegalChannelIndex(index, getFormat()));
        }
        return switch (index) {
            case 0 -> getXPosChannel();
            case 1 -> getYPosChannel();
            case 2 -> getZPosChannel();
            case 3 -> getWRotChannel();
            case 4 -> getXRotChannel();
            case 5 -> getYRotChannel();
            case 6 -> getZRotChannel();
            case 7 -> getXScaleChannel();
            case 8 -> getYScaleChannel();
            case 9 -> getZScaleChannel();
            default -> throw new IndexOutOfBoundsException(illegalChannelIndex(index, getFormat()));
        };
    }

    private static String illegalChannelIndex(int index, CurveFormat format) {
        return "Illegal channel index (" + index + ") for curve format " + format;
    }

    public final float[] getXPosChannel() {
        return xPosChannel.toFloatArray();
    }

    public final float[] getYPosChannel() {
        return yPosChannel.toFloatArray();
    }

    public final float[] getZPosChannel() {
        return zPosChannel.toFloatArray();
    }

    public final float[] getWRotChannel() {
        return wRotChannel.toFloatArray();
    }

    public final float[] getXRotChannel() {
        return xRotChannel.toFloatArray();
    }

    public final float[] getYRotChannel() {
        return yRotChannel.toFloatArray();
    }

    public final float[] getZRotChannel() {
        return zRotChannel.toFloatArray();
    }

    public final float[] getXScaleChannel() {
        return xScaleChannel.toFloatArray();
    }

    public final float[] getYScaleChannel() {
        return yScaleChannel.toFloatArray();
    }

    public final float[] getZScaleChannel() {
        return zScaleChannel.toFloatArray();
    }

    @Getter
    private CurveFormat format = CurveFormat.POS_ROT_SCALE;

    public void setFormat(@NonNull CurveFormat format) {
        if (!isEmpty()) {
            throw new IllegalStateException("Curve format may not be modified after data has been added.");
        }
        this.format = format;
    }

    @Getter @Setter
    private int frameOffset = 0;

    /**
     * The number of frames in this animation.
     */
    public int size() {
        return xPosChannel.size();
    }

    public boolean isEmpty() {
        return xPosChannel.isEmpty();
    }

    public int numChannels() {
        return format.getNumChannels();
    }

    public boolean hasRotation() {
        return format.hasRotation();
    }

    public boolean hasScale() {
        return format.hasScale();
    }

    public void appendFrom(AnimationCurve other) {
        if (other.isEmpty())
            return;

        xPosChannel.addAll(other.xPosChannel);
        yPosChannel.addAll(other.yPosChannel);
        zPosChannel.addAll(other.zPosChannel);

        if (hasRotation()) {
            if (other.hasRotation()) {
                wRotChannel.addAll(other.wRotChannel);
                xRotChannel.addAll(other.xRotChannel);
                yRotChannel.addAll(other.yRotChannel);
                zRotChannel.addAll(other.zRotChannel);
            } else {
                pad(wRotChannel, other.size());
                pad(xRotChannel, other.size());
                pad(yRotChannel, other.size());
                pad(zRotChannel, other.size());
            }
        }

        if (hasScale()) {
            if (other.hasScale()) {
                xScaleChannel.addAll(other.xScaleChannel);
                yScaleChannel.addAll(other.yScaleChannel);
                zScaleChannel.addAll(other.zScaleChannel);
            } else {
                pad(xScaleChannel, other.size());
                pad(yScaleChannel, other.size());
                pad(zScaleChannel, other.size());
            }
        }

    }

    private static void pad(FloatList list, int amount) {
        list.addElements(list.size(), new float[amount]);
    }

    /**
     * Append a frame to the end of this animation curve.
     *
     * @param pos   Position to add.
     * @param rot   Rotation to add (if applicable)
     * @param scale Scale to add (if applicable)
     * @apiNote If any of the required transforms are null, the value from the previous frame is copied.
     * If this is the first frame, the value is set to a default.
     */
    public void addFrame(@Nullable Vector3fc pos, @Nullable Quaternionfc rot, @Nullable Vector3fc scale) {
        int lastFrame = size() - 1;
        if (pos == null) {
            pos = lastFrame >= 0 ? getPosition(lastFrame, new Vector3f()) : new Vector3f();
        }
        xPosChannel.add(pos.x());
        yPosChannel.add(pos.y());
        zPosChannel.add(pos.z());

        if (hasRotation()) {
            if (rot == null) {
                rot = lastFrame >= 0 ? getRotation(lastFrame, new Quaternionf()) : new Quaternionf();
            }
            wRotChannel.add(rot.w());
            xRotChannel.add(rot.x());
            yRotChannel.add(rot.y());
            zRotChannel.add(rot.z());
        }

        if (hasScale()) {
            if (scale == null) {
                scale = lastFrame >= 0 ? getScale(lastFrame, new Vector3f()) : new Vector3f(1f);
            }
            xScaleChannel.add(scale.x());
            yScaleChannel.add(scale.y());
            zScaleChannel.add(scale.z());
        }

    }

    public void addFrame(Matrix4fc transform) {
        addFrame(transform.getTranslation(new Vector3f()),
                hasRotation() ? transform.getNormalizedRotation(new Quaternionf()) : null,
                hasScale() ? transform.getScale(new Vector3f()) : null);
    }

    public void setFrame(int frame, @Nullable Vector3fc pos, @Nullable Quaternionfc rot, @Nullable Vector3fc scale)
            throws IndexOutOfBoundsException {
        if (pos != null) {
            setPosition(frame, pos);
        }
        if (hasRotation() && rot != null) {
            setRotation(frame, rot);
        }
        if (hasScale() && scale != null) {
            setScale(frame, scale);
        }
    }

    public void setFrame(int frame, Matrix4fc transform) {
        setFrame(frame,
                transform.getTranslation(new Vector3f()),
                hasRotation() ? transform.getNormalizedRotation(new Quaternionf()) : null,
                hasScale() ? transform.getScale(new Vector3f()) : null);
    }

    public Vector3f getPosition(int frame, Vector3f dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        dest.x = xPosChannel.getFloat(frame);
        dest.y = yPosChannel.getFloat(frame);
        dest.z = zPosChannel.getFloat(frame);
        return dest;
    }

    public void setPosition(int frame, Vector3fc position) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        xPosChannel.set(frame, position.x());
        yPosChannel.set(frame, position.y());
        zPosChannel.set(frame, position.z());
    }

    public Quaternionf getRotation(int frame, Quaternionf dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);
        assertHasRotation();

        dest.w = wRotChannel.getFloat(frame);
        dest.x = xRotChannel.getFloat(frame);
        dest.y = yRotChannel.getFloat(frame);
        dest.z = zRotChannel.getFloat(frame);
        return dest;
    }

    public void setRotation(int frame, Quaternionfc rotation) throws IndexOutOfBoundsException {
        assertInBounds(frame);
        assertHasRotation();

        wRotChannel.set(frame, rotation.w());
        xRotChannel.set(frame, rotation.x());
        yRotChannel.set(frame, rotation.y());
        zRotChannel.set(frame, rotation.z());
    }

    private void assertHasRotation() {
        if (!hasRotation()) {
            throw new IllegalStateException("This animation curve does not have rotation data.");
        }
    }

    public Vector3f getScale(int frame, Vector3f dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);
        assertHasScale();

        dest.x = xScaleChannel.getFloat(frame);
        dest.y = yScaleChannel.getFloat(frame);
        dest.z = zScaleChannel.getFloat(frame);
        return dest;
    }

    public void setScale(int frame, Vector3fc scale) throws IndexOutOfBoundsException {
        assertInBounds(frame);
        assertHasScale();

        xScaleChannel.set(frame, scale.x());
        yScaleChannel.set(frame, scale.y());
        zScaleChannel.set(frame, scale.z());
    }

    private void assertHasScale() {
        if (!hasScale()) {
            throw new IllegalStateException("This animation does not have scale data.");
        }
    }

    /**
     * Apply a frame's transformation onto a matrix.
     *
     * @param frame Frame to get the transform of.
     * @param dest  Matrix to apply transformation onto.
     * @return <code>dest</code>
     * @throws IndexOutOfBoundsException If the input frame is out of bounds for this animation.
     */
    public Matrix4f getTransform(int frame, Matrix4f dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        if (hasScale()) {
            float scaleX = xScaleChannel.getFloat(frame);
            float scaleY = yScaleChannel.getFloat(frame);
            float scaleZ = zScaleChannel.getFloat(frame);
            dest.scale(scaleX, scaleY, scaleZ);
        }

        // I don't like that we're allocating here, but whatever
        if (hasRotation()) {
            dest.rotate(getRotation(frame, new Quaternionf()));
        }

        float posX = xPosChannel.getFloat(frame);
        float posY = yPosChannel.getFloat(frame);
        float posZ = zPosChannel.getFloat(frame);

        dest.translate(posX, posY, posZ);

        return dest;
    }

    /**
     * Get an array of all channel indices that have data in them.
     */
    public int[] getChannelIndices() {
        int[] result = new int[numChannels()];
        for (int i = 0; i < result.length; i++) {
            result[i] = i;
        }
        return result;
    }

    private void assertInBounds(int frame) {
        if (frame >= size() || frame < 0) {
            throw new IndexOutOfBoundsException("Frame " + frame + " is out of bounds for animation of length " + size());
        }
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(getFormat().ordinal());
        out.writeInt(frameOffset);
        out.writeInt(size());

        writeChannel(xPosChannel, out);
        writeChannel(yPosChannel, out);
        writeChannel(zPosChannel, out);

        if (hasRotation()) {
            writeChannel(wRotChannel, out);
            writeChannel(xRotChannel, out);
            writeChannel(yRotChannel, out);
            writeChannel(zRotChannel, out);
        }

        if (hasScale()) {
            writeChannel(xScaleChannel, out);
            writeChannel(yScaleChannel, out);
            writeChannel(zScaleChannel, out);
        }
    }

    private static void writeChannel(FloatList curve, DataOutput out) throws IOException {
        FloatIterator iter = curve.iterator();
        while (iter.hasNext()) {
            out.writeFloat(iter.nextFloat());
        }
    }

    public void read(DataInput in) throws IOException {
        setFormat(CurveFormat.values()[in.readByte()]);
        frameOffset = in.readInt();
        int length = in.readInt();

        readChannel(xPosChannel, in, length);
        readChannel(yPosChannel, in, length);
        readChannel(zPosChannel, in, length);

        if (hasRotation()) {
            readChannel(wRotChannel, in, length);
            readChannel(xRotChannel, in, length);
            readChannel(yRotChannel, in, length);
            readChannel(zRotChannel, in, length);
        }

        if (hasScale()) {
            readChannel(xScaleChannel, in, length);
            readChannel(yScaleChannel, in, length);
            readChannel(zScaleChannel, in, length);
        }
    }

    private static void readChannel(FloatList curve, DataInput in, int length) throws IOException {
        // Create a separate buffer so we don't constantly grow the curve array unnecessarily.
        float[] buffer = new float[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readFloat();
        }
        curve.addElements(curve.size(), buffer);
    }

    public static String nameFromCurveIndex(int index) {
        return switch (index) {
            case 0 -> "Location X";
            case 1 -> "Location Y";
            case 2 -> "Location Z";
            case 3 -> "Rotation W";
            case 4 -> "Rotation X";
            case 5 -> "Rotation Y";
            case 6 -> "Rotation Z";
            case 7 -> "Scale X";
            case 8 -> "Scale Y";
            case 9 -> "Scale Z";
            default -> "[unknown]";
        };
    }
}
