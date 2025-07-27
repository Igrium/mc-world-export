package com.igrium.worldexport.replay;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.ReplayTexture;
import com.igrium.worldexport.world.WorldMesh;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a serialized (but not-yet saved) replay file.
 */
@Getter
public class CompiledReplay {

    ;

    /**
     * All the meshes that belong to the world.
     */
    Map<String, WorldMesh> worldMeshes = new HashMap<>();

    /**
     * A map of texture names (relative to the replay root) and their texture data.
     */
    private final Map<String, ReplayTexture> textures = new HashMap<>();

    /**
     * A map of mtl file names (relative to the replay root) and the materials in them.
     */
    private final Map<String, List<ReplayMtl>> mtlLibs = new HashMap<>();

    private final Map<String, CapturedEntity> entities = new HashMap<>();


}
