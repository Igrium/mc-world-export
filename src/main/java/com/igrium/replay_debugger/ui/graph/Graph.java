package com.igrium.replay_debugger.ui.graph;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Graph extends JPanel {

    private Set<GraphChannel> channels = new HashSet<>();
    private float scaleX = 10;
    private float scaleY = 100;

    /**
     * Create the panel.
     */
    public Graph() {
        
    }

    public float getScaleX() {
        return scaleX;
    }

    public void setScaleX(float scaleX) {
        this.scaleX = scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public void setScaleY(float scaleY) {
        this.scaleY = scaleY;
    }

    public Set<GraphChannel> getChannels() {
        return channels;
    }

    public GraphChannel addChannel(Collection<Point2D> points) {
        GraphChannel channel = new GraphChannel();
        channel.getPoints().addAll(points);
        channel.sort();

        channels.add(channel);
        return channel;
    }

    public void clear() {
        channels.clear();
        SwingUtilities.updateComponentTreeUI(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (GraphChannel channel : getChannels()) {
            g2.setColor(channel.getColor());
            Point last = null;
            for (Point2D point : channel.getPoints()) {
                Point nPoint = transformPoint(point);
                if (last != null) {
                    g2.drawLine(last.x, last.y, nPoint.x, nPoint.y);
                }
                last = nPoint;
            }
        }
    }

    public Point transformPoint(Point2D in) {
        return new Point((int) (in.getX() * scaleX), (int) (in.getY() * scaleY));
    }

}
