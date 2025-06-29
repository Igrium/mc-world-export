package com.igrium.worldexport.world;

import com.igrium.worldexport.collectionutils.WriteSynchronizedList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks updates to blocks over time in a world.
 */
public class WorldCapture {

    public sealed interface Keyframe {
        int tick();

        Comparator<Keyframe> COMPARATOR = Comparator.comparingInt(Keyframe::tick);
    }

    /**
     * Marks that a block has been during in the capture.
     *
     * @param tick     The tick number this update happened on.
     * @param oldBlock The block that was replaced.
     * @param newBlock The block it was replaced with.
     */
    public record BlockKeyframe(int tick, @Nullable BlockState oldBlock, BlockState newBlock) implements Keyframe {
    }


    /**
     * Marks that an entire section was updated during the capture. Only used when a section was unloaded and reloaded.
     *
     * @param tick           The tick number this update happened on.
     * @param newSectionData The new section data.
     */
    public record SectionKeyframe(int tick, PalettedContainer<BlockState> newSectionData) implements Keyframe {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldCapture.class);

    @Getter
    private final Map<ChunkSectionPos, ReadableContainer<BlockState>> baseSections = new ConcurrentHashMap<>();

    /**
     * The minimum exported chunk section pos, inclusive.
     */
    @Getter
    private final ChunkSectionPos boundsMin;

    /**
     * The maximum exported chunk section pos, inclusive.
     */
    @Getter
    private final ChunkSectionPos boundsMax;

    /**
     * All the block keyframes in the recording. DO NOT MODIFY DIRECTLY!
     */
    @Getter
    private final Map<BlockPos, Int2ObjectSortedMap<BlockKeyframe>> blockKeyframes = new ConcurrentHashMap<>();

    /**
     * All the section keyframes in the recording. Initial-capture sections are not included.
     * DO NOT MODIFY DIRECTLY!
     */
    @Getter
    private final Map<ChunkSectionPos, Int2ObjectSortedMap<SectionKeyframe>> sectionKeyframes = new ConcurrentHashMap<>();

    /**
     * Check if a given section has any keyframes in it (so far).
     * @param pos Section to check.
     * @return If there are any keyframes.
     */
    public boolean isSectionKeyframed(ChunkSectionPos pos) {
        return sectionKeyframes.containsKey(pos);
    }

    public WorldCapture(ChunkSectionPos minPos, ChunkSectionPos maxPos) {

        int xMin = Math.min(minPos.getX(), maxPos.getX());
        int yMin = Math.min(minPos.getY(), maxPos.getY());
        int zMin = Math.min(minPos.getZ(), maxPos.getZ());

        int xMax = Math.max(minPos.getX(), maxPos.getX());
        int yMax = Math.max(minPos.getY(), maxPos.getY());
        int zMax = Math.max(minPos.getZ(), maxPos.getZ());

        boundsMin = ChunkSectionPos.from(xMin, yMin, zMin);
        boundsMax = ChunkSectionPos.from(xMax, yMax, zMax);
    }

    public boolean isInBounds(ChunkPos pos) {
        return boundsMin.getX() <= pos.x && pos.x <= boundsMax.getX()
                && boundsMin.getZ() <= pos.z && pos.z <= boundsMax.getZ();
    }

    public boolean isInBounds(ChunkSectionPos pos) {
        return boundsMin.getX() <= pos.getX() && pos.getX() <= boundsMax.getX()
                && boundsMin.getY() <= pos.getY() && pos.getY() <= boundsMax.getY()
                && boundsMin.getZ() <= pos.getZ() && pos.getZ() <= boundsMax.getZ();
    }

    public boolean isInBounds(BlockPos pos) {
        return isInBounds(ChunkSectionPos.from(pos));
    }


    public void addBlockKeyframe(BlockPos pos, BlockKeyframe keyframe) {
        var map = blockKeyframes.computeIfAbsent(pos, p -> new Int2ObjectAVLTreeMap<>());
        map.put(keyframe.tick, keyframe);
    }

    public void addBlockKeyframe(BlockPos pos, int tick, @Nullable BlockState oldBlock, BlockState newBlock) {
        addBlockKeyframe(pos, new BlockKeyframe(tick, oldBlock, newBlock));
    }

    public void addSectionKeyframe(ChunkSectionPos pos, SectionKeyframe keyframe) {
        var map = sectionKeyframes.computeIfAbsent(pos, p -> new Int2ObjectAVLTreeMap<>());
        map.put(keyframe.tick, keyframe);
    }

    public void addSectionKeyframe(ChunkSectionPos pos, int tick, PalettedContainer<BlockState> section) {
        addSectionKeyframe(pos, new SectionKeyframe(tick, section));
    }


    /**
     * Return the block at a given position during a specific tick.
     * @param pos Position to query.
     * @param tick Tick to query.
     * @return The block. Air if the position is out-of-bounds.
     */
    public BlockState getBlock(BlockPos pos, int tick) {
        if (!isInBounds(pos)) {
            return Blocks.AIR.getDefaultState();
        }

        ChunkSectionPos cPos = ChunkSectionPos.from(pos);

        // I really wish Java had an elvis operator...
        Int2ObjectSortedMap<BlockKeyframe> blockKeyframes = getBlockKeyframes().get(pos);
        if (blockKeyframes != null) blockKeyframes = blockKeyframes.headMap(tick + 1);

        Int2ObjectSortedMap<SectionKeyframe> sectionKeyframes = getSectionKeyframes().get(cPos);
        if (sectionKeyframes != null) sectionKeyframes = sectionKeyframes.headMap(tick + 1);

        ReadableContainer<BlockState> base = baseSections.get(cPos);

        if (isNullOrEmpty(blockKeyframes) && isNullOrEmpty(sectionKeyframes) && base == null) {
            return Blocks.AIR.getDefaultState();
        }

        int localX = ChunkSectionPos.getLocalCoord(pos.getX());
        int localY = ChunkSectionPos.getLocalCoord(pos.getY());
        int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());

        // Identify the last (timeline-wise) keyframe that can affect this block
        BlockKeyframe lastBlockKeyframe = null;
        if (!isNullOrEmpty(blockKeyframes)) {
            lastBlockKeyframe = blockKeyframes.get(blockKeyframes.lastIntKey());
        }

        SectionKeyframe lastSectionKeyframe = null;
        if (!isNullOrEmpty(sectionKeyframes)) {
            lastSectionKeyframe = sectionKeyframes.get(sectionKeyframes.lastIntKey());
        }

        Keyframe lastKeyframe = getLastKeyframeNullable(lastBlockKeyframe, lastSectionKeyframe);

        if (lastKeyframe != null) {
            if (lastKeyframe instanceof BlockKeyframe bk) {
                return bk.newBlock;
            } else if (lastKeyframe instanceof SectionKeyframe sk) {
                return sk.newSectionData().get(localX, localY, localZ);
            }
        }

        if (base != null) {
            return base.get(localX, localY, localZ);
        }

        return Blocks.AIR.getDefaultState();
    }

    private static boolean isNullOrEmpty(@Nullable Map<?, ?> collection) {
        return collection == null || collection.isEmpty();
    }

    private static @Nullable Keyframe getLastKeyframeNullable(@Nullable Keyframe k1, @Nullable Keyframe k2) {
        if (k1 == null && k2 == null) {
            return null;
        } else if (k1 == null) {
            return k2;
        } else if (k2 == null) {
            return k1;
        } else {
            return k2.tick() > k1.tick() ? k2 : k1;
        }
    }
}
