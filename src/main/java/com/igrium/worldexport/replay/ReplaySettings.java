package com.igrium.worldexport.replay;

import com.igrium.worldexport.math.ChunkSectionBox;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.nio.file.Paths;

@Builder
@Getter
public class ReplaySettings {

    /**
     * Replay output file/folder.
     */
    @Builder.Default @NonNull
    private Path exportPath = FabricLoader.getInstance().getGameDir().resolve("ReplayTest");

    /**
     * If set, output the replay into a zip file instead of spitting it into a folder.
     */
    @Builder.Default
    private boolean exportZip = true;

    /**
     * The world-space bounds of the exported area.
     */
    @Builder.Default @NonNull
    private ChunkSectionBox bounds = ChunkSectionBox.ZERO;

    /**
     * An offset to apply to the export
     */
    @Builder.Default @NonNull
    private BlockPos offset = BlockPos.ORIGIN;

    /**
     * If true, blocks will be assigned OBJ groups based on their type.
     */
    private boolean splitBlocks;

    /**
     * The max number of meshing operations that may be run at once (to avoid worker starvation)
     */
    @Builder.Default
    private int maxThreads = Runtime.getRuntime().availableProcessors();

    /**
     * The number of game ticks in each export tick. Useful for exporting timelapses.
     */
    @Builder.Default
    private int tickStride = 1;

    /**
     * If set, all base meshes (meshes without updates) will be merged into one.
     */
    @Builder.Default
    private boolean mergeBaseMeshes = true;

    @Builder.Default
    private boolean mergeDoubleVertices = true;
}
