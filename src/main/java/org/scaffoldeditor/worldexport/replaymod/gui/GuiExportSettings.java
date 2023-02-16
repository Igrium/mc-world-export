package org.scaffoldeditor.worldexport.replaymod.gui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.scaffoldeditor.worldexport.replaymod.export.ReplayExportSettings;
import org.scaffoldeditor.worldexport.replaymod.export.ReplayExporter;
import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.render.ReplayModRender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class GuiExportSettings extends AbstractGuiPopup<GuiExportSettings> {
    {
        disablePopupBackground();
    }

    public final GuiPanel contentPanel = new GuiPanel(popup).setBackgroundColor(new Color(0, 0, 0, 230));
    public final GuiVerticalList settingsList = new GuiVerticalList(contentPanel).setDrawSlider(true);
    public final GuiPanel buttonPanel = new GuiPanel(contentPanel).setLayout(new HorizontalLayout().setSpacing(4));

    private ReplayHandler replayHandler;
    private Timeline timeline;
    // private AbstractGuiScreen<?> screen;

    private MinecraftClient client = MinecraftClient.getInstance();

    private int minLowerDepth = 0;
    private int maxLowerDepth = 16;
    private final int minViewDistance = 1;
    
    private File outputFile;

    public void export() {
        close();

        ReplayExportSettings settings = new ReplayExportSettings()
                .setViewDistance(getViewDistance())
                .setLowerDepth(getLowerDepth())
                .setFluidMode(getFluidMode())
                .setOutputFile(outputFile);
        
        try {
            ReplayExporter exporter = new ReplayExporter(settings, replayHandler, timeline);
            exporter.exportReplay();
        } catch (Throwable e) {
            throw new CrashException(CrashReport.create(e, "Exporting replay"));
        }
    }

    public final GuiButton outputFileButton = new GuiButton().setMinSize(new Dimension(0, 20)).onClick(new Runnable() {
        public void run() {
            GuiFileChooserPopup popup = GuiFileChooserPopup.openSaveGui(GuiExportSettings.this, "replaymod.gui.save", "replay");
            popup.setFolder(outputFile.getParentFile());
            popup.setFileName(outputFile.getName());
            popup.onAccept(file -> {
                outputFile = file;
                outputFileButton.setLabel(file.getName());
            });
        }
    });
    
    public final GuiSlider viewDistanceSlider = new GuiSlider().onValueChanged(new Runnable() {
        public void run() {
            viewDistanceSlider.setText("Radius (Chunks): " + getViewDistance());
        };
    }).setSize(122, 20).setSteps(32 - minViewDistance);

    public final GuiSlider lowerDepthSlider = new GuiSlider().onValueChanged(new Runnable() {
        public void run() {
            lowerDepthSlider.setText("Lower Depth: " + getLowerDepth() * 16);
        };
    }).setSize(122, 20).setSteps(32);
    
    public final GuiLabel memoryWarning1 = new GuiLabel().setI18nText("worldexport.gui.memory_warning1")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiLabel memoryWarning2 = new GuiLabel().setI18nText("worldexport.gui.memory_warning2")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiLabel memoryWarning3 = new GuiLabel().setI18nText("worldexport.gui.memory_warning3")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiLabel memoryWarning4 = new GuiLabel().setI18nText("worldexport.gui.memory_warning4")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiDropdownMenu<FluidMode> fluidModeDropdown = new GuiDropdownMenu<FluidMode>()
            .setMinSize(new Dimension(0, 20)).setValues(FluidMode.NONE, FluidMode.STATIC).setSelected(FluidMode.NONE)
            .onSelection(ordinal -> {
                boolean showWarning = ordinal != FluidMode.NONE.ordinal();
                memoryWarning1.setEnabled(showWarning);
                memoryWarning2.setEnabled(showWarning);
                memoryWarning3.setEnabled(showWarning);
                memoryWarning4.setEnabled(showWarning);
            });

    public final GuiButton exportButton = new GuiButton(buttonPanel)
            .setLabel("Export")
            .setSize(100, 20)
            .onClick(this::export);
    
    public final GuiButton cancelButton = new GuiButton(buttonPanel)
            .setI18nLabel("replaymod.gui.cancel")
            .setSize(100, 20)
            .onClick(this::close);

    public final GuiPanel mainPanel = new GuiPanel()
            .addElements(new GridLayout.Data(1, 0.5),
                    new GuiLabel().setI18nText("replaymod.gui.rendersettings.outputfile"), outputFileButton,
                    viewDistanceSlider, lowerDepthSlider,
                    new GuiLabel().setText("Fluid Mode"), fluidModeDropdown)
            .setLayout(new GridLayout().setCellsEqualSize(false).setColumns(2).setSpacingX(5).setSpacingY(5));
    
    {
        settingsList.getListPanel().setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(0.5),
                        new GuiLabel().setText("Replay Export Settings"),
                        mainPanel,
                        memoryWarning1,
                        memoryWarning2,
                        memoryWarning3,
                        memoryWarning4);
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        outputFileButton.setLabel(outputFile.getName());
    }

    public void setLowerDepth(int lowerSectionCoord) {
        lowerDepthSlider.setSteps(maxLowerDepth - minLowerDepth);
        lowerDepthSlider.setValue(lowerSectionCoord - minLowerDepth);
    }
    
    public int getLowerDepth() {
        return lowerDepthSlider.getValue() + minLowerDepth;
    }

    public void setViewDistance(int viewDistance) {
        viewDistanceSlider.setValue(viewDistance - minViewDistance);
    }

    public int getViewDistance() {
        return viewDistanceSlider.getValue() + minViewDistance;
    }

    public FluidMode getFluidMode() {
        return fluidModeDropdown.getSelectedValue();
    }

    public GuiExportSettings(AbstractGuiScreen<?> container, ReplayHandler replayHandler, Timeline timeline) {
        super(container);
        this.replayHandler = replayHandler;
        this.timeline = timeline; 
        // this.screen = container;

        contentPanel.setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel container, int width, int height) {
                size(settingsList, width, height - height(buttonPanel) - 25);
                pos(settingsList, width / 2 - width(settingsList) / 2, 5);
                pos(buttonPanel, width / 2 - width(buttonPanel) / 2, y(settingsList) + height(settingsList) + 10);
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
                ReadableDimension screenSize = getContainer().getMinSize();
                return new Dimension(screenSize.getWidth() - 40, screenSize.getHeight() - 40);
            }
            
        });

        minLowerDepth = client.world.getBottomSectionCoord();
        maxLowerDepth = client.world.getTopSectionCoord();

        setOutputFile(generateOutputFile());
        setViewDistance(client.options.getClampedViewDistance());
        setLowerDepth(minLowerDepth);
    }
    @Override
    protected GuiExportSettings getThis() {
        return this;
    }

    @Override
    public void open() {
        super.open();
    }

    protected File generateOutputFile() {
        String fileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        File folder = ReplayModRender.instance.getVideoFolder();
        return new File(folder, fileName+".replay");
    }
    
    public static GuiScreen createBaseScreen() {
        GuiScreen screen = new GuiScreen();
        screen.setBackground(AbstractGuiScreen.Background.NONE);
        return screen;
    }
}
