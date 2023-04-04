package org.scaffoldeditor.worldexport.gui;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.gui.bounds_editor.GuiBoundsEditor;
import org.scaffoldeditor.worldexport.replaymod.export.ReplayExportSettings;
import org.scaffoldeditor.worldexport.replaymod.export.ReplayExporter;
import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.render.ReplayModRender;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec2f;

public class GuiExportSettings extends GuiScreen implements Closeable {

    public final GuiPanel contentPanel = new GuiPanel(this).setBackgroundColor(Colors.DARK_TRANSPARENT);
    public final GuiVerticalList settingsList = new GuiVerticalList(contentPanel).setDrawSlider(true);
    public final GuiPanel buttonPanel = new GuiPanel(contentPanel).setLayout(new HorizontalLayout().setSpacing(4));

    private ReplayHandler replayHandler;
    private Timeline timeline;

    private MinecraftClient client = MinecraftClient.getInstance();

    private int minLowerDepth = 0;
    private int maxLowerDepth = 16;
    private static final int MIN_VIEW_DISTANCE = 1;

    private File outputFile;

    @Nullable
    public AbstractGuiScreen<?> prevScreen = null; 

    public void export() {
        close();

        ReplayExportSettings settings = readSettings();
        
        try {
            ReplayExporter exporter = new ReplayExporter(settings, replayHandler, timeline);
            exporter.exportReplay();
        } catch (Throwable e) {
            throw new CrashException(CrashReport.create(e, "Exporting replay"));
        }
    }

    public ReplayExportSettings readSettings() {
        return new ReplayExportSettings()
                .setViewDistance(getViewDistance())
                .setLowerDepth(getLowerDepth())
                .setFluidMode(getFluidMode())
                .setOutputFile(outputFile);
    }

    public void applySettings(ReplayExportSettings settings) {
        setViewDistance(settings.getViewDistance());
        setLowerDepth(settings.getLowerDepth());
        setFluidMode(settings.getFluidMode());

        // So we don't crash opening the file select screen
        File outputFile = settings.getOutputFile();
        if (outputFile == null || !outputFile.getParentFile().isDirectory()) {
            outputFile = generateOutputFile();
        }
        setOutputFile(outputFile);
    }

    public final GuiButton outputFileButton = new GuiButton().setMinSize(new Dimension(0, 20)).onClick(this::handleClickOutputFile);

    private void handleClickOutputFile() {
        GuiFileChooserPopup popup = GuiFileChooserPopup.openSaveGui(this, "replaymod.gui.save", "replay");
        popup.setFolder(outputFile.getParentFile());
        popup.setFileName(outputFile.getName());
        popup.onAccept(file -> {
            outputFile = file;
            outputFileButton.setLabel(file.getName());
        });
    }

    public final GuiSlider viewDistanceSlider = new GuiSlider().onValueChanged(this::handleChangeViewDistance)
            .setSize(122, 20).setSteps(32 - MIN_VIEW_DISTANCE);

    private void handleChangeViewDistance() {
        viewDistanceSlider.setI18nText("worldexport.gui.export.radius", getViewDistance());
    }

    public final GuiSlider lowerDepthSlider = new GuiSlider().onValueChanged(this::handleChangeLowerDepth)
            .setSize(122, 20).setSteps(32);

    private void handleChangeLowerDepth() {
        lowerDepthSlider.setI18nText("worldexport.gui.export.lower_depth", getLowerDepth() * 16);
    }

    public final GuiDropdownMenu<FluidMode> fluidModeDropdown = new GuiDropdownMenu<FluidMode>()
            .setMinSize(new Dimension(0, 20)).setValues(FluidMode.NONE, FluidMode.STATIC).setSelected(FluidMode.NONE)
            .onSelection(this::handleChangeFluidMode);

    private void handleChangeFluidMode(Integer ordinal) {
        boolean showWarning = ordinal != FluidMode.NONE.ordinal();
        memoryWarning1.setEnabled(showWarning);
        memoryWarning2.setEnabled(showWarning);
        memoryWarning3.setEnabled(showWarning);
        memoryWarning4.setEnabled(showWarning);
    }

    public final GuiLabel memoryWarning1 = new GuiLabel().setI18nText("worldexport.gui.memory_warning1")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiLabel memoryWarning2 = new GuiLabel().setI18nText("worldexport.gui.memory_warning2")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiLabel memoryWarning3 = new GuiLabel().setI18nText("worldexport.gui.memory_warning3")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiLabel memoryWarning4 = new GuiLabel().setI18nText("worldexport.gui.memory_warning4")
            .setColor(Color.ORANGE).setEnabled(false);

