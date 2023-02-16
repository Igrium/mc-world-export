package org.scaffoldeditor.worldexport.world_snapshot;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

/**
 * Contains a "snapshot" of a world at a given time, which can be subsequently 
 */
public class WorldSnapshot implements ChunkView {
    private final ChunkView world;
    
    /**
     * A backup of all the blockstates that have been overwritten since ths snapshot.
     */
    protected final Map<BlockPos, BlockState> overwrittenStates = new ConcurrentHashMap<>();
    protected final Set<ChunkPos> bannedChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    protected final Set<ChunkSectionPos> bannedSections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private boolean isValid = true;

    protected WorldSnapshot(ChunkView world) {
        this.world = world;
    }

    /**
     * Get the underlying world that this is a view of. Note that the world may have changed since the snapshot was taken.
     * @return
     */
    public ChunkView getWorld() {
        return world;
    }

    public void onBlockUpdated(BlockPos pos, @Nullable BlockState oldState, BlockState state) {
        pos = new BlockPos(pos); // In case this was mutable.
        if (state.equals(oldState)) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) bannedChunks.add(chunkPos);

        ChunkSectionPos secPos = ChunkSectionPos.from(pos);
        if (!world.isSectionLoaded(secPos)) bannedSections.add(secPos);
        overwrittenStates.put(pos, oldState);
    }

    @Override
    public int getHeight() {
        return world.getHeight();
    }

    @Override
    public int getBottomY() {
        return world.getBottomY();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!isValid) {
            throw new IllegalStateException("This snapshot has been invalidated.");
        }
        return overwrittenStates.getOrDefault(pos, world.getBlockState(pos));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    /**
     * If this snapshot is still valid.
     */
    public boolean isValid() {
        return isValid;
    }

    public void invalidate() {
        isValid = false;
    }

    @Override
    public float getBrightness(Direction var1, boolean var2) {
        return world.getBrightness(var1, var2);
    }

    @Override
    public LightingProvider getLightingProvider() {
        return world.getLightingProvider();
    }

    @Override
    public int getColor(BlockPos var1, ColorResolver var2) {
        return world.getColor(var1, var2);
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        if (bannedChunks.contains(new ChunkPos(x, z))) return false;
        return world.isChunkLoaded(x, z);
    }

    @Override
    public boolean isSectionLoaded(int x, int y, int z) {
        if (bannedSections.contains(ChunkSectionPos.from(x, y, z))) return false;
        return world.isSectionLoaded(x, y, z);
    }
}
