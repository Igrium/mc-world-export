package com.igrium.worldexport.world;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * A simplified, vertical column of chunk sections without all the overhead of a world chunk.
 */
public class SimpleSectionColumn implements LevelHeightAccessor {
    /**
     * The section y coordinate of the bottom-most section.
     */
    private final int lowerSectionY;

    /**
     * All the sections in this column, in order.
     */
    private final LevelChunkSection[] sections;

    public SimpleSectionColumn(int lowerSectionY, int numSections, Registry<Biome> biomeRegistry) {
        this(lowerSectionY, numSections);
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new LevelChunkSection(biomeRegistry);
        }
    }

    private SimpleSectionColumn(int lowerSectionY, int numSections) {
        this.lowerSectionY = lowerSectionY;

        sections = new LevelChunkSection[numSections];
    }

    /**
     * Get the section at a given index.
     * @param yIndex 0-based Y index to get.
     * @return A reference to the section.
     * @throws IndexOutOfBoundsException If the index is out of range for this column.
     */
    public LevelChunkSection getSection(int yIndex) throws IndexOutOfBoundsException {
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
            return Blocks.AIR.defaultBlockState();
        }
        if (z < 0 || z >= 16) {
            return Blocks.AIR.defaultBlockState();
        }
        int yIndex = getSectionIndexFromSectionY(SectionPos.blockToSectionCoord(y));
        if (yIndex < 0 || yIndex >= sections.length)
            return Blocks.AIR.defaultBlockState();

        int localY = SectionPos.sectionRelative(y);
        return sections[yIndex].getBlockState(x, localY, z);
    }

    /**
     * Get the biome at a specific coordinate (relative to the chunk)
     * @param x Local X coord.
     * @param y Local Y coord.
     * @param z Local Z coord.
     * @return The block.
     * @throws IndexOutOfBoundsException If the supplied coordinates are out of range.
     */
    public Holder<Biome> getBiome(int x, int y, int z) throws IndexOutOfBoundsException {
        if (x < 0 || x >= 16) {
            throw new IndexOutOfBoundsException("X position " + x + " out of bounds for chunk.");
        }
        if (z < 0 || z >= 16) {
            throw new IndexOutOfBoundsException("Z position " + z + " out of bounds for chunk.");
        }
        int localY = SectionPos.sectionRelative(y);
        return getSection(getSectionIndex(y)).getNoiseBiome(x, localY, z);
    }

    @Override
    public int getHeight() {
        return sections.length * 16;
    }

    @Override
    public int getMinY() {
        return lowerSectionY * 16;
    }

    @Override
    public int getSectionsCount() {
        return sections.length;
    }

    @Override
    public int getMinSectionY() {
        return lowerSectionY;
    }

    @Override
    public int getMaxSectionY() {
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
    public static SimpleSectionColumn fromChunk(ChunkAccess chunk) {
        SimpleSectionColumn col = new SimpleSectionColumn(chunk.getMinSectionY(), chunk.getSectionsCount());
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
    public static SimpleSectionColumn fromChunk(ChunkAccess chunk, int lowerSectionY, int height, Registry<Biome> biomeRegistry) {
        SimpleSectionColumn col = new SimpleSectionColumn(lowerSectionY, height);

        for (int i = 0; i < height; i++) {
            int sectionY = lowerSectionY + i;
            int chunkSectionIndex = chunk.getSectionIndexFromSectionY(sectionY);

            if (chunkSectionIndex >= 0 && chunkSectionIndex < chunk.getSectionsCount()) {
                LevelChunkSection section = chunk.getSection(chunkSectionIndex);
                // If chunk.getSection can return null, add a null check here
                col.sections[i] = section != null ? section.copy() : new LevelChunkSection(biomeRegistry);
            } else {
                col.sections[i] = new LevelChunkSection(biomeRegistry);
            }
        }

        return col;
    }

}
