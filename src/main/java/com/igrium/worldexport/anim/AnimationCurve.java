package com.igrium.worldexport.anim;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatIterator;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.floats.FloatLists;
import lombok.Getter;
import lombok.Setter;
import org.joml.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a 3D point transformation that is animated.
 */
public class AnimationCurve {

    public static final int NUM_CHANNELS = 10;

    // Important implementation detail: each list should always be the same length.
    private final FloatList xPosCurve = new FloatArrayList();
    private final FloatList yPosCurve = new FloatArrayList();
    private final FloatList zPosCurve = new FloatArrayList();

    private final FloatList wRotCurve = new FloatArrayList();
    private final FloatList xRotCurve = new FloatArrayList();
    private final FloatList yRotCurve = new FloatArrayList();
    private final FloatList zRotCurve = new FloatArrayList();

    private final FloatList xScaleCurve = new FloatArrayList();
    private final FloatList yScaleCurve = new FloatArrayList();
    private final FloatList zScaleCurve = new FloatArrayList();

    public final float[] getCurve(int index) {
        return switch (index) {
            case 0 -> getXPosCurve();
            case 1 -> getYPosCurve();
            case 2 -> getZPosCurve();
            case 3 -> getWRotCurve();
            case 4 -> getXRotCurve();
            case 5 -> getYRotCurve();
            case 6 -> getZRotCurve();
            case 7 -> getXScaleCurve();
            case 8 -> getYScaleCurve();
            case 9 -> getZScaleCurve();
            default -> throw new IndexOutOfBoundsException("Illegal channel index: " + index);
        };
    }

    public final float[] getXPosCurve() {
        return xPosCurve.toFloatArray();
    }

    public final float[] getYPosCurve() {
        return yPosCurve.toFloatArray();
    }

    public final float[] getZPosCurve() {
        return zPosCurve.toFloatArray();
    }

    public final float[] getWRotCurve() {
        return wRotCurve.toFloatArray();
    }

    public final float[] getXRotCurve() {
        return xRotCurve.toFloatArray();
    }

    public final float[] getYRotCurve() {
        return yRotCurve.toFloatArray();
    }

    public final float[] getZRotCurve() {
        return zRotCurve.toFloatArray();
    }

    public final float[] getXScaleCurve() {
        return xScaleCurve.toFloatArray();
    }

    public final float[] getYScaleCurve() {
        return yScaleCurve.toFloatArray();
    }

    public final float[] getZScaleCurve() {
        return zScaleCurve.toFloatArray();
    }

    @Getter @Setter
    private int frameOffset = 0;

    /**
     * The number of frames in this animation.
     */
    public int size() {
        return xPosCurve.size();
    }

    public boolean isEmpty() {
        return xPosCurve.isEmpty();
    }

    public void appendFrom(AnimationCurve other) {
        xPosCurve.addAll(other.xPosCurve);
        yPosCurve.addAll(other.yPosCurve);
        zPosCurve.addAll(other.zPosCurve);

        wRotCurve.addAll(other.wRotCurve);
        xRotCurve.addAll(other.xRotCurve);
        yRotCurve.addAll(other.yRotCurve);
        zRotCurve.addAll(other.zRotCurve);

        xScaleCurve.addAll(other.xScaleCurve);
        yScaleCurve.addAll(other.yScaleCurve);
        zScaleCurve.addAll(other.zScaleCurve);
    }

    public void addFrame(Vector3fc pos, Quaternionfc rot, Vector3fc scale) {
        xPosCurve.add(pos.x());
        yPosCurve.add(pos.y());
        zPosCurve.add(pos.z());

        wRotCurve.add(rot.w());
        xRotCurve.add(rot.x());
        yRotCurve.add(rot.y());
        zRotCurve.add(rot.z());

        xScaleCurve.add(scale.x());
        yScaleCurve.add(scale.y());
        zScaleCurve.add(scale.z());
    }

    public void addFrame(Matrix4fc transform) {
        addFrame(transform.getTranslation(new Vector3f()),
                transform.getNormalizedRotation(new Quaternionf()),
                transform.getScale(new Vector3f()));
    }

    public void setFrame(int frame, Vector3fc pos, Quaternionfc rot, Vector3fc scale) throws IndexOutOfBoundsException {
        setPosition(frame, pos);
        setRotation(frame, rot);
        setScale(frame, scale);
    }

