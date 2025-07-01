package com.igrium.worldexport.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class SimpleColumnRendererRegion implements BlockRenderView {


    private final BlockRenderView base;
    protected final SimpleSectionColumn<PalettedContainer<BlockState>>[] chunks;

    private final int centerX;
    private final int centerZ;

    public SimpleColumnRendererRegion(BlockRenderView base, SimpleSectionColumn<PalettedContainer<BlockState>>[] chunks,
                                      int centerX, int centerZ) {
        if (chunks.length != 9) {
            throw new IllegalArgumentException("chunks must be an array of length 3");
        }

        this.base = base;
        this.chunks = chunks;
        this.centerX = centerX;
        this.centerZ = centerZ;
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
        int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());

        var section = getColumn(chunkX, chunkZ);
        if (section == null)
            return Blocks.AIR.getDefaultState();

        int localX = ChunkSectionPos.getLocalCoord(pos.getX());
        int localY = pos.getY();
        int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());


        return SimpleSectionColumn.getBlock(section, localX, localY, localZ, Blocks.AIR.getDefaultState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return chunks[4].getHeight();
    }

    @Override
    public int getBottomY() {
        return chunks[4].getBottomY();
    }

    @Override
    public int countVerticalSections() {
        return chunks[4].countVerticalSections();
    }

    @Override
    public int getBottomSectionCoord() {
        return chunks[4].getBottomSectionCoord();
    }

    private SimpleSectionColumn<PalettedContainer<BlockState>> getColumn(int globalX, int globalZ) {
        return chunks[getArrayIndex(centerX, centerZ, globalX, globalZ)];
    }

    public static int getArrayIndex(int centerX, int centerZ, int chunkX, int chunkZ) {
        return chunkX - centerX + (chunkZ - centerZ) * 3;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static SimpleColumnRendererRegion create(
            BlockRenderView base, ChunkPos center,
            Function<ChunkPos, SimpleSectionColumn<? extends ReadableContainer<BlockState>>> chunkSupplier) {
        SimpleSectionColumn[] array = new SimpleSectionColumn[9];

        array[0] = chunkSupplier.apply(new ChunkPos(center.x - 1, center.z - 1));
        array[1] = chunkSupplier.apply(new ChunkPos(center.x, center.z - 1));
        array[2] = chunkSupplier.apply(new ChunkPos(center.x + 1, center.z - 1));
        array[3] = chunkSupplier.apply(new ChunkPos(center.x - 1, center.z));
        array[4] = chunkSupplier.apply(new ChunkPos(center.x, center.z));
        array[5] = chunkSupplier.apply(new ChunkPos(center.x + 1, center.z));
        array[6] = chunkSupplier.apply(new ChunkPos(center.x - 1, center.z + 1));
        array[7] = chunkSupplier.apply(new ChunkPos(center.x, center.z + 1));
        array[8] = chunkSupplier.apply(new ChunkPos(center.x + 1, center.z + 1));

        return new SimpleColumnRendererRegion(base, array, center.x, center.z);
    }

    public static SimpleColumnRendererRegion create(BlockRenderView base, ChunkPos center, SimpleSectionWorld<? extends  ReadableContainer<BlockState>> world) {
        return create(base, center, world.getChunks()::get);
    }
}
