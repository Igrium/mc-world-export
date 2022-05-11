package org.scaffoldeditor.worldexport.replay.models;

import java.util.HashMap;
import java.util.Map;

import org.scaffoldeditor.worldexport.replay.models.OverrideChannel.OverrideChannelFrame;
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

        /**
         * A map of material overrides and their values for this frame.
         */
        public final Map<OverrideChannel, OverrideChannelFrame> overrideChannels = new HashMap<>();

        public void putMaterialOverride(OverrideChannel target, OverrideChannelFrame frame) {
            if (target.getMode() != frame.getMode()) {
                throw new IllegalArgumentException("Override target and frame must be of the same mode.");
            }
            overrideChannels.put(target, frame);
        }
    }

    /**
     * Get the bones of this model in definition order.
     * @return A view of this model's bones.
     */
    public Iterable<T> getBones();

    /**
     * Get the material overrides used by this model in definition order.
     * @return A view of this model's material overrides.
     */
    public Iterable<OverrideChannel> getOverrideChannels();

    public void addOverrideChannel(OverrideChannel channel);

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

    /**
     * Determine whether this model type allows the toggling of visibility of bones.
     * @return Can visibility be keyframed?
     */
    public default boolean allowVisibility() {
        return false;
    }
}
