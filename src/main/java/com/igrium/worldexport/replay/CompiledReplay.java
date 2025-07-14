package com.igrium.worldexport.replay;

import com.igrium.worldexport.world.WorldMesh;
import de.javagl.obj.Mtl;
import lombok.Getter;
import net.minecraft.client.texture.NativeImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Getter
/**
 * Represents a serialized (but not-yet saved) replay file.
 */
public class CompiledReplay {
    /**
     * All the meshes that belong to the world.
     */
    // TODO: Should this be a map?
    private final List<WorldMesh> worldMeshes = new ArrayList<>();

    /**
     * A map of texture names (relative to the replay root) and their texture data.
     */
    private final Map<String, NativeImage> textures = new HashMap<>();

    /**
     * A map of mtl file names (relative to the replay root) and the materials in them.
     */
    private final Map<String, List<Mtl>> mtlLibs = new HashMap<>();
}
