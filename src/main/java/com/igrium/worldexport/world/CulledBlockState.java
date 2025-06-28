package com.igrium.worldexport.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/**
 * A blockstate that also contains culling information.
 */
public record CulledBlockState(BlockState blockState, boolean down, boolean up, boolean north, boolean south, boolean west, boolean east) {

    /**
     * Return a CulledBlockState where every face is visible.
     * @param blockState Block state to use.
     */
    public static CulledBlockState full(BlockState blockState) {
        return new CulledBlockState(blockState, true, true, true, true, true, true);
    }

    /**
     * Return a CulledBlockState where every face is occluded.
     * @param blockState Block state to use.
     */
    public static CulledBlockState occluded(BlockState blockState) {
        return new CulledBlockState(blockState, false, false, false, false, false, false);
    }

    /**
     * Check if a given face should be rendered.
     * @param direction Direction of the face.
     * @return If it should be rendered.
     */
    public boolean isFaceExposed(Direction direction) {
        return switch (direction) {
            case DOWN -> down;
            case UP -> up;
            case NORTH -> north;
            case SOUTH -> south;
            case WEST -> west;
            case EAST -> east;
        };
    }

    /**
     * Return a new CulledBlockState with the base blockstate replaced.
     * @param blockState New block state.
     */
    public CulledBlockState withBlockState(BlockState blockState) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }

    /**
     * Return a new CulledBlockState with a modified visibility for a given face.
     * @param direction Direction of the face.
     * @param exposed If it should be rendered.
     */
    public CulledBlockState withFace(Direction direction, boolean exposed) {
        return new CulledBlockState(blockState,
                direction == Direction.DOWN ? exposed : down,
                direction == Direction.UP ? exposed : up,
                direction == Direction.NORTH ? exposed : north,
                direction == Direction.SOUTH ? exposed : south,
                direction == Direction.WEST ? exposed : west,
                direction == Direction.EAST ? exposed : east);
    }

    public CulledBlockState withDown(boolean down) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }

    public CulledBlockState withUp(boolean up) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }

    public CulledBlockState withNorth(boolean north) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }

    public CulledBlockState withSouth(boolean south) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }

    public CulledBlockState withWest(boolean west) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }

    public CulledBlockState withEast(boolean east) {
        return new CulledBlockState(blockState, down, up, north, south, west, east);
    }
}
