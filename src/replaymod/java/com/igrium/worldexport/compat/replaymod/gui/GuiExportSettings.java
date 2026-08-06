package com.igrium.worldexport.compat.replaymod.gui;

import com.igrium.worldexport.compat.replaymod.export.ReplayExporter;
import com.igrium.worldexport.compat.replaymod.gui.GuiBoundsEditor.EditMode;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.*;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.CrashReport;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Objects;

public class GuiExportSettings extends AbstractGuiPopup<GuiExportSettings> {

    private static final Logger LOGGER = LogManager.getLogger("WorldExport/GuiExportSettings");

    public final GuiPanel contentPanel = new GuiPanel(popup).setBackgroundColor(new Color(0, 0, 0, 230));
    public final GuiVerticalList settingsList = new GuiVerticalList(contentPanel).setDrawSlider(true);
    public final GuiPanel buttonPanel = new GuiPanel(contentPanel).setLayout(new HorizontalLayout().setSpacing(4));

    private final ReplayHandler replayHandler;
    private final Timeline timeline;
    private final AbstractGuiScreen<?> screen;

    private final Minecraft client = Minecraft.getInstance();

    /**
     * Where the player was when this screen opened. The default bounds and the export offset are both
     * derived from this, so that they stay consistent if the camera moves before exporting.
     */
    private final SectionPos exportCenter;

    @Getter
    private File outputFile;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsWorld;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsUpdate;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsEntity;

    public final GuiButton outputFileButton = new GuiButton().setMinSize(new Dimension(0, 20)).onClick(new Runnable() {
        public void run() {
            try {
                createOutputDir();
            } catch (IOException e) {
                LOGGER.error("Error creating output directory.", e);
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

    public final GuiButton boundsEditorButton = new GuiButton().setI18nLabel("worldexport.gui.export.edit_bounds")
            .setMinSize(new Dimension(122, 20)).onClick(this::openBoundsEditor);

    public void openBoundsEditor() {
        var level = client.level;
        if (level == null) return;

        // How far the editor's map extends from the center, in chunks.
        int mapRadius = Math.max(client.options.getEffectiveRenderDistance(), 1) * 2;

        EnumMap<EditMode, ChunkSectionBox> map = new EnumMap<>(EditMode.class);
        map.put(EditMode.WORLD, boundsWorld);
        map.put(EditMode.UPDATE, boundsUpdate);
        map.put(EditMode.ENTITY, boundsEntity);

        GuiBoundsEditor.openEditor(map, this, level, mapRadius * 2, mapRadius * 2,
                new ChunkPos(exportCenter.x() - mapRadius, exportCenter.z() - mapRadius)).thenAccept(eMap -> {
            for (var entry : eMap.entrySet()) {
                switch (entry.getKey()) {
                    case WORLD -> boundsWorld = entry.getValue();
                    case ENTITY -> boundsEntity = entry.getValue();
                    case UPDATE -> boundsUpdate = entry.getValue();
                }
            }
        });
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
                    new GuiLabel().setI18nText("worldexport.gui.export.bounds"), boundsEditorButton)
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

        int minSection = Objects.requireNonNull(client.level).getMinSectionY();
        int maxSection = client.level.getMaxSectionY();

        LocalPlayer player = client.player;
        exportCenter = player != null ? SectionPos.of(player.blockPosition()) : SectionPos.of(0, 0, 0);

        boundsWorld = ChunkSectionBox.from(exportCenter.x() - 4, minSection, exportCenter.z() - 4,
                exportCenter.x() + 4, maxSection, exportCenter.z() + 4);

        boundsEntity = boundsWorld;
        boundsUpdate = boundsWorld;

        setOutputFile(generateOutputFile());
    }

    public void export() {
        close();

        var builder = ReplayExportSettings.builder()
                .exportPath(outputFile.toPath())
                .worldBounds(boundsWorld)
                .entityBounds(boundsEntity.toBox())
                .updateBounds(boundsUpdate)
                .offset(exportCenter.origin().multiply(-1));

        ReplayExportSettings exportSettings = builder.build();

        try {
            createOutputDir();
            ReplayExporter exporter = new ReplayExporter(exportSettings, replayHandler, timeline);
            exporter.exportReplay();
        } catch (Throwable e) {
            // This popup is already closed, so the error has to attach to the screen behind it.
            screen.display();
            Utils.error(LOGGER, screen, CrashReport.forThrowable(e, "Exporting Replay"), () -> {});
        }

    }

    private void createOutputDir() throws IOException {
        Path parent = outputFile.toPath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
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
