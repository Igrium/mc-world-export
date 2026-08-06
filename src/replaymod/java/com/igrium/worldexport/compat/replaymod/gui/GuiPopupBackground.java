package com.igrium.worldexport.compat.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;

/**
 * There's a regression in jGui which makes drawing popup backgrounds extremely inefficient. Custom alternative to fix.
 */
public class GuiPopupBackground extends AbstractGuiElement<GuiPopupBackground> {

    /**
     * Top-left corner of the nine-slice in {@link #TEXTURE}, matching jGui's own popup background.
     */
    private static final int U0 = 0;
    private static final int V0 = 39;

    /**
     * Size of each slice. The middle slices sit 6px in, leaving a 1px gap from the corners.
     */
    private static final int SLICE = 5;
    private static final int MIDDLE = 6;

    private static final int TEXTURE_SIZE = 256;

    /**
     * Offset of the far slices, which sit one gap pixel past the middle ones.
     */
    private static final int FAR = MIDDLE + SLICE + 1;

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        // Matches jGui, which only draws the popup background on the bottom layer.
        if (renderInfo.getLayer() != 0) return;

        int width = size.getWidth();
        int height = size.getHeight();
        if (width < SLICE * 2 || height < SLICE * 2) return;

        int right = width - SLICE;
        int bottom = height - SLICE;

        renderer.bindTexture(TEXTURE);

        // Corners, drawn at their native size
        renderer.drawTexturedRect(0, 0, U0, V0, SLICE, SLICE);
        renderer.drawTexturedRect(right, 0, U0 + FAR, V0, SLICE, SLICE);
        renderer.drawTexturedRect(0, bottom, U0, V0 + FAR, SLICE, SLICE);
        renderer.drawTexturedRect(right, bottom, U0 + FAR, V0 + FAR, SLICE, SLICE);

        // Edges still tile, so they stay exact. They only grow with the perimeter.
        for (int x = SLICE; x < right; x += SLICE) {
            int rx = Math.min(SLICE, right - x);
            renderer.drawTexturedRect(x, 0, U0 + MIDDLE, V0, rx, SLICE);
            renderer.drawTexturedRect(x, bottom, U0 + MIDDLE, V0 + FAR, rx, SLICE);
        }
        for (int y = SLICE; y < bottom; y += SLICE) {
            int ry = Math.min(SLICE, bottom - y);
            renderer.drawTexturedRect(0, y, U0, V0 + MIDDLE, SLICE, ry);
            renderer.drawTexturedRect(right, y, U0 + FAR, V0 + MIDDLE, SLICE, ry);
        }

        // The center is the bulk of the quads, and its source cell is flat, so stretch it.
        renderer.drawTexturedRect(SLICE, SLICE, U0 + MIDDLE, V0 + MIDDLE, right - SLICE, bottom - SLICE,
                SLICE, SLICE, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected ReadableDimension calcMinSize() {
        return new Dimension(0, 0);
    }

    @Override
    protected GuiPopupBackground getThis() {
        return this;
    }
}