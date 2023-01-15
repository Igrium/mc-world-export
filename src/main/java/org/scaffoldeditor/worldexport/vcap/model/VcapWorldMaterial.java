package org.scaffoldeditor.worldexport.vcap.model;

import java.util.function.BiConsumer;

import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;

/**
 * A "prototype" material for the world atlas that will be generated at a later
 * time during export.
 */
public record VcapWorldMaterial(boolean transparent, boolean tinted, boolean emissive) implements MaterialProvider {

    public Material writeMaterial(BiConsumer<String, ReplayTexture> textureConsumer) {
        Material material = new Material();
        material.setColor("world");
        material.setRoughness(1);

        material.setTransparent(transparent);

        if (tinted) {
            material.addOverride("color2", Material.DEFAULT_OVERRIDES.VERTEX_COLOR);
        }
        
        if (emissive) {
            material.setEmission("world");
            material.setEmissionStrength(2);
        }

        return material;
    }

    /**
     * Get the name this material will save with.
     * @return Material name.
     */
    public String getName() {
        StringBuilder builder = new StringBuilder("world");
        if (transparent) builder.append("_trans");
        if (tinted) builder.append("_tinted");
        if (emissive) builder.append("_emit");
        return builder.toString();
    }
}
