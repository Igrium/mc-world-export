package org.scaffoldeditor.worldexport.gui.bounds_editor;

import java.io.Closeable;
import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.scaffoldeditor.worldexport.util.Box2i;

import com.mojang.logging.LogUtils;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Scrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.world.World;

public class GuiBoundsOverview extends AbstractGuiElement<GuiBoundsOverview> implements Closeable, Draggable, Scrollable {

    private static final int FILL_COLOR = ColorHelper.Argb.getArgb(64, 255, 0, 255);
    private static final int BORDER_COLOR = ColorHelper.Argb.getArgb(128, 255, 0, 255);

    private final OverviewData overviewData;
    private final Identifier texID;
    private final World world;

    private Vector2f panOffset = new Vector2f();
    private double zoomAmount = 0;

    private ReadablePoint lastGlOffset = new Point();
    private Matrix4f lastTransformMatrix = new Matrix4f();

    private final Box2i bounds;

    public GuiBoundsOverview(World world, OverviewData overviewData) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(overviewData);

        this.overviewData = overviewData;
        this.world = world;
        this.texID = getMinecraft().getTextureManager().registerDynamicTexture("overview/", overviewData.getTexture());
        updateTexture();

        // Default bounds
        int centerX = overviewData.getOrigin().x + overviewData.getWidth() / 2;
        int centerZ = overviewData.getOrigin().z + overviewData.getHeight() / 2;

