package org.scaffoldeditor.worldexport.mat;

import net.minecraft.util.Identifier;

public final class MaterialUtils {
    private MaterialUtils() {}

    /**
     * Get the filename of a texture, excluding the extension.
     * @param texture Texture identifier.
     * @return Filename, without extension.
     */
    public static String getTexName(Identifier texture) {
        String name = texture.toString().replace(':', '/');
        int index = name.lastIndexOf('.');
        if (index > 0) {
            return name.substring(0, index);
        } else {
            return name;
        }
    }
}
