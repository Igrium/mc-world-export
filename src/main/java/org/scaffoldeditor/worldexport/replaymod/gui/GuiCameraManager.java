package org.scaffoldeditor.worldexport.replaymod.gui;

import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.replay.ReplayHandler;

public class GuiCameraManager extends AbstractGuiPopup<GuiCameraManager> {

    protected ReplayHandler handler;
    protected final CameraAnimationModule cameraAnimations = CameraAnimationModule.getInstance();

    public final GuiPanel contentPanel = new GuiPanel(popup).setBackgroundColor(new Color(0, 0, 0, 230));
    public final GuiVerticalList camerasList = new GuiVerticalList(contentPanel).setDrawSlider(true);
    public final GuiPanel buttonPanel = new GuiPanel(contentPanel).setLayout(new HorizontalLayout().setSpacing(4));

    public GuiCameraManager(GuiContainer<?> container, ReplayHandler handler) {
        super(container);
        this.handler = handler;

        camerasList.getListPanel().setLayout(new VerticalLayout().setSpacing(3));
    }

    @Override
    protected GuiCameraManager getThis() {
        return this;
    }
    
}
