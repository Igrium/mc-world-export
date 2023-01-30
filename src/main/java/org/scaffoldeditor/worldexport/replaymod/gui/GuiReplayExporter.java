package org.scaffoldeditor.worldexport.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;

public class GuiReplayExporter extends GuiScreen implements Tickable {

    public final GuiLabel title = new GuiLabel().setI18nText("worldexport.gui.exporting.title");

    public final GuiProgressBar animProgressBar = new GuiProgressBar();
    public final GuiProgressBar worldProgressBar = new GuiProgressBar();

    {
        final GuiPanel contentPanel = new GuiPanel(this).setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel container, int width, int height) {
                pos(title, width / 2 - width(title) / 2, 0);

                size(animProgressBar, width, 20);
                size(worldProgressBar, width, 20);

                pos(animProgressBar, (width - width(animProgressBar)) / 2, height / 2 - height(animProgressBar) - 5);
                pos(worldProgressBar, (width - width(worldProgressBar)) / 2, height / 2 + 5);
            }
            
        }).addElements(null, title, animProgressBar, worldProgressBar);
        setLayout(new CustomLayout<GuiScreen>() {

            @Override
            protected void layout(GuiScreen container, int width, int height) {
                size(contentPanel, width - 40, height - 20);
                pos(contentPanel, (width - width(contentPanel)) / 2, (height - height(contentPanel)) / 2);
            }
            
        });
        setBackground(Background.DIRT);
    }

    @Override
    public void tick() {
        
    }
    
}
