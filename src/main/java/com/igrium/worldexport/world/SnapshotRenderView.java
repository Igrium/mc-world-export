package com.igrium.worldexport.world;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public class SnapshotRenderView implements BlockAndTintGetter {

    private final WorldCapture worldCapture;
    private final BlockAndTintGetter world;
    private final int tick;

    public SnapshotRenderView(WorldCapture worldCapture, BlockAndTintGetter world, int tick) {
        this.worldCapture = worldCapture;
        this.world = world;
        this.tick = tick;
    }

    @Override
    public @NonNull LevelLightEngine getLightEngine() {
        return world.getLightEngine();
    }

    @Override
    public @NonNull CardinalLighting cardinalLighting() {
        return SectionColumnRenderRegion.NO_SHADE;
    }

    @Override
    // TODO: snapshot tint
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return world.getBlockTint(pos, colorResolver);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public @NonNull BlockState getBlockState(BlockPos pos) {
        return worldCapture.getBlock(pos, tick);
    }

    @Override
    public @NonNull FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return worldCapture.getHeight() * 16;
    }

    @Override
    public int getMinY() {
        return worldCapture.getMinY() * 16;
    }
}
