package org.scaffoldeditor.worldexport.replaymod.gui;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;

import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.ReplayHandler;

import net.minecraft.util.crash.CrashReport;

@Deprecated
public class GuiCameraManagerOld extends AbstractGuiPopup<GuiCameraManagerOld> {

    public static GuiCameraManagerOld openScreen(ReplayHandler handler) {
        GuiScreen screen = new GuiScreen();
        screen.setBackground(AbstractGuiScreen.Background.NONE);

        GuiCameraManagerOld popup = new GuiCameraManagerOld(screen, handler);
        popup.open();
        screen.display();
        return popup;
    }

    protected ReplayHandler handler;
    protected final CameraAnimationModule cameraAnimations = CameraAnimationModule.getInstance();

    public final GuiPanel contentPanel = new GuiPanel(popup).setBackgroundColor(new Color(0, 0, 0, 230));
    public final GuiVerticalList camerasList = new GuiVerticalList(contentPanel).setDrawSlider(true);
    public final GuiPanel buttonPanel = new GuiPanel(contentPanel).setLayout(new HorizontalLayout().setSpacing(4));

    public final GuiButton cancelButton = new GuiButton(buttonPanel)
            .setI18nLabel("replaymod.gui.cancel")
            .setSize(100, 20)
            .onClick(this::close);

    public final GuiButton importButton = new GuiButton(buttonPanel)
            .setLabel("Import Camera")
            .setSize(100, 20)
            .onClick(this::openFileChooser);

    public GuiCameraManagerOld(GuiContainer<?> container, ReplayHandler handler) {
        super(container);
        this.handler = handler;

        camerasList.getListPanel().setLayout(new VerticalLayout().setSpacing(3));
        contentPanel.setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel container, int width, int height) {
                size(camerasList, width - 4, height - height(buttonPanel) - 25);
                pos(buttonPanel, width / 2 - width(buttonPanel) / 2, 5);
                pos(camerasList, width / 2 - width(camerasList) / 2, y(buttonPanel) + height(buttonPanel) + 5);
            }
            
            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
                ReadableDimension screenSize = getContainer().getMinSize();
                return new Dimension(screenSize.getWidth() - 120, screenSize.getHeight() - 40);
            }
        });
        // refresh();
    }

    /**
     * Called when the import button is clicked.
     */
    public void openFileChooser() {
        GuiFileChooserPopup chooser = GuiFileChooserPopup.openLoadGui(this, "Import Camera", "xml");
        chooser.onAccept(file -> {
            try {
                CameraAnimationModule.getInstance().importAnimation(handler.getReplayFile(), file);
                // refresh();
            } catch (IOException e) {
                Utils.error(LogManager.getLogger("Camera Import"), getContainer(), CrashReport.create(e, "Importing Camera Animation"), () -> {});
            }
        });
    }

    @SuppressWarnings("rawtypes")
    public void refresh() {
        // For some reason there isn't a dedicated clear function.
        Collection<GuiElement> elements = Set.copyOf(camerasList.getElements().keySet());
        elements.forEach(camerasList::removeElement);

        Map<Integer, AbstractCameraAnimation> anims = CameraAnimationModule.getInstance().getAnimations(handler.getReplayFile());
        GuiElement[] newElements = anims.keySet().stream().sorted().map(id -> {
            return new GuiLabel().setText(anims.get(id).getName());
        }).toArray(GuiElement[]::new);

        camerasList.addElements(new VerticalLayout.Data(), newElements);
    }

    @Override
    protected GuiCameraManagerOld getThis() {
        return this;
    }
    
}
