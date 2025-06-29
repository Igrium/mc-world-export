package com.igrium.worldexport.world.mesh;

import com.igrium.worldexport.world.WorldCapture;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Tracks all modified blocks across all frames.
 * A block is considered updated when it, or an adjacent block, gets updated, causing a model re-mesh.
 */
public class BlockUpdateTracker {

    /**
     * All the updates to a given block or it's neighbors over the animation.
     */
    private final Map<BlockPos, IntList> blockUpdates = new HashMap<>();

    /**
     * All full-chunk updates for a given chunk over the animation.
     */
    private final Map<ChunkSectionPos, IntList> sectionUpdates = new HashMap<>();

    private BlockUpdateTracker() {}

    /**
     * Read all the keyframes from a WorldCapture and cache block updates for meshing.
     * @param capture Capture to read.
     * @return The block update tracker.
     */
    public static BlockUpdateTracker parse(WorldCapture capture) {
        BlockUpdateTracker tracker = new BlockUpdateTracker();
        tracker.generate(capture);
        return tracker;
    }

    private void generate(WorldCapture capture) {
        // Block keys
        for (var entry : capture.getBlockKeyframes().entrySet()) {
            // For each block and its 6 adjacent neighbors...
            for (BlockPos pos : AdjacentDirectionIterator.getIterable(entry.getKey())) {
                IntList list = blockUpdates.computeIfAbsent(pos, p -> new IntArrayList());
                // Add all ticks when the block was updated.
                for (var key : entry.getValue()) {
                    list.add(key.tick());
                }
            }
        }

        // Section keys
        for (var entry : capture.getSectionKeyframes().entrySet()) {
            IntList list = new IntArrayList();
            // Add all ticks when the chunk section was updated.
            for (var key : entry.getValue()) {
                list.add(key.tick());
            }
            sectionUpdates.put(entry.getKey(), list);
        }
    }

    /**
     * Search forward for the next block update for a block from a given tick.
     * @param pos Block to search.
     * @param tick Tick to search from.
     * @return The next block update, or null if there are no more.
     */
    public @Nullable Integer getNextBlockUpdate(BlockPos pos, int tick) {
        IntList list = blockUpdates.get(pos);
        if (list == null)
            return null;

        return getNext(list, tick);
    }

    /**
     * Search backward for the previous block update for a block from a given tick.
     * @param pos Block to search.
     * @param tick Tick to search from.
     * @return The previous block update, or null if there are no more.
     */
    public @Nullable Integer getPrevBlockUpdate(BlockPos pos, int tick) {
        IntList list = blockUpdates.get(pos);
        if (list == null)
            return null;

        return getPrev(list, tick);
    }

    /**
     * Search forward for the next section update for a section from a given tick.
     * @param pos section to search.
     * @param tick Tick to search from.
     * @return The next section update, or null if there are no more.
     */
    public @Nullable Integer getNextSectionUpdate(ChunkSectionPos pos, int tick) {
        IntList list = sectionUpdates.get(pos);
        if (list == null)
            return null;

        return getNext(list, tick);
    }

    /**
     * Search backward for the previous section update for a section from a given tick.
     * @param pos section to search.
     * @param tick Tick to search from.
     * @return The previous section update, or null if there are no more.
     */
    public @Nullable Integer getPrevSectionUpdate(ChunkSectionPos pos, int tick) {
        IntList list = sectionUpdates.get(pos);
        if (list == null)
            return null;

        return getPrev(list, tick);
    }

    /**
     * Search forward for the next block or section from a given tick.
     * @param pos BLock to search.
     * @param tick Tick to search from.
     * @return The next update, or null if there are no more.
     */
    public @Nullable Integer getNextModelUpdate(BlockPos pos, int tick) {
        Integer nextBlock = getNextBlockUpdate(pos, tick);
        if (nextBlock != null && nextBlock == tick + 1)
            return nextBlock; // Shortcut if we already know the next tick is an update.

        Integer nextSection = getNextSectionUpdate(ChunkSectionPos.from(pos), tick);
        if (nextSection != null && nextSection == tick + 1)
            return nextSection;

        for (var cPos : getAdjacentSections(pos)) {
            nextSection = nullableMin(nextSection, getNextSectionUpdate(cPos, tick));
        }

        return nullableMin(nextBlock, nextSection);
    }

    /**
     * Search backward for the previous block or section update from a given tick.
     * @param pos BLock to search.
     * @param tick Tick to search from.
     * @return The previous update, or null if there are no more.
     */
    public @Nullable Integer getPrevModelUpdate(BlockPos pos, int tick) {
        Integer prevBlock = getPrevBlockUpdate(pos, tick);
        if (prevBlock != null && prevBlock == tick - 1)
            return prevBlock; // Shortcut if we already know the next tick is an update.

        Integer prevSection = getPrevSectionUpdate(ChunkSectionPos.from(pos), tick);
        if (prevSection != null && prevSection == tick - 1)
            return prevSection;

        for (var cPos : getAdjacentSections(pos)) {
            prevSection = nullableMax(prevSection, getPrevSectionUpdate(cPos, tick));
        }

        return nullableMax(prevBlock, prevSection);
    }

    private @Nullable Integer getNext(IntCollection list, int value) {
        Integer result = null;
        for (var i : list) {
            if (i > value && (result == null || i < result)) {
                result = i;
            }
        }
        return result;
    }

    private @Nullable Integer getPrev(IntCollection list, int value) {
        Integer result = null;
        for (var i : list) {
            if (i < value && (result == null || i > result)) {
                result = i;
            }
        }
        return result;
    }


    private List<ChunkSectionPos> getAdjacentSections(BlockPos pos) {
        List<ChunkSectionPos> list = new ArrayList<>(3);

        ChunkSectionPos cPos = ChunkSectionPos.from(pos);

        int localX = ChunkSectionPos.getLocalCoord(pos.getX());
        int localY = ChunkSectionPos.getLocalCoord(pos.getY());
        int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());

        if (localX == 0) {
            list.add(cPos.add(-1, 0, 0));
        }
        if (localX == 15) {
            list.add(cPos.add(1, 0, 0));
        }
        if (localY == 0) {
            list.add(cPos.add(0, -1, 0));
        }
        if (localY == 15) {
            list.add(cPos.add(0, 1, 0));
        }
        if (localZ == 0) {
            list.add(cPos.add(0, 0, -1));
        }
        if (localZ == 15) {
            list.add(cPos.add(0, 0, 1));
        }

        return list;
    }

    private @Nullable Integer nullableMin(@Nullable Integer a, @Nullable Integer b) {
        if (a == null && b == null) {
            return null;
        } else if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            return Math.min(a, b);
        }
    }

    private @Nullable Integer nullableMax(@Nullable Integer a, @Nullable Integer b) {
        if (a == null && b == null) {
            return null;
        } else if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            return Math.max(a, b);
        }
    }

    private static class AdjacentDirectionIterator implements Iterator<BlockPos> {

        static Iterable<BlockPos> getIterable(BlockPos pos) {
            return () -> new AdjacentDirectionIterator(pos);
        }

        int index = 0;

        final BlockPos centerPos;

        private AdjacentDirectionIterator(BlockPos centerPos) {
            this.centerPos = centerPos;
        }

        @Override
        public boolean hasNext() {
            return index < 7;
        }

        @Override
        public BlockPos next() {
            BlockPos val;
            if (index == 0) {
                val = centerPos;
            } else if (index <= 6) {
                val = centerPos.offset(Direction.values()[index - 1]);
            } else {
                throw new IndexOutOfBoundsException(index);
            }
            index++;
            return val;
        }
    }
}
