package com.igrium.worldexport.v1.world;

import lombok.Getter;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.HeightLimitView;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A world that is nothing more than a collection sections.
 * Fast to copy from actual world, and is used for base sections in WorldCapture.
 * @implSpec All functions here are thread-safe.
 */
public class SimpleSectionWorld<T> implements HeightLimitView {

    private final int lowerY;
    private final int numSections;

    /**
     * The actual world data.
     */
    @Getter
    private final Map<ChunkPos, SimpleSectionColumn<T>> chunks = new ConcurrentHashMap<>();

    public SimpleSectionWorld(int lowerY, int numSections) {
        this.lowerY = lowerY;
        this.numSections = numSections;
    }

    /**
     * Get the section at a given position.
     * @param pos Position of the section to get.
     * @return The section, or <code>null</code> if there is none at that position.
     */
    public @Nullable T getSection(ChunkSectionPos pos) {
        if (!isSectionInRange(pos.getY()))
            return null;
        var col = chunks.get(pos.toChunkPos());
        return col != null ? col.getSection(col.sectionCoordToIndex(pos.getY())) : null;
    }

    public T getOrCreateSection(ChunkSectionPos pos, Supplier<T> factory) throws IndexOutOfBoundsException {
        int index = getIndex(pos.getY());

        var col = chunks.computeIfAbsent(pos.toChunkPos(), p -> new SimpleSectionColumn<>(lowerY, numSections));
        return col.getOrCreate(index, factory);
    }

    public T putSection(ChunkSectionPos pos, T section) throws IndexOutOfBoundsException {
        int index = getIndex(pos.getY());

        var col = chunks.computeIfAbsent(pos.toChunkPos(), p -> new SimpleSectionColumn<>(lowerY, numSections));
        return col.putSection(index, section);
    }

    public boolean putSectionIfAbsent(ChunkSectionPos pos, T section) {
        int index = getIndex(pos.getY());

        var col = chunks.computeIfAbsent(pos.toChunkPos(), p -> new SimpleSectionColumn<>(lowerY, numSections));
        return col.putIfAbsent(index, section);
    }

    @Override
    public int getHeight() {
        return numSections * 16;
    }

    @Override
    public int countVerticalSections() {
        return numSections;
    }

    @Override
    public int getBottomY() {
        return lowerY * 16;
    }

    @Override
    public int getBottomSectionCoord() {
        return lowerY;
    }

    @Override
    public int getTopSectionCoord() {
        return lowerY + numSections;
    }

    public boolean isSectionInRange(int sectionY) {
        int index = sectionY - lowerY;
        return 0 <= index && index < numSections;
    }

    private int getIndex(int y) throws IndexOutOfBoundsException {
        int index = y - lowerY;
        if (index < 0 || index >= numSections) {
            throw new IndexOutOfBoundsException(y);
        }
        return index;
    }

    public void forEachSection(BiConsumer<ChunkSectionPos, T> sectionConsumer) {
        for (var entry : chunks.entrySet()) {
            var cPos = entry.getKey();
            var chunk = entry.getValue();

            for (int i = 0; i < chunk.getNumSections(); i++) {
                T section = chunk.getSection(i);
                if (section != null) {
                    ChunkSectionPos sPos = ChunkSectionPos.from(cPos, chunk.sectionIndexToCoord(i));
                    sectionConsumer.accept(sPos, section);
                }
            }
        }
    }

    public int countChunks() {
        return chunks.size();
    }
}