    public final GuiButton exportButton = new GuiButton(buttonPanel)
            .setI18nLabel("worldexport.gui.export")
            .setSize(100, 20)
            .onClick(this::export);

    public final GuiButton cancelButton = new GuiButton(buttonPanel)
            .setI18nLabel("replaymod.gui.cancel")
            .setSize(100, 20)
            .onClick(this::close);

    public final GuiButton boundsButton = new GuiButton(buttonPanel)
            .setLabel("Bounds Editor")
            .setSize(100, 20)
            .onClick(this::openBoundsEditor);

    public final GuiPanel mainPanel = new GuiPanel()
            .addElements(new GridLayout.Data(1, 0.5),
                    new GuiLabel().setI18nText("replaymod.gui.rendersettings.outputfile"), outputFileButton,
                    viewDistanceSlider, lowerDepthSlider,
                    new GuiLabel().setI18nText("worldexport.gui.export.fluid_mode"), fluidModeDropdown)
            .setLayout(new GridLayout().setCellsEqualSize(false).setColumns(2).setSpacingX(5).setSpacingY(5));

    {
        settingsList.getListPanel().setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(0.5),
                        new GuiLabel().setI18nText("worldexport.gui.export.title"),
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
        viewDistanceSlider.setValue(viewDistance - MIN_VIEW_DISTANCE);
    }

    public int getViewDistance() {
        return viewDistanceSlider.getValue() + MIN_VIEW_DISTANCE;
    }

    public void setFluidMode(FluidMode fluidMode) {
        fluidModeDropdown.setSelected(fluidMode);
    }

    public FluidMode getFluidMode() {
        return fluidModeDropdown.getSelectedValue();
    }

    public GuiExportSettings(ReplayHandler replayHandler, Timeline timeline) {
        this.replayHandler = replayHandler;
        this.timeline = timeline;

        setBackground(Background.NONE);

        contentPanel.setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel container, int width, int height) {
                size(settingsList, width, height - height(buttonPanel) - 25);
                pos(settingsList, width / 2 - width(settingsList) / 2, 5);
                pos(buttonPanel, width / 2 - width(buttonPanel) / 2, y(settingsList) + height(settingsList) + 10);
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> container) {
                // ReadableDimension screenSize = GuiExportSettings.this.getMinSize();
                // return new Dimension(screenSize.getWidth() - 40, screenSize.getHeight() - 80);
                return super.calcMinSize(container);
            }
            
        });

        this.setLayout(new CustomLayout<GuiScreen>() {

            @Override
            protected void layout(GuiScreen screen, int width, int height) {
                width(contentPanel, width - 40);
                height(contentPanel, height - 40);
                pos(contentPanel, 20, 20);
            }
            
        });

        ReplayExportSettings settings;
        try {
            settings = ReplayExportSettings.readFromFile(replayHandler.getReplayFile());
        } catch (IOException e) {
            LogManager.getLogger().error("Error reading export settings from file.", e);
            settings = null;
        }

        if (settings != null) {
            applySettings(settings);
        } else {
            minLowerDepth = client.world.getBottomSectionCoord();
            maxLowerDepth = client.world.getTopSectionCoord();
            
            setOutputFile(generateOutputFile());
            setViewDistance(Math.min(client.options.getClampedViewDistance(), 8));
            setLowerDepth(minLowerDepth);
        }
    }

    protected File generateOutputFile() {
        String fileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        File folder = ReplayModRender.instance.getVideoFolder();
        return new File(folder, fileName+".replay");
    }

    public void openBoundsEditor() {
        MinecraftClient client = getMinecraft();
        int radius = client.options.getClampedViewDistance();
        ChunkPos centerPos = client.getCameraEntity().getChunkPos();

        GuiBoundsEditor editor = new GuiBoundsEditor(this, client.world,
                radius * 2, radius * 2,
                new ChunkPos(centerPos.x - radius, centerPos.z - radius));

        editor.getOverview().setPanOffset(new Vec2f(0, -10));

        editor.open();
    }

    @Override
    @SuppressWarnings("null") // WHY does my null checker think this is dangerous?
    public void close() {
        try {
            ReplayExportSettings.writeToFile(replayHandler.getReplayFile(), readSettings());
        } catch (IOException e) {
            LogManager.getLogger().error("Error saving export settings to file.", e);
        }

        if (prevScreen != null) {
            // We need to finish exiting out of this screen before we call the next one.
            RenderSystem.recordRenderCall(prevScreen::display);
        } else {
            client.setScreen(null);
        }
    }
    
}
