package com.igrium.worldexport.world;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A re-implementation of {@link RenderChunkRegion} for section columns.
 */
public class SectionColumnRenderRegion implements BlockAndTintGetter {
    private final int chunkXOffset;
    private final int chunkZOffset;

    private final @Nullable SimpleSectionColumn[] columns;
    private final BlockAndTintGetter world;


    public SectionColumnRenderRegion(int chunkXOffset, int chunkZOffset, SimpleSectionColumn[] columns, BlockAndTintGetter world) {
        this.chunkXOffset = chunkXOffset;
        this.chunkZOffset = chunkZOffset;
        this.columns = columns;
        this.world = world;
    }

    private @Nullable SimpleSectionColumn getColumn(int x, int z) {
        return this.columns[getIndex(chunkXOffset, chunkZOffset, x, z)];
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return world.getShade(direction, shaded);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return world.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return world.getBlockTint(pos, colorResolver);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int localX = SectionPos.sectionRelative(pos.getX());
        int localZ = SectionPos.sectionRelative(pos.getZ());
        SimpleSectionColumn column = getColumn(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));

        return column != null ? column.getBlockState(localX, pos.getY(), localZ) : Blocks.AIR.defaultBlockState();
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
    public int getMinY() {
        return world.getMinY();
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
    public static SectionColumnRenderRegion build(Map<ChunkPos, SimpleSectionColumn> columns, ChunkPos cPos, BlockAndTintGetter world) {
        int minChunkX = cPos.x - 1;
        int minChunkZ = cPos.z - 1;
        int maxChunkX = cPos.x + 1;
        int maxChunkZ = cPos.z + 1;
        SimpleSectionColumn[] colArray = new SimpleSectionColumn[9];

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                int index = getIndex(minChunkX, minChunkZ, chunkX, chunkZ);
                colArray[index] = columns.get(new ChunkPos(chunkX, chunkZ));
            }
        }

        return new SectionColumnRenderRegion(minChunkX, minChunkZ, colArray, world);

    }

}
