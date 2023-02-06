package org.scaffoldeditor.worldexport.vcap.model;

import java.util.function.BiConsumer;

import org.scaffoldeditor.worldexport.mat.AnimatedReplayTexture;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;

import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

public record SpriteMaterialProvider(Sprite sprite, boolean transparent, boolean tinted, boolean emissive) implements MaterialProvider {

    @Override
    public Material writeMaterial(BiConsumer<String, ReplayTexture> textureConsumer) {
        String texName = getName();
        Material material = new Material();
        
        material.setColor(texName);
        material.setRoughness(1);
        material.setTransparent(transparent);

        if (tinted) {
            material.addOverride("color2", Material.DEFAULT_OVERRIDES.VERTEX_COLOR);
        }
        
        if (emissive) {
            material.setEmission(texName);
            material.setEmissionStrength(2);
        }
        
        textureConsumer.accept(texName, new AnimatedReplayTexture(sprite));
        
        return material;
    }
    
    public String getName() {
        return getTexName(sprite().getContents().getId());
    }

    /**
     * Get the filename of a texture, excluding the extension.
     * @param texture Texture identifier.
     * @return Filename, without extension. Compatible with material fields.
     */
    private String getTexName(Identifier texture) {
        String name = texture.toString().replace(':', '/');
        int index = name.lastIndexOf('.');
        if (index > 0) {
            return name.substring(0, index);
        } else {
            return name;
        }
    }
    
}
