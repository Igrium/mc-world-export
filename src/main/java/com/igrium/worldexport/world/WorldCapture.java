package com.igrium.worldexport.world;

import com.google.common.collect.AbstractIterator;
import com.igrium.worldexport.util.ChunkDiffs;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;
import lombok.Getter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for capturing and meshing updates to the block world
 */
public class WorldCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/WorldCapture");

    public record BlockUpdate(int tick, BlockState newBlock) {
    }

    /// === STATE & CONFIGURATION ===

    private final ClientLevel world;

    @Getter
    private final Set<ChunkPos> chunks;

    @Getter
    private final Map<ChunkPos, SimpleSectionColumn> copiedBaseWorld = new ConcurrentHashMap<>();

    private final Map<BlockPos, Int2ObjectSortedMap<BlockUpdate>> blockUpdates = new ConcurrentHashMap<>();
    @Getter
    private final Set<SectionPos> dirtySections = ConcurrentHashMap.newKeySet();

    @Getter
    private final WorldMesher mesher;
    private final PalettedContainerFactory factory;

    /**
     * The chunk-coordinate of the lowest chunk section to capture
     */
    @Getter
    private final int minY;

    /**
     * The number of chunk sections to capture vertically
     */
    @Getter
    private final int height;

    public WorldCapture(ClientLevel world, Set<ChunkPos> chunks, int minY, int height) {
        this.world = world;
        this.chunks = Set.copyOf(chunks);
        this.minY = minY;
        this.height = height;

        mesher = new WorldMesher(world, () -> new SectionIterator(this.chunks.iterator(), this.minY, this.height));
        factory = PalettedContainerFactory.create(world.registryAccess());
    }

    /// === BASE WORLD CAPTURE ===

    /**
     * Copy all relevant chunks from a world into this capture. These will be considered "base" chunks,
     * which updates are applied on top of.
     *
     * @implNote Somewhat expensive operation. Should only be called at the start of capture.
     */
    public void captureBaseWorld() {

        for (var chunkPos : this.chunks) {
            captureBaseChunk(chunkPos);
        }
    }

    private void captureBaseChunk(ChunkPos pos) {
        ChunkAccess chunk = world.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
        if (chunk == null) return;

        SimpleSectionColumn col = SimpleSectionColumn.fromChunk(chunk, minY, height, factory);
        copiedBaseWorld.put(pos, col);
    }

    /// === BLOCK UPDATE TRACKING ===

    /**
     * Called whenever a chunk has been loaded to either add it to the base world or check it's modified blocks.
     *
     * @param chunk Chunk that was loaded.
     * @param tick  The current tick.
     */
    public void onChunkLoaded(LevelChunk chunk, int tick) {
        ChunkPos cPos = chunk.getPos();
        if (!chunks.contains(cPos)) return;

        SimpleSectionColumn newVal = SimpleSectionColumn.fromChunk(chunk, minY, height, factory);
        SimpleSectionColumn oldVal = copiedBaseWorld.putIfAbsent(cPos, newVal);

        // The chunk has been previously loaded; diff it and add block updates
        if (oldVal != null) {
            var diffs = ChunkDiffs.diff(oldVal, newVal);

            for (var diff : diffs) {
                BlockPos globalPos = cPos.getBlockAt(diff.x(), diff.y(), diff.z());
                addBlockUpdate(globalPos, diff.secondVal(), tick);
            }
        } else {
            mesher.queueChunk(cPos, copiedBaseWorld, minY, height);
        }
    }

    /**
     * Add a block update keyframe.
     *
     * @param pos    Position of the updated block.
     * @param update The update data.
     */
    public void addBlockUpdate(BlockPos pos, BlockUpdate update) {
        if (!chunks.contains(ChunkPos.containing(pos))) return;
        Int2ObjectSortedMap<BlockUpdate> map = blockUpdates.computeIfAbsent(new BlockPos(pos),
                p -> Int2ObjectSortedMaps.synchronize(new Int2ObjectAVLTreeMap<>()));
        map.put(update.tick(), update);
        // Stop meshing the base chunk
        markSectionDirty(SectionPos.of(pos));

        // Mark adjacent sections dirty
        int lx = SectionPos.sectionRelative(pos.getX());
        int ly = SectionPos.sectionRelative(pos.getY());
        int lz = SectionPos.sectionRelative(pos.getZ());

        if (lx == 0) markSectionDirtyAt(pos.west());
        if (lx == 15) markSectionDirtyAt(pos.east());
        if (ly == 0) markSectionDirtyAt(pos.below());
        if (ly == 15) markSectionDirtyAt(pos.above());
        if (lz == 0) markSectionDirtyAt(pos.north());
        if (lz == 15) markSectionDirtyAt(pos.south());
    }

    private void markSectionDirty(SectionPos sPos) {
        if (dirtySections.add(sPos)) {
            mesher.cancelSection(sPos);
        }
    }

    /**
     * Dirty the section containing pos, ignoring positions outside the capture.
     */
    private void markSectionDirtyAt(BlockPos pos) {
        if (!chunks.contains(ChunkPos.containing(pos))) return;
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        if (sectionY < minY || sectionY >= minY + height) return;
        markSectionDirty(SectionPos.of(pos));
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

    public Map<BlockPos, Int2ObjectSortedMap<BlockUpdate>> getBlockUpdates() {
        return Collections.unmodifiableMap(blockUpdates);
    }

    /// === QUERYING ===

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
        if (!chunks.contains(ChunkPos.containing(pos))) {
            return Blocks.AIR.defaultBlockState();
        }

        Int2ObjectSortedMap<BlockUpdate> updateMap = blockUpdates.get(pos);
        if (updateMap != null) {
            var headMap = updateMap.headMap(tick + 1);
            if (!headMap.isEmpty()) {
                return headMap.get(headMap.lastIntKey()).newBlock();
            }

        }

        // Fallback to base
        ChunkPos cPos = ChunkPos.containing(pos);
        SimpleSectionColumn col = copiedBaseWorld.get(cPos);
        if (col != null) {
            return col.getBlockState(
                    SectionPos.sectionRelative(pos.getX()), pos.getY(), SectionPos.sectionRelative(pos.getZ()));
        }
        return Blocks.AIR.defaultBlockState();
    }

    /// === SECTION ITERATION HELPER ===

    private static class SectionIterator extends AbstractIterator<SectionPos> {

        final Iterator<ChunkPos> chunkIterator;
        final int minY;
        final int height;

        int curY;
        @Nullable ChunkPos curChunk;

        private SectionIterator(Iterator<ChunkPos> chunkIterator, int minY, int height) {
            if (height <= 0) throw new IllegalArgumentException("height <= 0");
            this.chunkIterator = chunkIterator;
            this.minY = minY;
            this.height = height;
            curY = minY;
        }

        @Override
        protected @Nullable SectionPos computeNext() {
            if (curChunk == null || curY >= minY + height) {
                if (chunkIterator.hasNext()) {
                    curChunk = chunkIterator.next();
                    curY = minY;
                } else {
                    return endOfData();
                }
            }
            return SectionPos.of(curChunk, curY++);
        }
    }
}