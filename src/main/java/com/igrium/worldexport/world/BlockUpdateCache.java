// TODO(Ravel): Failed to fully resolve file: null cannot be cast to non-null type com.intellij.psi.PsiJavaCodeReferenceElement
package com.igrium.worldexport.world;

import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;

import java.util.*;

/**
 * Keeps track of block updates in a format ideal for tessellation.
 *
 * @apiNote Unlike WorldCaptureOld, this class also keeps track of adjoining block updates
 * because they can affect the culling of a block.
 */
public class BlockUpdateCache {
    // All the blocks that are updated on a given frame, sorted by which section they're in.
    private final Map<SectionPos, Int2ObjectSortedMap<Set<BlockPos>>> sortedKeyframes = new HashMap<>();

    // All the frames that a given block is updated on.
    private final Map<BlockPos, IntSortedSet> blockUpdates = new HashMap<>();

    public static BlockUpdateCache generate(WorldCapture capture) {
        BlockUpdateCache cache = new BlockUpdateCache();
        cache.generateInternal(capture);
        return cache;
    }

    private BlockUpdateCache() {};

    private void generateInternal(WorldCapture capture) {
        // For each block
        for (var entry : capture.getBlockUpdates().entrySet()) {
            // For each update
            for (int frame : entry.getValue().keySet()) {
                // For each adjacent block (updates culling)
                for (BlockPos pos : AdjacentDirectionIterator.getIterable(entry.getKey())) {
                    blockUpdates.computeIfAbsent(pos, p -> new IntAVLTreeSet()).add(frame);

                    sortedKeyframes
                            .computeIfAbsent(SectionPos.of(pos), s -> new Int2ObjectAVLTreeMap<>())
                            .computeIfAbsent(frame, i -> new HashSet<>())
                            .add(pos);
                }
            }
        }
    }

    /**
     * Search forward from a given tick for the next block update.
     *
     * @param pos  Block to search.
     * @param tick Tick to search from.
     * @return The next update tick, or <code>-1</code> if there are no more.
     */
    public int getNextBlockUpdate(BlockPos pos, int tick) {
        IntSortedSet set = blockUpdates.get(pos);
        if (set == null)
            return -1;

        IntSortedSet tailSet = set.tailSet(tick + 1);
        return tailSet.isEmpty() ? -1 : tailSet.firstInt();
    }

    /**
     * Search backward from a given tick for the previous block update.
     *
     * @param pos  Block to search.
     * @param tick Tick to search from.
     * @return The update tick, or <code>-1</code> if there are no more.
     */
    public int getPrevBlockUpdate(BlockPos pos, int tick) {
        IntSortedSet set = blockUpdates.get(pos);
        if (set == null)
            return -1;

        IntSortedSet headSet = set.headSet(tick);
        return headSet.isEmpty() ? -1 : headSet.lastInt();
    }

    /**
     * Get all the ticks where a given block is updated.
     * @param pos Block to search for.
     * @return An unmodifiable set of all the ticks where that block updates.
     */
    public IntSortedSet getBlockUpdateTicks(BlockPos pos) {
        IntSortedSet set = blockUpdates.get(pos);
        return set != null ? IntSortedSets.unmodifiable(set) : IntSortedSets.EMPTY_SET;
    }

    /**
     * Assemble a collection of all updates that affect any block within a section.
     * @param sPos The section in question.
     * @return A map of keyframe ticks and all the blocks in the section that were updated that tick.
     */
    public Int2ObjectSortedMap<Set<BlockPos>> getSectionUpdates(SectionPos sPos) {
        var map = sortedKeyframes.get(sPos);
        return map != null ? map : Int2ObjectSortedMaps.emptyMap();
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
                val = centerPos.relative(Direction.values()[index - 1]);
            } else {
                throw new IndexOutOfBoundsException(index);
            }
            index++;
            return val;
        }
    }

    private static boolean isBlockInSection(BlockPos bPos, SectionPos cPos) {
        return SectionPos.blockToSectionCoord(bPos.getX()) == cPos.getX()
                && SectionPos.blockToSectionCoord(bPos.getY()) == cPos.getY()
                && SectionPos.blockToSectionCoord(bPos.getZ()) == cPos.getZ();
    }
}
