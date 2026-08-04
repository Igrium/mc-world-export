package com.igrium.worldexport.compat.replaymod.gui;

import com.igrium.worldexport.compat.replaymod.CustomPipelines;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.*;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.CrashReport;
import net.minecraft.core.SectionPos;
import org.apache.logging.log4j.LogManager;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GuiExportSettings extends AbstractGuiPopup<GuiExportSettings> {

    public final GuiPanel contentPanel = new GuiPanel(popup).setBackgroundColor(new Color(0, 0, 0, 230));
    public final GuiVerticalList settingsList = new GuiVerticalList(contentPanel).setDrawSlider(true);
    public final GuiPanel buttonPanel = new GuiPanel(contentPanel).setLayout(new HorizontalLayout().setSpacing(4));

    private final ReplayHandler replayHandler;
    private final Timeline timeline;
    private final AbstractGuiScreen<?> screen;

    private final Minecraft client = Minecraft.getInstance();

    private int minLowerDepth = 0;
    private int maxLowerDepth = 16;

    private final int minViewDistance = 1;
    private final int minEntityDistance = 0;
    private final int minUpdateDistance = 0;

    @Getter
    private File outputFile;

    public final GuiButton outputFileButton = new GuiButton().setMinSize(new Dimension(0, 20)).onClick(new Runnable() {
        public void run() {
            File parentFile = outputFile.getParentFile();
            if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
                LoggerFactory.getLogger(GuiExportSettings.class).error("Error creating output directory.");
            }

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

    public void setViewDistance(int viewDistance) {
        viewDistanceSlider.setValue(viewDistance - minViewDistance);
    }

    public int getViewDistance() {
        return viewDistanceSlider.getValue() + minViewDistance;
    }

    public final GuiSlider entityDistanceSlider = new GuiSlider()
            .onValueChanged(this::setEntitySliderDistanceText).setSize(122, 20).setSteps(32 - minEntityDistance);

    private void setEntitySliderDistanceText() {
        String prefix = "Entity Radius (Chunks): ";
        int distance = getEntityDistance();
        String suffix = distance > 0 ? String.valueOf(distance) : "[use view distance]";
        entityDistanceSlider.setText(prefix + suffix);
    }

    public void setEntityDistance(int entityDistance) {
        entityDistanceSlider.setValue(entityDistance - minEntityDistance);
    }

    public int getEntityDistance() {
        return entityDistanceSlider.getValue() + minEntityDistance;
    }

    public final GuiSlider updateDistanceSlider = new GuiSlider()
            .onValueChanged(this::setUpdateSliderDistanceText).setSize(122, 20).setSteps(32 - minUpdateDistance);

    private void setUpdateSliderDistanceText() {
        String prefix = "Update Radius: ";
        int distance = getUpdateDistance();
        String suffix = distance > 0 ? String.valueOf(distance) : "[use export radius]";
        updateDistanceSlider.setText(prefix + suffix);
    }

    public void setUpdateDistance(int updateDistance) {
        updateDistanceSlider.setValue(updateDistance - minUpdateDistance);
    }

    public int getUpdateDistance() {
        return updateDistanceSlider.getValue() + minUpdateDistance;
    }

    public final GuiSlider lowerDepthSlider = new GuiSlider().onValueChanged(new Runnable() {
        public void run() {
            lowerDepthSlider.setText("Lower Depth: " + getLowerDepth() * 16);
        };
    }).setSize(122, 20).setSteps(32);

    public void setLowerDepth(int lowerSectionCoord) {
        lowerDepthSlider.setSteps(maxLowerDepth - minLowerDepth);
        lowerDepthSlider.setValue(lowerSectionCoord - minLowerDepth);
    }

    public int getLowerDepth() {
        return lowerDepthSlider.getValue() + minLowerDepth;
    }

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
                    viewDistanceSlider, entityDistanceSlider, updateDistanceSlider, lowerDepthSlider)
            .setLayout(new GridLayout().setCellsEqualSize(false).setColumns(2).setSpacingX(5).setSpacingY(5));

    {
        settingsList.getListPanel().setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(0.5),
                        new GuiLabel().setText("Replay Export Settings"),
                        mainPanel);
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        outputFileButton.setLabel(outputFile.getName());
    }

    public GuiExportSettings(AbstractGuiScreen<?> container, ReplayHandler replayHandler, Timeline timeline) {
        super(container);
        disablePopupBackground();

        this.replayHandler = replayHandler;
        this.timeline = timeline;
        this.screen = container;

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

        minLowerDepth = client.level.getMinSectionY();
        maxLowerDepth = client.level.getMaxSectionY();

        setOutputFile(generateOutputFile());
        setViewDistance(client.options.getEffectiveRenderDistance());
        setEntityDistance(0);
        setUpdateDistance(0);
        setLowerDepth(minLowerDepth);
    }

    public void export() {
        close();
        RenderSettings settings = new RenderSettings(RenderSettings.RenderMethod.BLEND, RenderSettings.EncodingPreset.BLEND, 1920, 1080, 20, 100,
                outputFile, false, false, false, false, false, null, 360, 180, false, false, false, RenderSettings.AntiAliasing.NONE,
                "", "", false);

        LocalPlayer player = client.player;
        SectionPos center = player != null ? SectionPos.of(player.blockPosition()) : SectionPos.of(0,0,0);

        // maxLowerDepth is the top section coordinate, inclusive.
        int minSectionY = getLowerDepth();
        int height = maxLowerDepth + 1 - minSectionY;

        var builder = ReplayExportSettings.builder()
                .exportPath(outputFile.toPath())
                .bounds(ChunkSectionBox.fromRadius(center, getViewDistance(), minSectionY, height))
                .offset(center.origin().multiply(-1));

        if (getEntityDistance() > 0) {
            builder.entityBounds(ChunkSectionBox.fromRadius(center, getEntityDistance(), minSectionY, height).toBox());
        }

        if (getUpdateDistance() > 0) {
            builder.updateBounds(ChunkSectionBox.fromRadius(center, getUpdateDistance(), minSectionY, height));
        }

        CustomPipelines.replayExportSettings = builder.build();

        try {
            VideoRenderer renderer = new VideoRenderer(settings, replayHandler, timeline);
            renderer.renderVideo();
        } catch (Throwable e) {
            Utils.error(LogManager.getLogger("Replay Export"), this, CrashReport.forThrowable(e, "Exporting Replay"), () -> {});
            screen.display();
        }

    }

    protected File generateOutputFile() {
        String fileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        File folder = FabricLoader.getInstance().getGameDir().resolve("replay_exports").toFile();
        return new File(folder, fileName+".replay");
    }

    @Override
    protected GuiExportSettings getThis() {
        return this;
    }

    public static GuiScreen createBaseScreen() {
        GuiScreen screen = new GuiScreen();
        screen.setBackground(AbstractGuiScreen.Background.NONE);
        return screen;
    }

    // Access widen
    @Override
    public void open() {
        super.open();
    }
}
