package com.igrium.replay_debugger.ui.graph;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.joml.Vector2dc;
import org.joml.Vector2i;

public class Graph extends JPanel {

    private Set<GraphChannel> channels = new HashSet<>();
    private float scaleX = 10f;
    private float scaleY = 100f;
    private Vector2i offset = new Vector2i();

    /**
     * Create the panel.
     */
    public Graph() {
        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
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

    public GraphChannel addChannel(Collection<Vector2dc> points) {
        GraphChannel channel = new GraphChannel();
        channel.getPoints().addAll(points);
        // channel.sort();

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
            for (Vector2dc point : channel.getPoints()) {
                Point nPoint = transformPoint(point);
                if (last != null) {
                    g2.drawLine(last.x, last.y, nPoint.x, nPoint.y);
                }
                last = nPoint;
            }
        }
    }

    public Point transformPoint(Vector2dc in) {
        return new Point(
            (int) (in.x() * scaleX) + offset.x(),
            (int) (in.y() * scaleY) + offset.y()
        );
    }
    
    private class MouseHandler implements MouseListener, MouseMotionListener {

        Vector2i dragStart;
        Vector2i dragOffset;

        @Override
        public void mouseClicked(MouseEvent e) {
            
        }

        @Override
        public void mousePressed(MouseEvent e) {
            dragStart = new Vector2i(offset);
            dragOffset = new Vector2i(e.getX(), e.getY());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragStart = null;
            dragOffset = null;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            
        }

        @Override
        public void mouseExited(MouseEvent e) {
            
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragStart != null) {
                Vector2i coord = new Vector2i(e.getX(), e.getY()).sub(dragOffset);
                offset.set(dragStart);
                offset.add(coord);
                e.consume();
                SwingUtilities.updateComponentTreeUI(Graph.this);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            
        }
        
        
    }

}
