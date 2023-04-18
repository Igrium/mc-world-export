package org.scaffoldeditor.worldexport.util;

import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * A two-dimensional bounding box made of integers.
 */
public class Box2i {
    public static enum Corner {
        NORTH_WEST(false, false),
        NORTH_EAST(true, false),
        SOUTH_EAST(true, true),
        SOUTH_WEST(false, true);

        private Corner(boolean xMax, boolean yMax) {
            this.xMax = xMax;
            this.yMax = yMax;
        }
        
        static {
            NORTH_WEST.opposite = SOUTH_EAST;
            SOUTH_EAST.opposite = NORTH_WEST;

            NORTH_EAST.opposite = SOUTH_WEST;
            SOUTH_WEST.opposite = NORTH_EAST;
        }

        private final boolean xMax;
        private final boolean yMax;
        private Corner opposite;

        public boolean isXMax() {
            return xMax;
        }

        public boolean isYMax() {
            return yMax;
        }

        public Corner opposite() {
            return opposite;
        }

        public static Corner get(boolean xMax, boolean yMax) {
            for (Corner corner : Corner.values()) {
                if (corner.xMax == xMax && corner.yMax == yMax) {
                    return corner;
                }
            }
            throw new RuntimeException("This should never happen.");
        }
    }

    private int x1 = 0;
    private int y1 = 0;
    private int x2 = 0;
    private int y2 = 0;

    public Box2i() {}

    public Box2i(int x1, int y1, int x2, int y2) {
        set(x1, y1, x2, y2);
    }

    public int getX1() {
        return x1;
    }

    public int getY1() {
        return y1;
    }

    public int getX2() {
        return x2;
    }

    public int getY2() {
        return y2;
    }

    public Box2i set(int x1, int y1, int x2, int y2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        return this;
    }
    
    public Box2i set(Vector2ic point1, Vector2ic point2) {
        this.set(point1.x(), point1.y(), point2.x(), point2.y());
        return this;
    }

    public Box2i set(Box2i box) {
        this.x1 = box.x1;
        this.x2 = box.x2;
        this.y1 = box.y1;
        this.y2 = box.y2;
        return this;
    }

    public Vector2i point1(Vector2i dest) {
        dest.set(x1, y1);
        return dest;
    }

    public Vector2i point2(Vector2i dest) {
        dest.set(x2, y2);
        return dest;
    }

    /**
     * Check whether a given point falls inside this box, all edges inclusive.
     * @param x Point X
     * @param y Point Y
     * @return Whether its in this box.
     */
    public boolean contains(int x, int y) {
        return (x1 <= x && x <= x2
                && y1 <= y && y <= y2);
    }

    /**
     * Check whether a given point falls inside thix box, all edges inclusive.
     * @param point The point
     * @return Whether its in this box.
     */
    public boolean contains(Vector2ic point) {
        return contains(point.x(), point.y());
    }

    /**
     * Get the point at a corner of this box.
     * @param corner The corner to get.
     * @param dest Vector to put the result.
     * @return <code>dest</code>
     */
    public Vector2i getCorner(Corner corner, Vector2i dest) {
        int x = corner.isXMax() ? x2 : x1;
        int y = corner.isYMax() ? y2 : y1;
        return dest.set(x, y);
    }
    
    /**
     * Move a corner of this box.
     * @param corner The corner to move.
     * @param x New X value.
     * @param y New Y value.
     * @return The corner that is now at that point.
     */
    public Corner setCorner(Corner corner, int x, int y) {
        int otherX = corner.opposite().isXMax() ? x2 : x1;
        int otherY = corner.opposite().isYMax() ? y2 : y1;

        boolean xMax = x > otherX;
        this.x1 = xMax ? otherX : x;
        this.x2 = xMax ? x : otherX;

        boolean yMax = y > otherY;
        this.y1 = yMax ? otherY : y;
        this.y2 = yMax ? y : otherY;

        return Corner.get(xMax, yMax);
    }
    
    /**
     * Move a corner of this box.
     * @param corner The corner to move.
     * @param val Point to move the corner to.
     * @return The corner that is now at that point.
     */
    public Corner setCorner(Corner corner, Vector2ic val) {
        return setCorner(corner, val.x(), val.y());
    }

    public Corner getClosestCorner(int x, int y) {
        int x1Dif = Math.abs(x - x1);
        int x2Dif = Math.abs(x - x2);

        int y1Dif = Math.abs(y - y1);
        int y2Dif = Math.abs(y - y2);

        return Corner.get(x2Dif < x1Dif, y2Dif < y1Dif);
    }

    public Corner getClosestCorner(Vector2ic point) {
        return getClosestCorner(point.x(), point.y());
    }
}
