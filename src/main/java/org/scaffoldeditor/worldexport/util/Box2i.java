package org.scaffoldeditor.worldexport.util;

import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * A two-dimensional bounding box made of integers.
 */
public class Box2i {
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

    public void set(int x1, int y1, int x2, int y2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
    }
    
    public void set(Vector2ic point1, Vector2ic point2) {
        this.set(point1.x(), point1.y(), point2.x(), point2.y());
    }

    public void set(Box2i box) {
        this.x1 = box.x1;
        this.x2 = box.x2;
        this.y1 = box.y1;
        this.y2 = box.y2;
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
}
