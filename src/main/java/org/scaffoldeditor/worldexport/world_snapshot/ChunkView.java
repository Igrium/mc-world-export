package org.scaffoldeditor.worldexport.world_snapshot;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.level.ColorResolver;

/**
 * A block view that can report if a chunk is loaded.
 */
public interface ChunkView extends BlockRenderView {
    boolean isChunkLoaded(int x, int z);

    default boolean sectionExists(int x, int y, int z) {
        return isChunkLoaded(x, z);
    }

    /**
     * A wrapper around a WorldAccess that implements ChunkView
     */
    public static class WorldAccessWrapper implements ChunkView {
        public final WorldAccess base;

        public WorldAccessWrapper(WorldAccess base) {
            this.base = base;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos var1) {
            return base.getBlockEntity(var1);
        }

        @Override
        public BlockState getBlockState(BlockPos var1) {
            return base.getBlockState(var1);
        }

        @Override
        public FluidState getFluidState(BlockPos var1) {
            return base.getFluidState(var1);
        }

        @Override
        public int getHeight() {
            return base.getHeight();
        }

        @Override
        public int getBottomY() {
            return base.getBottomY();
        }

        @Override
        public boolean isChunkLoaded(int x, int z) {
            return base.isChunkLoaded(x, z);
        }

        @Override
        public float getBrightness(Direction var1, boolean var2) {
            return base.getBrightness(var1, var2);
        }

        @Override
        public LightingProvider getLightingProvider() {
            return base.getLightingProvider();
        }

        @Override
        public int getColor(BlockPos var1, ColorResolver var2) {
            return base.getColor(var1, var2);
        }
        
        @Override
        public boolean sectionExists(int x, int y, int z) {
            if (!isChunkLoaded(x, z)) return false;

            Chunk chunk = base.getChunk(x, z);
            return chunk.getSection(chunk.sectionCoordToIndex(y)) != null;
        }
    }
}
