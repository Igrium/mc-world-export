package com.igrium.worldexport.compat.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Scrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.igrium.worldexport.math.Box2i;
import com.mojang.blaze3d.platform.NativeImage;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.joml.*;
import org.lwjgl.glfw.GLFW;

import java.io.Closeable;
import java.util.Objects;
import java.lang.Math;

public class GuiBoundsOverview extends AbstractGuiElement<GuiBoundsOverview> implements Closeable, Draggable, Scrollable {

    private static final int FILL_COLOR = ARGB.color(64, 255, 0, 255);
    private static final int BORDER_COLOR = ARGB.color(128, 255, 0, 255);

    private final OverviewData overviewData;
    private final Identifier texID;
    private final Level world;

    @Getter
    private final Vector2f panOffset = new Vector2f();
    @Setter
    @Getter
    private double zoomAmount = 0;

    private ReadablePoint lastGlOffset = new Point();
    private final Matrix3x2f lastTransformMatrix = new Matrix3x2f();

    @Getter
    private final Box2i bounds;

    @Getter
    private int bottomY;
    @Getter
    private int topY;

    public GuiBoundsOverview(Level world, OverviewData overviewData) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(overviewData);

        bottomY = world.getMinY();
        topY = world.getMinY() + world.getHeight();

        this.overviewData = overviewData;
        this.world = world;
        this.texID = Identifier.fromNamespaceAndPath("worldexport", "overview_" + System.identityHashCode(overviewData));
        getMinecraft().getTextureManager().register(texID, overviewData.getTexture());
        updateTexture();

        // Default bounds
        int centerX = overviewData.getOrigin().x() + overviewData.getWidth() / 2;
        int centerZ = overviewData.getOrigin().z() + overviewData.getHeight() / 2;

