package com.igrium.worldexport.replay;

import com.igrium.worldexport.math.ChunkSectionBox;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.util.math.BlockPos;

@Builder
@Getter
public class ReplaySettings {

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
}
