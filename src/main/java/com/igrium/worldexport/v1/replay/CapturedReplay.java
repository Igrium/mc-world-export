package com.igrium.worldexport.v1.replay;

import com.igrium.worldexport.v1.world.mesh.WorldMesh;
import lombok.Getter;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * in-memory representation of a replay file.
 */
public class CapturedReplay {

    @Getter
    private final Map<ChunkSectionPos, List<WorldMesh>> worldMeshes = new ConcurrentHashMap<>();


}
