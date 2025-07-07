package com.igrium.worldexport.world;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.function.Supplier;

/**
 * A simplified, vertical column of chunk sections without all the overhead of a world chunk.
 */
public class SimpleSectionColumn implements HeightLimitView {
    /**
     * The section y coordinate of the bottom-most section.
     */
    private final int lowerSectionY;

    /**
     * All the sections in this column, in order.
     */
    private final ChunkSection[] sections;

    public SimpleSectionColumn(int lowerSectionY, int numSections, Registry<Biome> biomeRegistry) {
        this(lowerSectionY, numSections);
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new ChunkSection(biomeRegistry);
        }
    }

    private SimpleSectionColumn(int lowerSectionY, int numSections) {
        this.lowerSectionY = lowerSectionY;

        sections = new ChunkSection[numSections];
    }

    /**
     * Get the section at a given index.
     * @param yIndex 0-based Y index to get.
     * @return A reference to the section.
     * @throws IndexOutOfBoundsException If the index is out of range for this column.
     */
    public ChunkSection getSection(int yIndex) throws IndexOutOfBoundsException {
        return sections[yIndex];
    }

    /**
     * Get the block at a specific coordinate (relative to the chunk)
     * @param x Local X coord.
     * @param y Local Y coord.
     * @param z Local Z coord.
     * @return The block.
     * @throws IndexOutOfBoundsException If the supplied coordinates are out of range.
     */
    public BlockState getBlockState(int x, int y, int z) throws IndexOutOfBoundsException {
        if (x < 0 || x >= 16) {
            throw new IndexOutOfBoundsException("X position " + x + " out of bounds for chunk.");
        }
        if (z < 0 || z >= 16) {
            throw new IndexOutOfBoundsException("Z position " + z + " out of bounds for chunk.");
        }
        int localY = ChunkSectionPos.getLocalCoord(y);
        return getSection(ChunkSectionPos.getSectionCoord(y)).getBlockState(x, localY, z);
    }

    /**
     * Get the biome at a specific coordinate (relative to the chunk)
     * @param x Local X coord.
     * @param y Local Y coord.
     * @param z Local Z coord.
     * @return The block.
     * @throws IndexOutOfBoundsException If the supplied coordinates are out of range.
     */
    public RegistryEntry<Biome> getBiome(int x, int y, int z) throws IndexOutOfBoundsException {
        if (x < 0 || x >= 16) {
            throw new IndexOutOfBoundsException("X position " + x + " out of bounds for chunk.");
        }
        if (z < 0 || z >= 16) {
            throw new IndexOutOfBoundsException("Z position " + z + " out of bounds for chunk.");
        }
        int localY = ChunkSectionPos.getLocalCoord(y);
        return getSection(ChunkSectionPos.getSectionCoord(y)).getBiome(x, localY, z);
    }

    @Override
    public int getHeight() {
        return sections.length * 16;
    }

    @Override
    public int getBottomY() {
        return lowerSectionY * 16;
    }

    @Override
    public int countVerticalSections() {
        return sections.length;
    }

    @Override
    public int getBottomSectionCoord() {
        return lowerSectionY;
    }

    @Override
    public int getTopSectionCoord() {
        return lowerSectionY + sections.length;
    }

    /**
     * Duplicate this column and all the sections in it.
     * @return Deep copy of the column.
     */
    public SimpleSectionColumn copy() {
        SimpleSectionColumn col = new SimpleSectionColumn(lowerSectionY, sections.length);
        for (int i = 0; i < sections.length; i++) {
            col.sections[i] = this.sections[i].copy();
        }
        return col;
    }

    /**
     * Generate a section column based on the sections in a chunk.
     * @param chunk Chunk to get sections from.
     * @return Generated column with copies of all the chunk sections.
     */
    public static SimpleSectionColumn fromChunk(Chunk chunk) {
        SimpleSectionColumn col = new SimpleSectionColumn(chunk.getBottomSectionCoord(), chunk.countVerticalSections());
        for (int i = 0; i < col.sections.length; i++) {
            assert chunk.getSection(i) != null;
            col.sections[i] = chunk.getSection(i).copy();
        }
        return col;
    }

    /**
     * Generate a section column based on a selection of sections in a chunk.
     *
     * @param chunk          Chunk to get sections from.
     * @param lowerSectionY  Minimum section Y value.
     * @param height         Height of the column.
     * @param biomeRegistry  Biome registry to use if new sections need to be generated.
     * @return The generated column with copies of all the chunk sections.
     */
    public static SimpleSectionColumn fromChunk(Chunk chunk, int lowerSectionY, int height, Registry<Biome> biomeRegistry) {
        SimpleSectionColumn col = new SimpleSectionColumn(lowerSectionY, height);

        int chunkIndexOffset = chunk.sectionCoordToIndex(lowerSectionY); // Index 0 in col is this index in chunk.
        int chunkStartIndex = Math.max(0, chunkIndexOffset); // Col index where chunk data starts
        int chunkEndIndex = Math.min(height, chunk.countVerticalSections() + chunkIndexOffset); // Col index where chunk data ends

        for (int i = 0; i < height; i++) {
            if (chunkStartIndex <= i && i < chunkEndIndex) {
                col.sections[i] = chunk.getSection(i + chunkIndexOffset).copy();
            } else {
                col.sections[i] = new ChunkSection(biomeRegistry);
            }
        }

        return col;
    }

}
