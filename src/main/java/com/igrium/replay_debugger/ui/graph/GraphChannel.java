package com.igrium.replay_debugger.ui.graph;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;


public class GraphChannel {
    private Color color;
    private List<Point2D> points = new ArrayList<>();

    public GraphChannel() {
        Random rand = new Random();
        float r = rand.nextFloat();
        float g = rand.nextFloat();
        float b = rand.nextFloat();

        color = new Color(r, g, b);
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public List<Point2D> getPoints() {
        return points;
    }

    public void sort() {
        Collections.sort(points, (o1, o2) -> {
            return (int) ((o1.getX() - o2.getX()) * 100);
        });
    }
}
