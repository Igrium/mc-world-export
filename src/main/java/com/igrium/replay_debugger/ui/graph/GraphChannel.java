package com.igrium.replay_debugger.ui.graph;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.joml.Vector2dc;


public class GraphChannel {
    private Color color;
    private List<Vector2dc> points = new ArrayList<>();

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

    public List<Vector2dc> getPoints() {
        return points;
    }

    public void sort() {
        Collections.sort(points, (o1, o2) -> {
            return (int) ((o1.x() - o2.y() * 100));
        });
    }
}
