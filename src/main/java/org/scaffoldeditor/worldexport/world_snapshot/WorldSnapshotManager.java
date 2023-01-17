package org.scaffoldeditor.worldexport.world_snapshot;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.scaffoldeditor.worldexport.ReplayExportMod;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public final class WorldSnapshotManager implements ClientBlockPlaceCallback {
    private final Set<WorldSnapshot> snapshots = Collections.newSetFromMap(new WeakHashMap<>());

    public WorldSnapshotManager() {
        ClientBlockPlaceCallback.EVENT.register(this);
    }

    /**
     * Get the concurrent world manager instance. Shortcut for
     * <code>ReplayExportMod.getInstance().getWorldSnapshotManager()</code>
     * 
     * @return The current instance.
     */
    public static WorldSnapshotManager getInstance() {
        return ReplayExportMod.getInstance().getWorldSnapshotManager();
    }

    /**
     * Take a "snapshot" of a world in time. This snapshot does <i>not</i>
     * copy the block data. Instead, it sets up a thread-safe view of it that
     * accounts for any potential updates. This way, a world can be captured and
     * iterated through off-thread.
     * 
     * @param world The world to capture.
     * @return The snapshot.
     */
    public synchronized WorldSnapshot snapshot(ChunkView world) {
        WorldSnapshot snapshot = new WorldSnapshot(world);
        snapshots.add(snapshot);
        return snapshot;
    }

    /**
     * Take a "snapshot" of a world in time. This snapshot does <i>not</i>
     * copy the block data. Instead, it sets up a thread-safe view of it that
     * accounts for any potential updates. This way, a world can be captured and
     * iterated through off-thread.
     * 
     * @param world The world to capture.
     * @return The snapshot.
     */
    public WorldSnapshot snapshot(WorldAccess world) {
        return snapshot(new ChunkView.Wrapper(world));
    }

    @Override
    public synchronized void place(BlockPos pos, @Nullable BlockState oldState, BlockState state, World world) {
        snapshots.forEach(snapshot -> {
            if (world.equals(snapshot.getWorld().getBase())) snapshot.onBlockUpdated(pos, oldState, state);
        });
    }

    /**
     * Invalidate and remove all snapshots.
     */
    public synchronized void clear() {
        snapshots.forEach(snapshot -> snapshot.invalidate());
        snapshots.clear();
    }
}
