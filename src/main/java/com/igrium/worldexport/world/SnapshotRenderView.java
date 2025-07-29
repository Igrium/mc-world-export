package com.igrium.worldexport.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

public class SnapshotRenderView implements BlockRenderView {

    private final WorldCapture worldCapture;
    private final BlockRenderView world;
    private final int tick;

    public SnapshotRenderView(WorldCapture worldCapture, BlockRenderView world, int tick) {
        this.worldCapture = worldCapture;
        this.world = world;
        this.tick = tick;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return world.getBrightness(direction, shaded);
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
        return worldCapture.getBounds().sizeY() * 16;
    }

    @Override
    public int getBottomY() {
        return worldCapture.getBounds().minY() * 16;
    }
}
