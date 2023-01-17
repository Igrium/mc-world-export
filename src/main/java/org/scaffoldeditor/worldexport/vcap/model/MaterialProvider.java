package org.scaffoldeditor.worldexport.vcap.model;

import java.util.function.BiConsumer;

import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;

/**
 * A "prototype" material that will be generated at a later time during export.
 */
public interface MaterialProvider { 

    /**
     * Generate this material.
     * 
     * @param textureConsumer A consumer for replay textures should this material
     *                        have any special textures it needs to create.
     * @return The generated material.
     */
    Material writeMaterial(BiConsumer<String, ReplayTexture> textureConsumer);

    public static MaterialProvider of(Material material) {
        return textures -> material;
    }
}
