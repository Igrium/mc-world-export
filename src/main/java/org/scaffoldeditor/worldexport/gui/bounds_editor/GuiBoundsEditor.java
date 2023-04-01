package org.scaffoldeditor.worldexport.gui.bounds_editor;

import java.util.Objects;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.world.World;

public class GuiBoundsEditor extends AbstractGuiPopup<GuiBoundsEditor> {

    private final World world;

    private final OverviewData overviewData;
    private final Identifier textureID;

    private Vec2f panOffset = Vec2f.ZERO;
    private double zoomAmount = 0;

    public GuiBoundsEditor(GuiContainer<?> container, World world, int width, int height, ChunkPos rootPos) {
        super(container);
        Objects.requireNonNull(world);
        this.world = world;
        
        overviewData = new OverviewData(width, height, rootPos);
        overviewData.updateTexture(world, world.getBottomY(), world.getBottomY() + height, Util.getMainWorkerExecutor());
        textureID = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("overview/", overviewData.getTexture());
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        // TODO Auto-generated method stub
        super.draw(renderer, size, renderInfo);
        drawOverview(renderer, size, renderInfo);
    }

    private double getZoomMultiplier() {
        return Math.pow(2, zoomAmount);
    }

    private void drawOverview(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        MatrixStack matrices = renderer.getMatrixStack();
        
        int centerX = size.getWidth() / 2;
        int centerY = size.getHeight() / 2;

        float zoomMultiplier = (float) getZoomMultiplier();

        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.scale(zoomMultiplier, zoomMultiplier, zoomMultiplier);
        matrices.translate(-centerX, -centerY, 0);
        matrices.translate(panOffset.x, panOffset.y, 0);

        renderer.bindTexture(textureID);
        renderer.drawTexturedRect(0, 0, 0, 0, overviewData.getTexture().getImage().getWidth(), overviewData.getTexture().getImage().getHeight());
        
        matrices.pop();
    }

    public OverviewData getOverviewData() {
        return overviewData;
    }

    public Identifier getTextureID() {
        return textureID;
    }

    @Override
    protected GuiBoundsEditor getThis() {
        return this;
    }
    
    @Override
    protected void close() {
        super.close();
        overviewData.close();
    }

    @Override
    public void open() {
        super.open();
    }
}