        bounds = new Box2i(centerX - 4, centerZ - 4, centerX + 4, centerZ + 4);
    }

    public void setBounds(Box2i bounds) {
        this.bounds.set(bounds);
    }

    public void setPanOffset(Vector2fc panOffset) {
        this.panOffset.set(panOffset);
    }

    public void setBottomY(int bottomY) {
        this.bottomY = bottomY;
        getMinecraft().execute(this::updateTexture);
    }

    public void setTopY(int topY) {
        this.topY = topY;
        getMinecraft().execute(this::updateTexture);
    }

    public void updateTexture() {
        overviewData.updateTexture(world, bottomY, topY);
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        drawMap(renderer, size, renderInfo);
    }

    public double getZoomMultiplier() {
        return Math.pow(2, zoomAmount);
    }

    private void drawMap(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        NativeImage image = overviewData.getTexture().getPixels();

        GuiGraphicsExtractor context = renderer.getContext();
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();

        ReadablePoint glOffset = renderer.getOpenGlOffset();
        matrices.translate(glOffset.getX(), glOffset.getY());

        // jGui's setDrawingArea uses raw glScissor, which the deferred GUI renderer ignores.
        context.enableScissor(0, 0, size.getWidth(), size.getHeight());

        // Center image
        matrices.translate(size.getWidth() / 2f - image.getWidth() / 2f, size.getHeight() / 2f - image.getHeight() / 2f);

        // Apply zoom
        float zoomMultiplier = (float) getZoomMultiplier();
        float centerX = image.getWidth() / 2f;
        float centerY = image.getHeight() / 2f;

        matrices.translate(centerX, centerY);
        matrices.scale(zoomMultiplier, zoomMultiplier);
        matrices.translate(-centerX, -centerY);

        matrices.translate(panOffset.x, panOffset.y);

        // Center image
        int imageX = 0;
        int imageY = 0;

        context.blit(RenderPipelines.GUI_TEXTURED, texID, imageX, imageY, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());

        Vector2i bounds1 = worldToImage(bounds.point1(new Vector2i()).mul(16));
        Vector2i bounds2 = worldToImage(bounds.point2(new Vector2i()).mul(16)).add(1, 1); // Inclusive

        context.fill(bounds1.x, bounds1.y, bounds2.x, bounds2.y, FILL_COLOR);

        // Selection border
        context.fill(bounds1.x - 1, bounds1.y - 1, bounds2.x + 1, bounds1.y, BORDER_COLOR);
        context.fill(bounds1.x - 1, bounds2.y, bounds2.x + 1, bounds2.y + 1, BORDER_COLOR);
        context.fill(bounds1.x - 1, bounds1.y, bounds1.x, bounds2.y, BORDER_COLOR);
        context.fill(bounds2.x, bounds1.y, bounds2.x + 1, bounds2.y, BORDER_COLOR);


        context.disableScissor();

        lastGlOffset = glOffset;
        lastTransformMatrix.identity();

        calcTransformMatrix(lastTransformMatrix);
        matrices.popMatrix();

    }

    private Matrix3x2f calcTransformMatrix(Matrix3x2f dest) {
        NativeImage image = overviewData.getTexture().getPixels();

        dest.translate(lastGlOffset.getX(), lastGlOffset.getY());

        // Center image
        dest.translate(getLastSize().getWidth() / 2f - image.getWidth() / 2f,
                getLastSize().getHeight() / 2f - image.getHeight() / 2f);

        // Apply zoom
        float zoomMultiplier = (float) getZoomMultiplier();
        float centerX = image.getWidth() / 2f;
        float centerY = image.getHeight() / 2f;

        dest.translate(centerX, centerY);
        dest.scale(zoomMultiplier);
        dest.translate(-centerX, -centerY);

        dest.translate(panOffset.x, panOffset.y);

        return dest;
    }

    public Vector2f viewportToImage(Vector2ic viewport, Vector2f dest) {
        dest.set(viewport);
        viewportToImage(dest);
        return dest;
    }

    public Vector2f viewportToImage(Vector2fc viewport, Vector2f dest) {
        dest.set(viewport);
        viewportToImage(dest);
        return dest;
    }

    public Vector2f viewportToImage(Vector2f viewport) {
        lastTransformMatrix.invert(new Matrix3x2f()).transformPosition(viewport);
        return viewport;
    }

    public Vector2i viewportToWorld(Vector2ic viewport, Vector2i dest) {
        Vector2f image = viewportToImage(new Vector2f(viewport));
        return imageToWorld(dest.set((int) image.x, (int) image.y));
    }

    public Vector2i viewportToWorld(Vector2i viewport) {
        return viewportToWorld(viewport, viewport);
    }

    public Vector2i worldToImage(Vector2ic world, Vector2i dest) {
        return dest.set(
                world.x() - overviewData.getOrigin().x() * 16,
                world.y() - overviewData.getOrigin().z() * 16
        );
    }

    public Vector2i worldToImage(Vector2i world) {
        return worldToImage(world, world);
    }

    public Vector2i imageToWorld(Vector2ic image, Vector2i dest) {
        return dest.set(
                image.x() + overviewData.getOrigin().x() * 16,
                image.y() + overviewData.getOrigin().z() * 16
        );
    }

    public Vector2i imageToWorld(Vector2i image) {
        return imageToWorld(image, image);
    }

    @Override
    protected ReadableDimension calcMinSize() {
        return new Dimension(128, 128);
    }

    @Override
    protected GuiBoundsOverview getThis() {
        return this;
    }

    @Override
    public void close() {
        overviewData.close();
    }

    @Override
    public boolean mouseClick(Click click) {
        return false;
    }

    @Override
    public boolean scroll(ReadablePoint mousePosition, int dWheel) {
        setZoomAmount(zoomAmount + (dWheel > 0 ? .5 : -.5));
        return true;
    }

    private ReadablePoint lastDragPosition;

    protected boolean isMouseHovering(ReadablePoint pos) {
        return pos.getX() > 0 && pos.getY() > 0
                && pos.getX() < getLastSize().getWidth() && pos.getY() < getLastSize().getHeight();
    }

    @Override
    public boolean mouseDrag(Click click) {
        Point pos = new Point(click);
        if (getContainer() != null) {
            getContainer().convertFor(this, pos);
        }
        if (!isMouseHovering(pos)) return false;

        if (click.button == GLFW.GLFW_MOUSE_BUTTON_2) {
            return mouseDragSecondary(click);
        }

        if (click.button != GLFW.GLFW_MOUSE_BUTTON_1) return false;

        if (lastDragPosition == null) {
            lastDragPosition = click;
            return true;
        }

        float zoomMultiplier = (float) getZoomMultiplier();
        float deltaX = (click.getX() - lastDragPosition.getX()) / zoomMultiplier;
        float deltaY = (click.getY() - lastDragPosition.getY()) / zoomMultiplier;

        this.panOffset.x += deltaX;
        this.panOffset.y += deltaY;

        lastDragPosition = click;
        return true;
    }

    private boolean mouseDragSecondary(ReadablePoint position) {
        Vector2i worldPos = viewportToWorld(new Vector2i(position.getX(), position.getY()));

        worldPos.x = Math.round(worldPos.x / 16f);
        worldPos.y = Math.round(worldPos.y / 16f);

        Box2i.Corner corner = bounds.getClosestCorner(worldPos);
        bounds.setCorner(corner, worldPos);
        return true;
    }

    @Override
    public boolean mouseRelease(Click click) {
        if (lastDragPosition != null) {
            lastDragPosition = null;
            return true;
        } else {
            return false;
        }
    }

}