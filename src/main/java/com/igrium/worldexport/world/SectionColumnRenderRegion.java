package com.igrium.worldexport.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A re-implementation of {@link ChunkRendererRegion} for section columns.
 */
public class SectionColumnRenderRegion implements BlockRenderView {
    private final int chunkXOffset;
    private final int chunkZOffset;

    private final @Nullable SimpleSectionColumn[] columns;
    private final BlockRenderView world;


    public SectionColumnRenderRegion(int chunkXOffset, int chunkZOffset, SimpleSectionColumn[] columns, BlockRenderView world) {
        this.chunkXOffset = chunkXOffset;
        this.chunkZOffset = chunkZOffset;
        this.columns = columns;
        this.world = world;
    }

    private @Nullable SimpleSectionColumn getColumn(int x, int z) {
        return this.columns[getIndex(chunkXOffset, chunkZOffset, x, z)];
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return 15;
    }

    @Override
    public LightingProvider getLightingProvider() {
        return world.getLightingProvider();
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver) {
        return world.getColor(pos, colorResolver);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int localX = ChunkSectionPos.getLocalCoord(pos.getX());
        int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());
        SimpleSectionColumn column = getColumn(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));

        return column != null ? column.getBlockState(localX, pos.getY(), localZ) : Blocks.AIR.getDefaultState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return world.getHeight();
    }

    @Override
    public int getBottomY() {
        return world.getBottomY();
    }

    /**
     * Get the index in the render region chunk array for a given chunk.
     */
    private static int getIndex(int xOffset, int zOffset, int x, int z) {
        return x - xOffset + (z - zOffset) * 3;
    }

    /**
     * Create a column render region from a set of columns.
     * @param columns All columns that might be queried by their global chunk positions.
     * @param cPos The chunk that will actually be rendered.
     * @param world World to deffer unsupported operations to.
     * @return The render region.
     */
    public static SectionColumnRenderRegion build(Map<ChunkPos, SimpleSectionColumn> columns, ChunkPos cPos, BlockRenderView world) {
        int minChunkX = cPos.x - 1;
        int minChunkZ = cPos.z - 1;
        int maxChunkX = cPos.x + 1;
        int maxChunkZ = cPos.z + 1;
        SimpleSectionColumn[] colArray = new SimpleSectionColumn[9];

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX < maxChunkX; chunkX++) {
                int index = getIndex(minChunkX, minChunkZ, chunkX, chunkZ);
                colArray[index] = columns.get(new ChunkPos(chunkX, chunkZ));
            }
        }

        return new SectionColumnRenderRegion(minChunkX, minChunkZ, colArray, world);

    }

}
