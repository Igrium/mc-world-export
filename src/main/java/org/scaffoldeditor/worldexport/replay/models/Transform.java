package org.scaffoldeditor.worldexport.replay.models;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Represents a transform within a pose (of a bone, for example).
 */
public class Transform {
    public final Vector3dc translation;
    public final Quaterniondc rotation;
    public final Vector3dc scale;

    /**
     * If this transform was created with a matrix, store the original matrix to
     * prevent rounding errors.
     */
    private Matrix4d matrix = new Matrix4d();

    public static final Transform NEUTRAL = new Transform(new Vector3d(), new Quaterniond(), new Vector3d(1d));

    public Transform(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;

        this.matrix.scale(scale);
        this.matrix.rotate(rotation);
        this.matrix.translate(translation);
    }

    public Transform(Matrix4dc matrix) {
        this.translation = matrix.getTranslation(new Vector3d());
        this.rotation = matrix.getUnnormalizedRotation(new Quaterniond());
        this.scale = matrix.getScale(new Vector3d());

        this.matrix.set(matrix);
    }

    /**
     * Get a string representation of this bone transform, as defined by the Replay
     * file specification (for use in the Entity XML.)
     * 
     * @param useTranslation Whether to include translation in the string.
     * @param useScale       Whether to include scale in the string. Can only be
     *                       if <code>useTranslation</code> is true!
     * @throws IllegalArgumentException If you attempt to include scale without
     *                                  including translation.
     * @return Stringified object.
     */
    public String toString(boolean useTranslation, boolean useScale) {
        if (useScale && !useTranslation) {
            throw new IllegalArgumentException("Translation MUST be written in order for scale to be written!");
        }

        List<String> strings = new ArrayList<>();

        strings.add(String.valueOf(rotation.w()));
        strings.add(String.valueOf(rotation.x()));
        strings.add(String.valueOf(rotation.y()));
        strings.add(String.valueOf(rotation.z()));

        if (useTranslation) {
            strings.add(String.valueOf(translation.x()));
            strings.add(String.valueOf(translation.y()));
            strings.add(String.valueOf(translation.z()));
        }
        if (useScale) {
            strings.add(String.valueOf(scale.x()));
            strings.add(String.valueOf(scale.y()));
            strings.add(String.valueOf(scale.z()));
        }

        return String.join(" ", strings) + ";";
    }

    /**
     * Get a string representation of this bone transform (including translation,
     * rotation, and scale), as defined by the Replay file specification (for use in
     * the Entity XML.)
     */
    @Override
    public String toString() {
        return toString(true, true);
    }

    /**
     * Obtain the inverse of this transformation.
     * 
     * @return A transformation that, when applied to an object with this
     *         transformation applied, will revert the object to its original
     *         position.
     */
    public Transform inverse() {
        Vector3d pos = translation.mul(-1, new Vector3d());
        Quaterniond rot = rotation.conjugate(new Quaterniond());
        Vector3d scale = new Vector3d(1 / this.scale.x(), 1 / this.scale.y(), 1 / this.scale.z());

        return new Transform(pos, rot, scale);
    }

    /**
     * Transform this transformation by another transformation.
     * 
     * @param other Transformation to transform by.
     * @return A transformation which holds the sum of these two transformations.
     */
    public Transform add(Transform other) {
        Vector3d pos = this.translation.add(other.translation, new Vector3d());
        Quaterniond rot = this.rotation.mul(other.rotation, new Quaterniond());
        Vector3d scale = this.scale.mul(other.scale, new Vector3d());

        return new Transform(pos, rot, scale);
    }

    /**
     * Get this transform as a transformation matrix.
     * @param dest Destination matrix.
     * @return <code>dest</code>
     */
    public Matrix4d toMatrix(Matrix4d dest) {
        dest.set(this.matrix);
        return dest;
    }
}