    public void setFrame(int frame, Matrix4fc transform) {
        setFrame(frame,
                transform.getTranslation(new Vector3f()),
                transform.getNormalizedRotation(new Quaternionf()),
                transform.getScale(new Vector3f()));
    }

    public Vector3f getPosition(int frame, Vector3f dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        dest.x = xPosCurve.getFloat(frame);
        dest.y = yPosCurve.getFloat(frame);
        dest.z = zPosCurve.getFloat(frame);
        return dest;
    }

    public void setPosition(int frame, Vector3fc position) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        xPosCurve.set(frame, position.x());
        yPosCurve.set(frame, position.y());
        zPosCurve.set(frame, position.z());
    }

    public Quaternionf getRotation(int frame, Quaternionf dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        dest.w = wRotCurve.getFloat(frame);
        dest.x = xRotCurve.getFloat(frame);
        dest.y = yRotCurve.getFloat(frame);
        dest.z = zRotCurve.getFloat(frame);
        return dest;
    }

    public void setRotation(int frame, Quaternionfc rotation) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        wRotCurve.set(frame, rotation.w());
        xRotCurve.set(frame, rotation.x());
        yRotCurve.set(frame, rotation.y());
        zRotCurve.set(frame, rotation.z());
    }

    public Vector3f getScale(int frame, Vector3f dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        dest.x = xScaleCurve.getFloat(frame);
        dest.y = yScaleCurve.getFloat(frame);
        dest.z = zScaleCurve.getFloat(frame);
        return dest;
    }

    public void setScale(int frame, Vector3fc scale) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        xScaleCurve.set(frame, scale.x());
        yScaleCurve.set(frame, scale.y());
        zScaleCurve.set(frame, scale.z());
    }

    public Matrix4f getTransform(int frame, Matrix4f dest) throws IndexOutOfBoundsException {
        assertInBounds(frame);

        float scaleX = xScaleCurve.getFloat(frame);
        float scaleY = yScaleCurve.getFloat(frame);
        float scaleZ = zScaleCurve.getFloat(frame);

        dest.scale(scaleX, scaleY, scaleZ);

        // I don't like that we're allocating here, but whatever
        dest.rotate(getRotation(frame, new Quaternionf()));

        float posX = xPosCurve.getFloat(frame);
        float posY = yPosCurve.getFloat(frame);
        float posZ = zPosCurve.getFloat(frame);

        dest.translate(posX, posY, posZ);

        return dest;
    }

    /**
     * Get an array of all channel indices that have data in them.
     */
    public int[] getChannelIndices() {
        int numChannels = NUM_CHANNELS;
        int[] result = new int[numChannels];
        for (int i = 0; i < numChannels; i++) {
            result[i] = i;
        }
        return result;
    }

    private void assertInBounds(int frame) {
        if (frame > size()) {
            throw new IndexOutOfBoundsException("Frame " + frame + " is out of bounds for animation of length " + size());
        }
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(frameOffset);
        out.writeInt(size());

        writeCurve(xPosCurve, out);
        writeCurve(yPosCurve, out);
        writeCurve(zPosCurve, out);

        writeCurve(wRotCurve, out);
        writeCurve(xRotCurve, out);
        writeCurve(yRotCurve, out);
        writeCurve(zRotCurve, out);

        writeCurve(xScaleCurve, out);
        writeCurve(yScaleCurve, out);
        writeCurve(zScaleCurve, out);
    }

    private static void writeCurve(FloatList curve, DataOutputStream out) throws IOException {
        FloatIterator iter = curve.iterator();
        while (iter.hasNext()) {
            out.writeFloat(iter.nextFloat());
        }
    }

    public void read(DataInputStream in) throws IOException {
        frameOffset = in.readInt();
        int length = in.readInt();

        readCurve(xPosCurve, in, length);
        readCurve(yPosCurve, in, length);
        readCurve(zPosCurve, in, length);

        readCurve(wRotCurve, in, length);
        readCurve(xRotCurve, in, length);
        readCurve(yRotCurve, in, length);
        readCurve(zRotCurve, in, length);

        readCurve(xScaleCurve, in, length);
        readCurve(yScaleCurve, in, length);
        readCurve(zScaleCurve, in, length);
    }

    private static void readCurve(FloatList curve, DataInputStream in, int length) throws IOException {
        // Create a separate buffer so we don't constantly grow the curve array unnecessarily.
        float[] buffer = new float[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readFloat();
        }
        curve.addAll(FloatList.of(buffer));
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
