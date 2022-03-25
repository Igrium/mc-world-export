package org.scaffoldeditor.worldexport.replay.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * <p>
 * Unlike block models, Minecraft does not have a universal system for entity
 * models. Instead, each entity writes it's vertex data directly into GPU
 * buffers each frame with no abstraction, meaning there is no viable way to
 * dynamically generate entity meshes.
 * </p>
 * <p>
 * This is an intermediary class which contains entity meshes that can be
 * exported. Generators must be written on a per-class basis.
 * </p>
 * 
 * @param <T> The bone data structure this model will use.
 */
public interface ReplayModel<T> {

    /**
     * Represents a transform within a pose (of a bone, for example).
     */
    public static class Transform {
        public final Vector3dc translation;
        public final Quaterniondc rotation;
        public final Vector3dc scale;

        public static final Transform NEUTRAL = new Transform(new Vector3d(), new Quaterniond(), new Vector3d(1d));
        
        public Transform(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
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

            return String.join(" ", strings)+";";
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
         * @param other Transformation to transform by.
         * @return A transformation which holds the sum of these two transformations.
         */
        public Transform add(Transform other) {
            Vector3d pos = this.translation.add(other.translation, new Vector3d());
            Quaterniond rot = this.rotation.mul(other.rotation, new Quaterniond());
            Vector3d scale = this.scale.mul(other.scale, new Vector3d());

            return new Transform(pos, rot, scale);
        }
    }
    
    /**
     * Represents a single-frame pose of a model.
     * @param <T> The data structure used to represent bones.
     */
    public static class Pose<T> {
        /**
         * The root transform of the entity (in world space)
         */
        public Transform root = Transform.NEUTRAL;
        /**
         * A map of bones and their transforms. The coordinate space these transforms
         * are in depends on the model.
         */
        public final Map<T, Transform> bones = new HashMap<>();
    }

    /**
     * Get the bones of this model in definition order.
     * @return A view of this model's bones.
     */
    public Iterable<T> getBones();

    /**
     * Convert a transform into a coordinate space suitable for this armature type.
     * 
     * @param T  The bone this transform belongs to.
     * @param in Transform relative to entity root.
     * @return Transform in the target coordinate space.
     */
    public Transform processCoordinateSpace(T bone, Transform in);

    /**
     * Save this model into XML.
     * @param dom Document to write into.
     * @return The <code>&lt;model&gt;</code> element of the entity file.
     */
    public Element serialize(Document dom);
}
