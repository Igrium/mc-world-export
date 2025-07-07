package com.igrium.worldexport.util;

import com.igrium.worldexport.world.SimpleSectionColumn;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Compares two different chunk sections and identifies differences between the,
 */
public class ChunkDiffs {
    // TODO: Can we abuse paletted containers to optimize this?

    /**
     * An updated block between two chunk sections.
     *
     * @param firstVal  The value in the first section.
     * @param secondVal The value in the second section.
     * @param x         Local X coordinate.
     * @param y         Local Y coordinate.
     * @param z         Local Z coordinate.
     * @param <T>       Palette item type.
     */
    public record Diff<T>(T firstVal, T secondVal, int x, int y, int z) {}

    /**
     * Identify the differences between two chunk sections.
     *
     * @param first  The first section's block data.
     * @param second The second section's block data.
     * @param diffConsumer Called for every diff found.
     * @param <T>    Palette item type.
     */
    public static <T> void diff(PalettedContainer<? extends T> first, PalettedContainer<? extends T> second, Consumer<Diff<T>> diffConsumer) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    T firstVal = first.get(x, y, z);
                    T secondVal = second.get(x, y, z);
                    if (!Objects.equals(firstVal, secondVal)) {
                        diffConsumer.accept(new Diff<>(firstVal, secondVal, x, y, z));
                    }
                }
            }
        }
    }

    /**
     * Identify the differences between two chunk sections.
     *
     * @param first  The first section's block data.
     * @param second The second section's block data.
     * @param <T>    Palette item type.
     * @return A list of all diffs found.
     */
    public static <T> List<Diff<T>> diff(PalettedContainer<? extends T> first, PalettedContainer<? extends T> second) {
        List<Diff<T>> list = new ArrayList<>();
        diff(first, second, list::add);
        return list;
    }

    /**
     * Identify the differences between two section columns.
     * @param first The first column.
     * @param second The second column.
     * @param diffConsumer Called for every diff found.
     */
    public static void diff(SimpleSectionColumn first, SimpleSectionColumn second, Consumer<Diff<BlockState>> diffConsumer) {
        if (first.countVerticalSections() != second.countVerticalSections()) {
            throw new IllegalArgumentException("Both columns should have the same number of sections (and should start on the same Y index)");
        }


        for (int i = 0; i < first.countVerticalSections(); i++) {
            ChunkSection sec1 = first.getSection(i);
            ChunkSection sec2 = second.getSection(i);

            int sectionY = first.sectionIndexToCoord(i);

            diff(sec1.getBlockStateContainer(), sec2.getBlockStateContainer(), diff ->
                    diffConsumer.accept(new Diff<>(diff.firstVal, diff.secondVal, diff.x, diff.y + sectionY, diff.z)));
        }
    }

    /**
     * Identify the differences between two section columns.
     * @param first The first column.
     * @param second The second column.
     * @return A list of all diffs found.
     */
    public static List<Diff<BlockState>> diff(SimpleSectionColumn first, SimpleSectionColumn second) {
        List<Diff<BlockState>> list = new ArrayList<>();
        diff(first, second, list::add);
        return list;
    }
}