        bounds = new Box2i(centerX - 4, centerZ - 4, centerX + 4, centerZ + 4);
    }

    public Box2i getBounds() {
        return bounds;
    }

    public void setBounds(Box2i bounds) {
        this.bounds.set(bounds);
    }

    public double getZoomAmount() {
        return zoomAmount;
    }

    public void setZoomAmount(double zoomAmount) {
        this.zoomAmount = zoomAmount;
    }

    public Vector2f getPanOffset() {
        return panOffset;
    }

    public void setPanOffset(Vector2fc panOffset) {
        this.panOffset.set(panOffset);
    }

    public void updateTexture() {
        overviewData.updateTexture(world, world.getBottomY(), world.getTopY(), Util.getMainWorkerExecutor());
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        drawMap(renderer, size, renderInfo);
    }

    public double getZoomMultiplier() {
        return Math.pow(2, zoomAmount);
    }

    private void drawMap(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        NativeImage image = overviewData.getTexture().getImage();

        MatrixStack matrices = renderer.getMatrixStack();
        matrices.push();

        ReadablePoint glOffset = renderer.getOpenGlOffset();
        matrices.translate(glOffset.getX(), glOffset.getY(), 0);

        // Center image
        matrices.translate(size.getWidth() / 2f - image.getWidth() / 2f, size.getHeight() / 2f - image.getHeight() / 2f, 0);

        // Apply zoom
        float zoomMultiplier = (float) getZoomMultiplier();
        float centerX = image.getWidth() / 2f;
        float centerY = image.getHeight() / 2f;

        matrices.translate(centerX, centerY, 0);
        matrices.scale(zoomMultiplier, zoomMultiplier, zoomMultiplier);
        matrices.translate(-centerX, -centerY, 0);
        
        matrices.translate(panOffset.x, panOffset.y, 0);

        // matrices.translate(-image.getWidth() / 2f, -image.getHeight() / 2f, 0);
        
        // Center image
        int imageX = 0;
        int imageY = 0;

        renderer.bindTexture(texID);
        DrawableHelper.drawTexture(matrices, imageX, imageY, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
        
        Vector2i bounds1 = worldToImage(bounds.point1(new Vector2i()).mul(16));
        Vector2i bounds2 = worldToImage(bounds.point2(new Vector2i()).mul(16)).add(1, 1); // Inclusive
        
        DrawableHelper.fill(matrices, bounds1.x, bounds1.y, bounds2.x, bounds2.y, FILL_COLOR);

        // Selection border
        DrawableHelper.fill(matrices, bounds1.x - 1, bounds1.y - 1, bounds2.x + 1, bounds1.y, BORDER_COLOR);
        DrawableHelper.fill(matrices, bounds1.x - 1, bounds2.y, bounds2.x + 1, bounds2.y + 1, BORDER_COLOR);
        DrawableHelper.fill(matrices, bounds1.x - 1, bounds1.y, bounds1.x, bounds2.y, BORDER_COLOR);
        DrawableHelper.fill(matrices, bounds2.x, bounds1.y, bounds2.x + 1, bounds2.y, BORDER_COLOR);
        

        lastGlOffset = glOffset;
        lastTransformMatrix.identity();

        // lastTransformMatrix.set(matrices.peek().getPositionMatrix());
        calcTransformMatrix(lastTransformMatrix);
        matrices.pop();

    }

    private Matrix4f calcTransformMatrix(Matrix4f dest) {
        NativeImage image = overviewData.getTexture().getImage();

        dest.translate(lastGlOffset.getX(), lastGlOffset.getY(), 0);

        // Center image
        dest.translate(getLastSize().getWidth() / 2f - image.getWidth() / 2f,
                getLastSize().getHeight() / 2f - image.getHeight() / 2f, 0);

        // Apply zoom
        float zoomMultiplier = (float) getZoomMultiplier();
        float centerX = image.getWidth() / 2f;
        float centerY = image.getHeight() / 2f;

        dest.translate(centerX, centerY, 0);
        dest.scale(zoomMultiplier);
        dest.translate(-centerX, -centerY, 0);

        dest.translate(panOffset.x, panOffset.y, 0);

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
        Vector4f vec = new Vector4f(viewport.x, viewport.y, 0, 1);
        lastTransformMatrix.invert(new Matrix4f()).transform(vec);
        viewport.set(vec.x, vec.y);

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
            world.x() - overviewData.getOrigin().x * 16,
            world.y() - overviewData.getOrigin().z * 16
        );
    }

    public Vector2i worldToImage(Vector2i world) {
        return worldToImage(world, world);
    }

    public Vector2i imageToWorld(Vector2ic image, Vector2i dest) {
        return dest.set(
            image.x() + overviewData.getOrigin().x * 16,
            image.y() + overviewData.getOrigin().z * 16
        );
    }
    
    public Vector2i imageToWorld(Vector2i image) {
        return imageToWorld(image, image);
    }

    @Override
    protected ReadableDimension calcMinSize() {
        return new Dimension(384, 256);
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
    public boolean mouseClick(ReadablePoint position, int button) {
        // return (button == GLFW.GLFW_MOUSE_BUTTON_1);
        LogUtils.getLogger().info("Click pos: " + position);
        // Vector2f worldPos = viewportToImage(new Vector2f(position.getX(), position.getY()));
        Vector2i worldPos = viewportToWorld(new Vector2i(position.getX(), position.getY()));
        LogUtils.getLogger().info(String.format("World pos: [%d, %d]", worldPos.x, worldPos.y));
        return false;
    }

    @Override
    public boolean scroll(ReadablePoint mousePosition, int dWheel) {
        setZoomAmount(zoomAmount + (dWheel > 0 ? .5 : -.5));
        return true;
    }

    private ReadablePoint lastDragPosition;

    @Override
    public boolean mouseDrag(ReadablePoint position, int button, long timeSinseLastCall) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
            return mouseDragSecondary(position);
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return false;
        
        if (lastDragPosition == null) {
            lastDragPosition = position;
            return true;
        }

        float zoomMultiplier = (float) getZoomMultiplier();
        float deltaX = (position.getX() - lastDragPosition.getX()) / zoomMultiplier;
        float deltaY = (position.getY() - lastDragPosition.getY()) / zoomMultiplier;
        
        this.panOffset.x += deltaX;
        this.panOffset.y += deltaY;

        lastDragPosition = position;
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
    public boolean mouseRelease(ReadablePoint position, int button) {
        if (lastDragPosition != null) {
            lastDragPosition = null;
            return true;
        } else {
            return false;
        }
    }
    
}
