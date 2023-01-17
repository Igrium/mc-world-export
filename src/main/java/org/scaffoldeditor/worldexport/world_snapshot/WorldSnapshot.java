package org.scaffoldeditor.worldexport.world_snapshot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/**
 * Contains a "snapshot" of a world at a given time, which can be subsequently 
 */
public class WorldSnapshot implements BlockView {
    private final WorldView world;
    
    /**
     * A backup of all the blockstates that have been overwritten since ths snapshot.
     */
    protected final Map<BlockPos, BlockState> overwrittenStates = new ConcurrentHashMap<>();

    private boolean isValid = true;

    protected WorldSnapshot(WorldView world) {
        this.world = world;
    }

    /**
     * Get the underlying world that this is a view of. Note that the world may have changed since the snapshot was taken.
     * @return
     */
    public WorldView getWorld() {
        return world;
    }

    public void onBlockUpdated(BlockPos pos, @Nullable BlockState oldState, BlockState state) {
        if (state.equals(oldState)) return;
        overwrittenStates.put(pos, state);
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
}
