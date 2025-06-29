package com.igrium.worldexport.world;

import com.igrium.worldexport.collectionutils.WriteSynchronizedList;
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
 * Captures block updates within a Minecraft world.
 */
public class WorldCapture {

    public sealed interface Keyframe {
        int tick();

        public static final Comparator<Keyframe> COMPARATOR =
                (o1, o2) -> o1.tick() - o2.tick();
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
    private final Map<BlockPos, List<BlockKeyframe>> blockKeyframes = new ConcurrentHashMap<>();

    /**
     * All the section keyframes in the recording. Initial-capture sections are not included.
     * DO NOT MODIFY DIRECTLY!
     */
    @Getter
    private final Map<ChunkSectionPos, List<SectionKeyframe>> sectionKeyframes = new ConcurrentHashMap<>();

    private final Set<ChunkSectionPos> keyframedSections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Get a set of all sections that have a keyframe in them.
     * @return Unmodifiable set.
     */
    public Set<ChunkSectionPos> getKeyframedSections() {
        return Collections.unmodifiableSet(keyframedSections);
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
        var list = blockKeyframes.computeIfAbsent(pos, p -> WriteSynchronizedList.of(new ArrayList<>()));
        list.add(keyframe);
        keyframedSections.add(ChunkSectionPos.from(pos));
    }

    public void addBlockKeyframe(BlockPos pos, int tick, @Nullable BlockState oldBlock, BlockState newBlock) {
        addBlockKeyframe(pos, new BlockKeyframe(tick, oldBlock, newBlock));
    }

    public void addSectionKeyframe(ChunkSectionPos pos, SectionKeyframe keyframe) {
        var list = sectionKeyframes.computeIfAbsent(pos, p -> WriteSynchronizedList.of(new ArrayList<>()));
        list.add(keyframe);
        keyframedSections.add(pos);
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
        List<BlockKeyframe> blockKeyframes = getBlockKeyframes().get(pos);
        List<SectionKeyframe> sectionKeyframes = getSectionKeyframes().get(cPos);
        ReadableContainer<BlockState> base = baseSections.get(cPos);

        if (blockKeyframes == null && sectionKeyframes == null && base == null) {
            return Blocks.AIR.getDefaultState();
        }

        int localX = ChunkSectionPos.getLocalCoord(pos.getX());
        int localY = ChunkSectionPos.getLocalCoord(pos.getY());
        int localZ = ChunkSectionPos.getLocalCoord(pos.getZ());

        // Identify the last (timeline-wise) keyframe that can affect this block.
        Keyframe lastKeyframe = null;
        int lastKeyTick = 0;
        if (sectionKeyframes != null) {
            for (var k : sectionKeyframes) {
                if (k.tick() <= tick && k.tick() >= lastKeyTick) {
                    lastKeyframe = k;
                    lastKeyTick = k.tick();
                }
            }
        }
        if (blockKeyframes != null) {
            for (var k : blockKeyframes) {
                if (k.tick() <= tick && k.tick() >= lastKeyTick) {
                    lastKeyframe = k;
                    lastKeyTick = k.tick();
                }
            }
        }

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
}
