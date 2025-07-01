package com.igrium.worldexport.world;

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

public class SimpleSectionBlockRenderView implements BlockRenderView {
    private final SimpleSectionWorld<? extends ReadableContainer<BlockState>> world;
    private final BlockRenderView base;

    public SimpleSectionBlockRenderView(SimpleSectionWorld<? extends ReadableContainer<BlockState>> world, BlockRenderView base) {
        this.world = world;
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

        ReadableContainer<BlockState> container = world.getSection(cPos);
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
        return world.getHeight();
    }

    @Override
    public int getBottomY() {
        return world.getBottomY();
    }
}
