package com.igrium.worldexport.world;

import com.igrium.worldexport.IgriumsReplayExporter;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures block updates within a Minecraft world.
 */
public class WorldCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldCapture.class);

    /**
     * If a block gets updated while the world is being captured, we need to save the original version of the chunk.
     */
    private final Map<ChunkSectionPos, PalettedContainer<BlockState>> baseChunkCache = new ConcurrentHashMap<>();

    /**
     * Get the world capture instance that's currently capturing.
     * Shortcut for <code>IgriumsReplayExporter.getInstance().getCurrentWorldCapture();</code>
     * @return Current capture instance, if any.
     */
    public static @Nullable WorldCapture getCurrent() {
        return IgriumsReplayExporter.getInstance().getCurrentWorldCapture();
    }

    /**
     * The world being captured.
     */
    @Getter
    private final World world;

    @Getter
    private final ChunkSectionPos boundsMin;

    @Getter
    private final ChunkSectionPos boundsMax;

    @Getter
    private boolean started;


    /**
     * Create a WorldCapture instance.
     *
     * @param world     The world to capture.
     * @param boundsMin The negative-most section coordinate to export.
     * @param boundsMax The positive-most section coordinate to export.
     */
    public WorldCapture(World world, ChunkSectionPos boundsMin, ChunkSectionPos boundsMax) {
        this.world = world;
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
    }

    public void captureAllChunks(ChunkPos minChunk, ChunkPos maxChunk) {
        for (int x = minChunk.x; x <= maxChunk.x; x++) {
            for (int z = minChunk.z; z <= maxChunk.z; z++) {
                for (int y = world.getBottomSectionCoord(); y <= world.getTopSectionCoord(); y++) {
                    ChunkSectionPos pos = ChunkSectionPos.from(x, y, z);
                    copyBaseSection(pos);
                }
            }
        }
    }

    public void beforeSetBlock(BlockPos pos, BlockState newState) {
        ChunkSectionPos cPos = ChunkSectionPos.from(pos);
        copyBaseSection(cPos);
    }

    /**
     * Get or create a unique copy of a base section. Should be called before any meshing or block updates.
     *
     * @param cPos Chunk section pos to use.
     */
    private void copyBaseSection(ChunkSectionPos cPos) {
        WorldChunk chunk = world.getChunk(cPos.getX(), cPos.getZ());
        if (chunk == null)
            return;
        baseChunkCache.computeIfAbsent(cPos, p -> {
            ChunkSection section = chunk.getSection(chunk.getSectionIndex(cPos.getSectionY()));
            return section.getBlockStateContainer().copy();
        });
    }
}
