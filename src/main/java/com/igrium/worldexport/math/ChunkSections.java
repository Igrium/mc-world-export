package com.igrium.worldexport.math;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

public class ChunkSections {
    public static Iterable<SectionPos> iterate(SectionPos start, SectionPos end) {
        return iterate(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ());
    }

    public static Iterable<ChunkPos> iterate(ChunkPos start, ChunkPos end) {
        return iterate(start.x(), start.z(), end.x(), end.z());
    }

    public static Set<ChunkPos> getSet(int startX, int startZ, int endX, int endZ) {
        int xSize = endX - startX + 1;
        int zSize = endZ - startZ + 1;
        Set<ChunkPos> set = new HashSet<>(xSize * zSize);
        for (var pos : iterate(startX, startZ, endX, endZ)) {
            set.add(pos);
        }
        return set;
    }

    public static Set<SectionPos> getSet(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        int xSize = endX - startX + 1;
        int ySize = endY - startY + 1;
        int zSize = endZ - startZ + 1;

        Set<SectionPos> set = new HashSet<>(xSize * ySize * zSize);
        for (var pos : iterate(startX, startY, startZ, endX, endY, endZ)) {
            set.add(pos);
        }
        return set;
    }

    public static Iterable<ChunkPos> iterate(int startX, int startZ, int endX, int endZ) {
        int xSize = endX - startX + 1;
        int zSize = endZ - startZ + 1;

        int totalPositions = xSize * zSize;

        return () -> new AbstractIterator<>() {
            private int currentIndex;

            @Override
            protected ChunkPos computeNext() {
                if (currentIndex == totalPositions) {
                    return endOfData();
                } else {
                    int dx = this.currentIndex % xSize;
                    int dz = this.currentIndex / xSize;
                    this.currentIndex++;
                    return new ChunkPos(startX + dx, startZ + dz);
                }
            }
        };
    }

    public static Iterable<SectionPos> iterate(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        int xSize = endX - startX + 1;
        int ySize = endY - startY + 1;
        int zSize = endZ - startZ + 1;

        int totalPositions = xSize * ySize * zSize;

        return () -> new AbstractIterator<>() {
            private int currentIndex;

            @Override
            protected SectionPos computeNext() {
                if (currentIndex == totalPositions) {
                    return endOfData();
                } else {
                    int dx = this.currentIndex % xSize;
                    int dyIndex = this.currentIndex / xSize;
                    int dy = dyIndex % ySize;
                    int dz = dyIndex / ySize;
                    this.currentIndex++;
                    return SectionPos.of(startX + dx, startY + dy, startZ + dz);
                }
            }
        };
    }
}
