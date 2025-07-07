package com.igrium.worldexport.v1.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A block render view that directly references sets of containers.
 * This simplified version only affects blocks. Other stuff like lighting and biome colors are forwarded to a base world.
 */
@Deprecated
public class SectionSetBlockRenderView implements BlockRenderView {


    private final Map<ChunkSectionPos, ? extends ReadableContainer<BlockState>> blockStateContainer;
    private final BlockRenderView base;

    public SectionSetBlockRenderView(Map<ChunkSectionPos, ? extends ReadableContainer<BlockState>> blockStateContainer, BlockRenderView base) {
        this.blockStateContainer = blockStateContainer;
        this.base = base;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return base.getBrightness(direction, shaded);
    }

    @Override
    public LightingProvider getLightingProvider() {
        return base.getLightingProvider();
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver) {
        return base.getColor(pos, colorResolver);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        ChunkSectionPos cPos = ChunkSectionPos.from(pos);

        ReadableContainer<BlockState> container = blockStateContainer.get(cPos);
        if (container == null) {
            return Blocks.AIR.getDefaultState();
        }

        int localX = ChunkSectionPos.getLocalCoord(pos.getX());
        int localY = ChunkSectionPos.getLocalCoord(pos.getY());
        int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());

        return container.get(localX, localY, localZ);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return base.getHeight();
    }

    @Override
    public int getBottomY() {
        return base.getBottomY();
    }



}
