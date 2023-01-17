package org.scaffoldeditor.worldexport.vcap.model;

import java.util.Map;
import java.util.Optional;

import de.javagl.obj.ReadableObj;
import net.minecraft.block.BlockState;

/**
 * A "prototype" model that will be generated when the vcap is finalized.
 */
public interface ModelProvider {

    /**
     * Metadata that's created when a mesh is produced.
     */
    public static record ModelInfo(ReadableObj mesh, int numLayers, Map<String, MaterialProvider> materials) {}

    ModelInfo writeMesh();

    /**
     * Get the blockstate that was used to create this model.
     * @return The (optional) blockstate.
     */
    default Optional<BlockState> getBlockstate() {
        return Optional.empty();
    }

    public static ModelProvider of(ModelInfo model) {
        return () -> model;
    }

}
