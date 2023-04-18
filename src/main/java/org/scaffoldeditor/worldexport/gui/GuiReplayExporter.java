package org.scaffoldeditor.worldexport.gui;

import org.scaffoldeditor.worldexport.replaymod.util.ExportInfo;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;

public class GuiReplayExporter extends GuiScreen implements Tickable {

    public final GuiLabel title = new GuiLabel().setI18nText("worldexport.gui.exporting.title");

    public final GuiLabel statusText = new GuiLabel().setI18nText("worldexport.gui.status.init");
    public final GuiProgressBar animProgressBar = new GuiProgressBar();
    public final GuiProgressBar worldProgressBar = new GuiProgressBar();

    public final ExportInfo exportInfo;

    private static final int BAR_HEIGHT = 20;
    private static final int SPACING = 5;


    {
        final GuiPanel contentPanel = new GuiPanel(this).setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel container, int width, int height) {
                pos(title, width / 2 - width(title) / 2, 0);
                
                size(animProgressBar, width, BAR_HEIGHT);
                size(worldProgressBar, width, BAR_HEIGHT);

                int centerY = height / 2;

                pos(statusText, width / 2 - width(statusText) / 2, centerY - BAR_HEIGHT / 2 - SPACING - height(statusText));
                pos(animProgressBar, 0, centerY - BAR_HEIGHT / 2);
                pos(worldProgressBar, 0, centerY + BAR_HEIGHT / 2 + SPACING);
            }
            
        }).addElements(null, title, statusText, animProgressBar, worldProgressBar);
        setLayout(new CustomLayout<GuiScreen>() {

            @Override
            protected void layout(GuiScreen container, int width, int height) {
                size(contentPanel, width - 40, height - 20);
                pos(contentPanel, (width - width(contentPanel)) / 2, (height - height(contentPanel)) / 2);
            }
            
        });
        setBackground(Background.DIRT);
    }

    public GuiReplayExporter(ExportInfo exportInfo) {
        this.exportInfo = exportInfo;
    }

    @Override
    public void tick() {
        statusText.setI18nText(exportInfo.getPhase());

        int framesDone = exportInfo.getFramesDone();
        int totalFrames = exportInfo.getTotalFrames();
        
        if (totalFrames != 0) animProgressBar.setProgress(framesDone / (float) totalFrames);
        animProgressBar.setI18nLabel("worldexport.gui.exporting.frame_progress", framesDone, totalFrames);
        
        int chunksDone = exportInfo.getChunksDone();
        int totalChunks = exportInfo.getTotalChunks();
        
        if (totalChunks != 0) worldProgressBar.setProgress(chunksDone / (float) totalChunks);
        worldProgressBar.setI18nLabel("worldexport.gui.exporting.world_progress", chunksDone, totalChunks);
    }
    
}
