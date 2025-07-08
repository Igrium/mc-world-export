package com.igrium.worldexport.math;

import com.google.common.collect.AbstractIterator;
import net.minecraft.util.math.ChunkSectionPos;

public class ChunkSections {
    public static Iterable<ChunkSectionPos> iterate(ChunkSectionPos start, ChunkSectionPos end) {
        return iterate(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ());
    }

    public static Iterable<ChunkSectionPos> iterate(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        int xSize = endX - startX + 1;
        int ySize = endY - startY + 1;
        int zSize = endZ - startZ + 1;

        int totalPositions = xSize * ySize * zSize;

        return () -> new AbstractIterator<ChunkSectionPos>() {
            private int currentIndex;

            @Override
            protected ChunkSectionPos computeNext() {
                if (currentIndex == totalPositions) {
                    return endOfData();
                } else {
                    int dx = this.currentIndex % xSize;
                    int dyIndex = this.currentIndex / xSize;
                    int dy = dyIndex % ySize;
                    int dz = dyIndex / ySize;
                    this.currentIndex++;
                    return ChunkSectionPos.from(startX + dx, startY + dy, startZ + dz);
                }
            }
        };
    }
}
