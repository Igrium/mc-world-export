package com.igrium.worldexport.math;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Represents a 3D box in chunk section coordinates.
 *
 * @author Igrium
 */
public record ChunkSectionBox(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {

    public static final ChunkSectionBox ZERO = new ChunkSectionBox(0, 0, 0, 0, 0, 0);

    /**
     * Constructs a {@code ChunkSectionBox} with the given minimum position and size.
     *
     * @param min  The minimum position of the box as a {@link ChunkSectionPos}.
     * @param size The size of the box as a {@link ChunkSectionPos}.
     */
    public ChunkSectionBox(ChunkSectionPos min, ChunkSectionPos size) {
        this(min.getX(), min.getY(), min.getZ(), size.getX(), size.getY(), size.getZ());
    }

    /**
     * Returns the minimum position of the box as a {@link ChunkSectionPos}.
     *
     * @return The minimum corner of the box.
     */
    public ChunkSectionPos minPos() {
        return ChunkSectionPos.from(minX, minY, minZ);
    }

    /**
     * Returns the size of the box as a {@link ChunkSectionPos}.
     *
     * @return The size of the box in chunk sections.
     */
    public ChunkSectionPos size() {
        return ChunkSectionPos.from(sizeX, sizeY, sizeZ);
    }

    /**
     * Returns the number of sections in the box.
     * @return Number of sections in the box.
     */
    public int count() {
        return sizeX * sizeY * sizeZ;
    }

    /**
     * Returns the maximum X coordinate (exclusive) of the box.
     *
     * @return The maximum exclusive X coordinate.
     */
    public int maxX() {
        return minX + sizeX;
    }

    /**
     * Returns the maximum Y coordinate (exclusive) of the box.
     *
     * @return The maximum exclusive Y coordinate.
     */
    public int maxY() {
        return minY + sizeY;
    }

    /**
     * Returns the maximum Z coordinate (exclusive) of the box.
     *
     * @return The maximum exclusive Z coordinate.
     */
    public int maxZ() {
        return minZ + sizeZ;
    }

    /**
     * Returns the maximum position (exclusive) of the box as a {@link ChunkSectionPos}.
     *
     * @return The exclusive maximum corner of the box.
     */
    public ChunkSectionPos maxPos() {
        return ChunkSectionPos.from(maxX(), maxY(), maxZ());
    }

    /**
     * Returns the maximum X coordinate (inclusive) of the box.
     *
     * @return The maximum inclusive X coordinate.
     */
    public int maxXInclusive() {
        return minX + sizeX - 1;
    }

    /**
     * Returns the maximum Y coordinate (inclusive) of the box.
     *
     * @return The maximum inclusive Y coordinate.
     */
    public int maxYInclusive() {
        return minY + sizeY - 1;
    }

    /**
     * Returns the maximum Z coordinate (inclusive) of the box.
     *
     * @return The maximum inclusive Z coordinate.
     */
    public int maxZInclusive() {
        return minZ + sizeZ - 1;
    }

    /**
     * Returns the maximum position (inclusive) of the box as a {@link ChunkSectionPos}.
     *
     * @return The inclusive maximum corner of the box.
     */
    public ChunkSectionPos maxPosInclusive() {
        return ChunkSectionPos.from(maxXInclusive(), maxYInclusive(), maxZInclusive());
    }

    /**
     * Checks if the given chunk section coordinates are within the bounds of this box.
     *
     * @param x The X coordinate to check.
     * @param y The Y coordinate to check.
     * @param z The Z coordinate to check.
     * @return {@code true} if the coordinates are inside the box (inclusive lower bound, exclusive upper bound), {@code false} otherwise.
     */
    public boolean isInBounds(int x, int y, int z) {
        return minX <= x && x < maxX()
                && minY < y && y < maxY()
                && minZ < z && z < maxZ();
    }

    /**
     * Checks if the given {@link ChunkSectionPos} is within the bounds of this box.
     *
     * @param pos The position to check.
     * @return {@code true} if the position is inside the box (inclusive lower bound, exclusive upper bound), {@code false} otherwise.
     */
    public boolean isInBounds(ChunkSectionPos pos) {
        return isInBounds(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Check if the given chunk coordinates overlap with this box.
     * @param x The X coordinate to check.
     * @param z The Z coordinate to check.
     * @return {@code true} if the coordinates overlap the box (inclusive lower bound, exclusive upper bound), {@code false} otherwise.
     */
    public boolean isInBounds(int x, int z) {
        return minX <= x && x < maxX()
                && minZ <= z && z < maxZ();
    }

    /**
     * Check if the given chunk coordinates overlap with this box.
     * @param pos The position to check.
     * @return {@code true} if the coordinates overlap the box (inclusive lower bound, exclusive upper bound), {@code false} otherwise.
     */
    public boolean isInBounds(ChunkPos pos) {
        return isInBounds(pos.x, pos.z);
    }

    /**
     * Iterate over all the section positions within this box.
     * @return An iterable
     */
    public Iterable<ChunkSectionPos> iterate() {
        return ChunkSections.iterate(minX, minY, minZ, maxXInclusive(), maxYInclusive(), maxZInclusive());
    }

    /**
     * Creates a {@code ChunkSectionBox} from two corners (inclusive).
     * The resulting box includes both (x1, y1, z1) and (x2, y2, z2).
     *
     * @param x1 The X coordinate of the first corner.
     * @param y1 The Y coordinate of the first corner.
     * @param z1 The Z coordinate of the first corner.
     * @param x2 The X coordinate of the second corner.
     * @param y2 The Y coordinate of the second corner.
     * @param z2 The Z coordinate of the second corner.
     * @return A new {@code ChunkSectionBox} that spans the two corners.
     */
    public static ChunkSectionBox from(int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);

        int maxX = Math.max(x1, x2) + 1;
        int maxY = Math.max(y1, y2) + 1;
        int maxZ = Math.max(z1, z2) + 1;

        return new ChunkSectionBox(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ);
    }

    /**
     * Creates a {@code ChunkSectionBox} from two {@link ChunkSectionPos} corners (inclusive).
     * The resulting box includes both positions.
     *
     * @param pos1 The first corner position.
     * @param pos2 The second corner position.
     * @return A new {@code ChunkSectionBox} that spans the two positions.
     */
    public static ChunkSectionBox from(ChunkSectionPos pos1, ChunkSectionPos pos2) {
        return from(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
    }

    public static ChunkSectionBox fromRadius(int centerX, int centerY, int centerZ, int radius) {
        return new ChunkSectionBox(centerX - radius, centerY - radius, centerZ - radius, radius * 2, radius * 2, radius * 2);
    }

    public static ChunkSectionBox fromRadius(ChunkSectionPos center, int radius) {
        return fromRadius(center.getX(), center.getY(), center.getZ(), radius);
    }
}