package com.igrium.worldexport.v1.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * A render view that returns the world as it was at a given timestamp.
 * This does <em>not</em> include lighting information, which this mod doesn't use anyway.
 */
public class SnapshotBlockRenderView implements BlockRenderView {
    private final WorldCapture worldCapture;
    private final World world;
    private final int tick;

    public SnapshotBlockRenderView(WorldCapture worldCapture, World world, int tick) {
        this.worldCapture = worldCapture;
        this.world = world;
        this.tick = tick;
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
        return worldCapture.getBlock(pos, tick);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getBottomY() {
        return 0;
    }
}
