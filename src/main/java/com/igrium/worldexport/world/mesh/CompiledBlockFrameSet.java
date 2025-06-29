package com.igrium.worldexport.world.mesh;

import com.igrium.worldexport.world.WorldCapture;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Keeps track of block update frames in a format ideal for tessellation.
 * @apiNote In this implementation, blocks are given a keyframe if any adjoining blocks are updated.
 */
public class CompiledBlockFrameSet {

    // All the blocks that are updated on a given frame.
    private final Int2ObjectSortedMap<Set<BlockPos>> blockKeyframes = new Int2ObjectAVLTreeMap<>();

    // All the sections that are updated on a given frame.
    private final Int2ObjectSortedMap<Set<ChunkSectionPos>> sectionKeyframes = new Int2ObjectAVLTreeMap<>();

    // All the frames that a given block is updated on.
    private final Map<BlockPos, IntSortedSet> blockUpdates = new HashMap<>();

    // All the frames that a given section is updated on.
    private final Map<ChunkSectionPos, IntSortedSet> sectionUpdates = new HashMap<>();

    private CompiledBlockFrameSet() {}

    public static CompiledBlockFrameSet compile(WorldCapture capture) {
        CompiledBlockFrameSet value = new CompiledBlockFrameSet();
        value.generate(capture);
        return value;
    }

    private void generate(WorldCapture capture) {
        // For each block
        for (var entry : capture.getBlockKeyframes().entrySet()) {
            // For each keyframe
            for (int frame : entry.getValue().keySet()) {
                Set<BlockPos> keyframe = blockKeyframes.computeIfAbsent(frame, i -> new HashSet<>());

                // For each adjacent block
                for (BlockPos pos : AdjacentDirectionIterator.getIterable(entry.getKey())) {
                    IntSortedSet blockUpdateSet = blockUpdates.computeIfAbsent(pos, p -> new IntAVLTreeSet());
                    blockUpdateSet.add(frame);

                    keyframe.add(pos);
                }
            }
        }

        // For each section
        for (var entry : capture.getSectionKeyframes().entrySet()) {
            IntSortedSet sectionUpdateSet = new IntAVLTreeSet();
            // For each keyframe
            for (int frame : entry.getValue().keySet()) {
                Set<ChunkSectionPos> keyframe = sectionKeyframes.computeIfAbsent(frame, i -> new HashSet<>());
                keyframe.add(entry.getKey());

                sectionUpdateSet.add(frame);
            }
            sectionUpdates.put(entry.getKey(), sectionUpdateSet);

        }
    }

    /**
     * Search forward for the next block update from a given tick.
     *
     * @param pos  Block to search.
     * @param tick Tick to search from.
     * @return The next update, or null if there are no more.
     */
    public @Nullable Integer getNextBlockUpdate(BlockPos pos, int tick) {
        IntSortedSet set = blockUpdates.get(pos);
        if (set == null)
            return null;

        IntSortedSet tailSet = set.tailSet(tick + 1);
        return tailSet.isEmpty() ? null : tailSet.firstInt();
    }

    /**
     * Search backward for the previous block update from a given tick.
     *
     * @param pos  Block to search.
     * @param tick Tick to search from.
     * @return The previous update, or null if there are no more.
     */
    public @Nullable Integer getPrevBlockUpdate(BlockPos pos, int tick) {
        IntSortedSet set = blockUpdates.get(pos);
        if (set == null)
            return null;

        IntSortedSet headSet = set.headSet(tick);
        return headSet.isEmpty() ? null : headSet.lastInt();
    }

    /**
     * Search forward for the next section update from a given tick.
     *
     * @param pos  Section to search.
     * @param tick Tick to search from.
     * @return The next update, or null if there are no more.
     */
    public @Nullable Integer getNextSectionUpdate(ChunkSectionPos pos, int tick) {
        IntSortedSet set = sectionUpdates.get(pos);
        if (set == null)
            return null;

        IntSortedSet tailSet = set.tailSet(tick + 1);
        return tailSet.isEmpty() ? null : tailSet.firstInt();
    }

