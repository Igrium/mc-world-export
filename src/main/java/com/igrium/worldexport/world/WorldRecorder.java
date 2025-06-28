package com.igrium.worldexport.world;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Records a world block updates.
 */
public class WorldRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldRecorder.class);

    @Getter
    private final World world;

    @Getter
    private final WorldCapture worldCapture;

    @Getter
    @Setter
    int currentTick;

    public WorldRecorder(World world, WorldCapture worldCapture) {
        this.world = world;
        this.worldCapture = worldCapture;
    }

    public WorldRecorder(World world, ChunkSectionPos minPos, ChunkSectionPos maxPos) {
        this(world, new WorldCapture(minPos, maxPos));
    }

    public void nextTick() {
        currentTick++;
    }

    /**
     * Query the world and get a section.
     * @param pos Section position to get.
     * @return The world section.
     */
    public @Nullable ChunkSection getSection(ChunkSectionPos pos) {
        WorldChunk chunk = world.getChunk(pos.getX(), pos.getZ());
        if (chunk == null)
            return null;

        if (chunk.getBottomSectionCoord() < pos.getY() || pos.getY() > chunk.getTopSectionCoord())
            return null;

        return chunk.getSection(pos.getSectionY());
    }

    /**
     * Capture the initial block world. Should be called at the beginning of the capture.
     * @implNote Expensive operation. Don't call every frame.
     */
    public void captureInitialWorld() {
        long startTime = Util.getMeasuringTimeMs();

        Map<ChunkSectionPos, ChunkSection> sections = findChunkSections();

        for (var entry : sections.entrySet()) {
            worldCapture.getBaseSections().put(entry.getKey(), entry.getValue().getBlockStateContainer().copy());
        }

        long elapsed = Util.getMeasuringTimeMs() - startTime;
        LOGGER.info("Initial world capture took {}ms", elapsed);
    }

    /**
     * Called when a section has been (re)loaded during the course of the recording
     * @param pos Position of the section
     * @param section The section that was loaded.
     */
    public void onLoadSection(ChunkSectionPos pos, ChunkSection section) {
        if (section == null) {
            LOGGER.warn("Attempted to load null section at {}", pos);
            return;
        }
        PalettedContainer<BlockState> blocks = section.getBlockStateContainer().copy();
        // No need to copy because biomes don't change.
        ReadableContainer<RegistryEntry<Biome>> biomes = section.getBiomeContainer();

        if (worldCapture.getBaseSections().putIfAbsent(pos, blocks) != null) {
            // There's already a section in the base.
            worldCapture.addSectionKeyframe(pos, currentTick, blocks);
        }
        worldCapture.getBiomeSections().put(pos, biomes);
    }

    /**
     * Called when a chunk has been (re)loaded during the course of the recording.
     * @param pos Position of the chunk.
     * @param chunk The chunk that was loaded.
     */
    public void onLoadChunk(ChunkPos pos, WorldChunk chunk) {
        if (chunk == null) {
            LOGGER.warn("Attempted to load null chunk at {}", pos);
            return;
        }
        for (int y = chunk.getBottomSectionCoord(); y <= chunk.getTopSectionCoord(); y++) {
            onLoadSection(ChunkSectionPos.from(pos, y), chunk.getSection(chunk.getSectionIndex(y)));
        }
    }

    /**
     * Called whenever a block has updated and we're recording.
     * @param pos Global position of the block.
     * @param oldBlock Previous block.
     * @param newBlock New block.
     */
    public void onUpdateBlock(BlockPos pos, @Nullable BlockState oldBlock, BlockState newBlock) {
        worldCapture.addBlockKeyframe(pos, currentTick, oldBlock, newBlock);
    }

    /**
     * Find all chunk sections in the world within the bounds.
     * @param target Map to add to.
     */
    public void findChunkSections(Map<ChunkSectionPos, ChunkSection> target) {

        ChunkSectionPos boundsMin = worldCapture.getBoundsMin();
        ChunkSectionPos boundsMax = worldCapture.getBoundsMax();

        int maxSections = (boundsMax.getX() + 1 - boundsMin.getX())
                * (boundsMax.getY() + 1 - boundsMin.getY())
                * (boundsMax.getY() + 1 - boundsMin.getZ());

        Map<ChunkSectionPos, ChunkSection> sections = new HashMap<>(maxSections);

        for (int x = boundsMin.getX(); x <= boundsMax.getX(); x++) {
            for (int z = boundsMin.getZ(); z <= boundsMax.getZ(); z++) {
                WorldChunk chunk = world.getChunk(x, z);
                if (chunk == null)
                    continue;

                for (int y = boundsMin.getY(); y <= boundsMax.getY(); y++) {
                    if (y < chunk.getBottomSectionCoord() || y > chunk.getTopSectionCoord())
                        continue;

                    ChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    if (section != null) {
                        target.put(ChunkSectionPos.from(x, y, z), section);
                    }
                }
            }
        }

    }

    /**
     * Find all chunk sections in the world within the bounds.
     * @return All chunk sections.
     */
    public Map<ChunkSectionPos, ChunkSection> findChunkSections() {
        ChunkSectionPos boundsMin = worldCapture.getBoundsMin();
        ChunkSectionPos boundsMax = worldCapture.getBoundsMax();

        int maxSections = boundsMax.getX() + 1 - boundsMin.getX()
                * boundsMax.getY() + 1 - boundsMin.getY()
                * boundsMax.getY() + 1 - boundsMin.getZ();

        Map<ChunkSectionPos, ChunkSection> sections = new HashMap<>(maxSections);
        findChunkSections(sections);
        return sections;
    }
}
