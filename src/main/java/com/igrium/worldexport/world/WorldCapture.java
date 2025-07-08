package com.igrium.worldexport.world;

import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.util.ChunkDiffs;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks updates to blocks over time in a world. Does not store actual mesh data; only block values.
 */
public class WorldCapture {

    public record BlockUpdate(int tick, BlockState newBlock) {};

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldCapture.class);

    @Getter
    private final ChunkSectionBox bounds;

    /**
     * The base world that was copied from the MC world upon capture start.
     */
    @Getter
    private final Map<ChunkPos, SimpleSectionColumn> copiedBaseWorld = new ConcurrentHashMap<>();

    /**
     * A map of all the block updates for a given block. DO NOT MODIFY DIRECTLY
     */
    @Getter
    private final Map<BlockPos, Int2ObjectSortedMap<BlockUpdate>> blockUpdates = new ConcurrentHashMap<>();

    private final Set<ChunkSectionPos> sectionsWithUpdates = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ChunkSectionPos> sectionsWithUpdatesUnmodifiable = Collections.unmodifiableSet(sectionsWithUpdates);

    public WorldCapture(ChunkSectionBox bounds) {
        this.bounds = bounds;
    }

    /**
     * Copy all relevant chunks from a world into this capture. These will be considered "base" chunks,
     * which updates are applied on top of.
     *
     * @param world World to extract chunks from.
     * @implNote Somewhat expensive operation. Should only be called at the start of capture.
     */
    public void captureBaseWorld(World world) {
        var biomeRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);

        for (int z = bounds.minZ(); z < bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x < bounds.maxX(); x++) {
                Chunk chunk = world.getChunk(x, z);
                if (chunk == null)
                    continue;

                SimpleSectionColumn col = SimpleSectionColumn.fromChunk(chunk, bounds.minY(), bounds.sizeY(), biomeRegistry);
                copiedBaseWorld.put(new ChunkPos(x, z), col);
            }
        }
    }

    /**
     * Called whenever a chunk has been loaded to either add it to the base world or check it's modified blocks.
     * @param chunk Chunk that was loaded.
     * @param tick The current tick.
     */
    public void onChunkLoaded(WorldChunk chunk, int tick) {
        var biomeRegistry = chunk.getWorld().getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        SimpleSectionColumn newVal = SimpleSectionColumn.fromChunk(chunk, bounds.minY(), bounds.sizeY(), biomeRegistry);
        SimpleSectionColumn oldVal = copiedBaseWorld.putIfAbsent(chunk.getPos(), newVal);

        // The chunk had been previously loaded; diff it and add block updates.
        if (oldVal != null) {
            List<ChunkDiffs.Diff<BlockState>> diffs = ChunkDiffs.diff(oldVal, newVal);

            for (var diff : diffs) {
                BlockPos globalPos = chunk.getPos().getBlockPos(diff.x(), diff.y(), diff.z());
                addBlockUpdate(globalPos, diff.secondVal(), tick);
            }
        }
    }

    /**
     * Add a block update keyframe.
     *
     * @param pos    Position of the updated block.
     * @param update The update data.
     */
    public void addBlockUpdate(BlockPos pos, BlockUpdate update) {
        Int2ObjectSortedMap<BlockUpdate> map = blockUpdates.computeIfAbsent(pos,
                p -> Int2ObjectSortedMaps.synchronize(new Int2ObjectAVLTreeMap<>()));
        map.put(update.tick, update);
        sectionsWithUpdates.add(ChunkSectionPos.from(pos));
    }

    /**
     * Add a block update keyframe.
     *
     * @param pos      Position of the updated block.
     * @param newBlock The new block.
     * @param tick     The current tick.
     */
    public final void addBlockUpdate(BlockPos pos, BlockState newBlock, int tick) {
        addBlockUpdate(pos, new BlockUpdate(tick, newBlock));
    }

    /**
     * Find all chunk sections that have an update in them.
     * @return An unmodifiable set of all sections with an update.
     */
    public Set<ChunkSectionPos> getSectionsWithUpdates() {
        return sectionsWithUpdatesUnmodifiable;
    }

    /**
     * Check if a given section has any updates in it.
     * @param sPos Section to check.
     * @return <code>true</code> if the section has any updates.
     */
    public boolean sectionHasUpdates(ChunkSectionPos sPos) {
        return sectionsWithUpdates.contains(sPos);
    }

    /**
     * Return the block at a given position during a specific tick.
     *
     * @param pos  Position to query.
     * @param tick Tick to query.
     * @return The block. Air if the position is out-of-bounds.
     */
    public BlockState getBlock(BlockPos pos, int tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Tick may not be less than 0 (" + tick + ")");
        }
        if (!bounds.isInBounds(ChunkSectionPos.from(pos))) {
            return Blocks.AIR.getDefaultState();
        }

        Int2ObjectSortedMap<BlockUpdate> updateMap = blockUpdates.get(pos);
        if (updateMap != null) {
            var headMap = updateMap.headMap(tick + 1);
            if (!headMap.isEmpty()) {
                return headMap.get(headMap.lastIntKey()).newBlock();
            }

        }

        // Fallback to base
        ChunkPos cPos = new ChunkPos(pos);
        SimpleSectionColumn col = copiedBaseWorld.get(cPos);
        if (col != null) {
            return col.getBlockState(
                    ChunkSectionPos.getLocalCoord(pos.getX()), pos.getY(), ChunkSectionPos.getLocalCoord(pos.getZ()));
        }
        return Blocks.AIR.getDefaultState();
    }
}
