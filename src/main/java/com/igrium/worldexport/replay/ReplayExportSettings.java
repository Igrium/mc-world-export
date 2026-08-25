package com.igrium.worldexport.replay;

import com.igrium.worldexport.math.ChunkSectionBox;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@Builder
@Getter
public class ReplayExportSettings {

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
    private ChunkSectionBox worldBounds = ChunkSectionBox.ZERO;

    /**
     * The bounding box in which block updates are processed.
     */
    @Nullable @Getter
    private ChunkSectionBox updateBounds;

    /**
     * Get the bounding box that block updates should be culled to.
     * @return <code>updateBounds</code> if not null, otherwise <code>bounds</code>
     */
    public @NonNull ChunkSectionBox updateBounds() {
        return updateBounds != null ? updateBounds : worldBounds;
    }

    /**
     * An optional, additional bounding box to use to cull entity exports.
     */
    @Nullable @Getter
    private AABB entityBounds;

    /**
     * Get the bounding box that entities should be culled to.
     * @return <code>entityBounds</code> if not null, otherwise <code>bounds.toBox()</code>
     */
    public @NonNull AABB entityBounds() {
        return entityBounds != null ? entityBounds : worldBounds.toBox();
    }

    /**
     * Export the block world as a mesh
     */
    @Builder.Default
    private boolean exportWorld = true;

    /**
     * Export block updates
     * @apiNote Depends on {@link #exportWorld}
     */
    @Builder.Default
    private boolean exportUpdates = true;

    /**
     * Export animated textures (fire, etc.)
     */
    @Builder.Default
    private boolean exportSpritesheets = true;

    /**
     * Export entity animations
     */
    @Builder.Default
    private boolean exportEntities = true;

    /**
     * An offset to apply to the export
     */
    @Builder.Default @NonNull
    private BlockPos offset = BlockPos.ZERO;

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
