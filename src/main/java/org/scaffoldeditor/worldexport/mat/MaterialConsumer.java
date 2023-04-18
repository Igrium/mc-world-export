package org.scaffoldeditor.worldexport.mat;

import java.util.HashMap;
import java.util.Map;

public interface MaterialConsumer {

    /**
     * Add a material to this consumer, only if one by this name doesn't already exist.
     * @param name Material name (to be used in meshes).
     * @param mat Material.
     */
    default void addMaterial(String name, Material mat) {
        if (!hasMaterial(name)) putMaterial(name, mat);
    }

    /**
     * Add a texture to this consumer, only if one by this name doesn't already exist.
     * @param name Texture name (to be used in materials).
     * @param texture Texture.
     */
    default void addTexture(String name, ReplayTexture texture) {
        if (!hasTexture(name)) putTexture(name, texture);
    }

    /**
     * Determine if this consumer already has a material by the given name.
     * @param name Material name.
     * @return If that material exists.
     */
    default boolean hasMaterial(String name) {
        return getMaterial(name) != null;
    }

    /**
     * Determine if this consumer already has a texture by the given name.
     * @param name Texture name.
     * @return If that texture exists.
     */
    default boolean hasTexture(String name) {
        return getTexture(name) != null;
    }

    /**
     * Add a material to this consumer. Overrides if it already exists.
     * @param name Material name (to be used in meshes).
     * @param mat Material
     */
    void putMaterial(String name, Material mat);

    /**
     * Get the material of a given name from this consumer.
     * @param name Name to search for.
     * @return The material, or <code>null</code> if it doesn't exist.
     */
    Material getMaterial(String name);

    /**
     * Add a texture to this consumer. Overrides if it already exists.
     * @param name Name of the texture (to be used in materials).
     * @param texture Texture
     */
    void putTexture(String name, ReplayTexture texture);

    /**
     * Get the texture of a given name from this consumer.
     * @param name Name to search for.
     * @return The material, or <code>null</code> if it doesn't exist.
     */
    ReplayTexture getTexture(String name);
    
    public static class MaterialCache implements MaterialConsumer {

        public final Map<String, Material> materials = new HashMap<>();
        public final Map<String, ReplayTexture> textures = new HashMap<>();

        @Override
        public void putMaterial(String name, Material mat) {
            materials.put(name, mat);
        }

        @Override
        public Material getMaterial(String name) {
            return materials.get(name);
        }

        @Override
        public void putTexture(String name, ReplayTexture texture) {
            textures.put(name, texture);
        }

        @Override
        public ReplayTexture getTexture(String name) {
            return textures.get(name);
        }

        public void dump(MaterialConsumer target) {
            for (String name : materials.keySet()) {
                target.addMaterial(name, materials.get(name));
            }

            for (String name : textures.keySet()) {
                target.addTexture(name, textures.get(name));
            }
        }
        
    }

    /**
     * A replay texture associated with a name.
     */
    public static record NamedReplayTexture(String name, ReplayTexture texture) {};
}