    /**
     * Search backward for the previous section update from a given tick.
     *
     * @param pos  Section to search.
     * @param tick Tick to search from.
     * @return The previous update, or null if there are no more.
     */
    public @Nullable Integer getPrevSectionUpdate(ChunkSectionPos pos, int tick) {
        IntSortedSet set = sectionUpdates.get(pos);
        if (set == null)
            return null;

        IntSortedSet headSet = set.headSet(tick);
        return headSet.isEmpty() ? null : headSet.lastInt();
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

    /**
     * Get all block keyframe ticks.
     * @return All ticks that have a block keyframe.
     */
    public IntSortedSet getBlockKeyframes() {
        return IntSortedSets.unmodifiable(blockKeyframes.keySet());
    }

    /**
     * Get all the keyframes on which a given block is updated.
     * @param pos The block's position.
     * @return An unmodifiable set of all update ticks.
     */
    public IntSortedSet getBlockUpdates(BlockPos pos) {
        var set = blockUpdates.get(pos);
        return set != null ? IntSortedSets.unmodifiable(set) : IntSortedSets.EMPTY_SET;
    }

    /**
     * Assemble a collection of all keyframes that update a block within a section.
     *
     * @param cPos Section pos to search for.
     * @return A map of keyframe ticks and all the blocks in the section that were updated that tick.
     * @implNote Doesn't take into account section keyframes.
     */
    public Int2ObjectSortedMap<Set<BlockPos>> getSectionBlockKeyframes(ChunkSectionPos cPos) {
        Int2ObjectSortedMap<Set<BlockPos>> map = new Int2ObjectAVLTreeMap<>();

        for (var entry : blockKeyframes.int2ObjectEntrySet()) {
            Set<BlockPos> set = null;
            for (var bPos : entry.getValue()) {
                if (isBlockInSection(bPos, cPos)) {
                    if (set == null) set = new HashSet<>();
                    set.add(bPos);
                }
            }

            if (set != null) {
                map.put(entry.getIntKey(), set);
            }
        }

        return map;
    }

    /**
     * Assemble a set of all keyframes on which any block within a section is updated.
     * @param cPos Section to search.
     * @return All update ticks.
     * @apiNote Doesn't account for section keyframes
     */
    public IntSortedSet getSectionBlockUpdates(ChunkSectionPos cPos) {
        IntSortedSet set = new IntAVLTreeSet();
        for (var entry : blockKeyframes.int2ObjectEntrySet()) {

            if (entry.getValue().stream().anyMatch(bPos -> isBlockInSection(bPos, cPos))) {
                set.add(entry.getIntKey());
            }

        }

        return set;
    }

    /**
     * Get a set of all section keyframe ticks.
     * @return All ticks that have a section keyframe.
     */
    public IntSortedSet getSectionKeyframes() {
        return IntSortedSets.unmodifiable(sectionKeyframes.keySet());
    }

    public IntStream streamKeyframes() {
        return IntStream.concat(blockKeyframes.keySet().intStream(), sectionKeyframes.keySet().intStream())
                .distinct();
    }

    /**
     * Get a set of all blocks that will be updated by a block keyframe.
     * @param tick Keyframe tick.
     * @return The updated blocks; an empty set if there is no keyframe on that tick.
     */
    public Set<BlockPos> getBlockKeyframeMask(int tick) {
        Set<BlockPos> set = blockKeyframes.get(tick);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /**
     * Get a set of all sections that will be updated by a section keyframe.
     * @param tick Keyframe tick.
     * @return The updated sections; an empty set if there are no keyframes on that tick.
     */
    public Set<ChunkSectionPos> getSectionKeyframeMask(int tick) {
        Set<ChunkSectionPos> set = sectionKeyframes.get(tick);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    private static List<ChunkSectionPos> getAdjacentSections(BlockPos pos) {
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

    private static @Nullable Integer nullableMin(@Nullable Integer a, @Nullable Integer b) {
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

    private static @Nullable Integer nullableMax(@Nullable Integer a, @Nullable Integer b) {
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

    private static boolean isBlockInSection(BlockPos bPos, ChunkSectionPos cPos) {
        return ChunkSectionPos.getSectionCoord(bPos.getX()) == cPos.getX()
                && ChunkSectionPos.getSectionCoord(bPos.getY()) == cPos.getY()
                && ChunkSectionPos.getSectionCoord(bPos.getZ()) == cPos.getZ();
    }
}
