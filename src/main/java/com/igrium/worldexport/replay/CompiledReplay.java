package com.igrium.worldexport.replay;

import com.google.gson.annotations.JsonAdapter;
import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.ReplayTexture;
import com.igrium.worldexport.mesh.WorldMesh;
import com.igrium.worldexport.util.JsonAdapters;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a serialized (but not-yet saved) replay file.
 */
@Getter
public class CompiledReplay {

    @Setter
    private @NonNull ReplayMeta meta = new ReplayMeta();

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

    @Getter @Setter
    public static class ReplayMeta {
        /**
         * The version string
         */
        private @NonNull String version = IgriumsReplayExporter.REPLAY_VERSION;

        /**
         * The global block pos of the export origin
         */
        @JsonAdapter(JsonAdapters.BlockPosAdapter.class)
        private @NonNull BlockPos origin = BlockPos.ZERO;
    }
}
