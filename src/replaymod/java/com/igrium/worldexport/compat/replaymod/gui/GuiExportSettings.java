package com.igrium.worldexport.compat.replaymod.gui;

import com.igrium.worldexport.compat.replaymod.SavedExportSettings;
import com.igrium.worldexport.compat.replaymod.export.ReplayExporter;
import com.igrium.worldexport.compat.replaymod.gui.GuiBoundsEditor.EditMode;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.igrium.worldexport.util.CompatChecker;
import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.*;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiNumberField;
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
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.CrashReport;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public class GuiExportSettings extends AbstractGuiPopup<GuiExportSettings> {

    private static final Logger LOGGER = LogManager.getLogger("WorldExport/GuiExportSettings");

    private final Minecraft client = Minecraft.getInstance();

    private final ReplayHandler replayHandler;
    private final Timeline timeline;
    private final AbstractGuiScreen<?> screen;

    // === Export state ===

    @Getter
    private File outputFile;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsWorld;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsUpdate;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsEntity;

    // === Output file ===

    private final GuiButton outputFileButton = new GuiButton()
            .setMinSize(new Dimension(0, 20))
            .onClick(this::chooseOutputFile);

    // === Bounds ===

    private final GuiButton boundsEditorButton = new GuiButton()
            .setI18nLabel("worldexport.gui.export.edit_bounds")
            .setMinSize(new Dimension(122, 20))
            .onClick(this::openBoundsEditor);

    // === Export center ===

    private final GuiNumberField centerXField = createCenterField();
    private final GuiNumberField centerYField = createCenterField();
    private final GuiNumberField centerZField = createCenterField();

    private final GuiPanel centerFieldPanel = new GuiPanel()
            .setLayout(new HorizontalLayout().setSpacing(4))
            .addElements(new HorizontalLayout.Data(0.5), centerXField, centerYField, centerZField);

    private final GuiButton useCameraPosButton = new GuiButton()
            .setI18nLabel("worldexport.gui.export.use_camera_pos")
            .setMinSize(new Dimension(122, 20))
            .onClick(this::setCenterFromCamera);

    private final GuiPanel exportCenterPanel = new GuiPanel()
            .setLayout(new VerticalLayout().setSpacing(4))
            .addElements(new VerticalLayout.Data(0.5), centerFieldPanel, useCameraPosButton);

    private static GuiNumberField createCenterField() {
        return new GuiNumberField().setValidateOnFocusChange(true).setSize(38, 20);
    }

    // === FIELDS ===

    private final GuiCheckbox exportWorld = new GuiCheckbox()
            .setI18nLabel("worldexport.gui.export.world")
            .setChecked(true)
            .onClick(this::updateExportUpdatesEnabled);

    public boolean isExportWorld() {
        return exportWorld.isChecked();
    }

    public void setExportWorld(boolean exportWorld) {
        this.exportWorld.setChecked(exportWorld);
    }

    private final GuiCheckbox exportUpdates = new GuiCheckbox()
            .setI18nLabel("worldexport.gui.export.updates")
            .setChecked(true);

    public boolean isExportUpdates() {
        return exportUpdates.isChecked();
    }

    public void setExportUpdates(boolean exportUpdates) {
        this.exportUpdates.setChecked(exportUpdates);
    }

    private void updateExportUpdatesEnabled() {
        exportUpdates.setEnabled(exportWorld.isChecked());
    }

    private final GuiCheckbox exportSpritesheets = new GuiCheckbox()
            .setI18nLabel("worldexport.gui.export.spritesheets")
            .setChecked(false); // Change to true once spritesheets are fixed

    public boolean isExportSpritesheets() {
        return exportSpritesheets.isChecked();
    }

    public void setExportSpritesheets(boolean exportSpritesheets) {
        this.exportSpritesheets.setChecked(exportSpritesheets);
    }

    private final GuiCheckbox exportEntities = new GuiCheckbox()
            .setI18nLabel("worldexport.gui.export.entities")
            .setChecked(true);

    public boolean isExportEntities() {
        return exportEntities.isChecked();
    }

    public void setExportEntities(boolean exportEntities) {
        this.exportEntities.setChecked(exportEntities);
    }
    
    private final GuiPanel flagsPanel = new GuiPanel()
            .setLayout(new VerticalLayout().setSpacing(4))
            .addElements(new VerticalLayout.Data(0), exportWorld, exportUpdates, exportSpritesheets, exportEntities);

    // === Settings list ===

    private final GuiPanel mainPanel = new GuiPanel()
            .setLayout(new GridLayout().setCellsEqualSize(false).setColumns(2).setSpacingX(5).setSpacingY(5))
            .addElements(new GridLayout.Data(1, 0.5),
                    new GuiLabel().setI18nText("replaymod.gui.rendersettings.outputfile"), outputFileButton,
                    new GuiLabel().setI18nText("worldexport.gui.export.bounds"), boundsEditorButton,
                    new GuiLabel().setI18nText("worldexport.gui.export.export_center"), exportCenterPanel)
            // Align label with the top of the row
            .addElements(new GridLayout.Data(1, 0), new GuiLabel().setI18nText("worldexport.gui.export.include"))
            // Left-align checkboxes
            .addElements(new GridLayout.Data(0, 0.5), flagsPanel);

    // === Popup root ===

    private final GuiPanel contentPanel = new GuiPanel(popup)
            .setBackgroundColor(new Color(0, 0, 0, 230));

    private final GuiVerticalList settingsList = new GuiVerticalList(contentPanel)
            .setDrawSlider(true);

    {
        settingsList.getListPanel().setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(0.5),
                        new GuiLabel().setText("Replay Export Settings"),
                        mainPanel);
    }

    // === Bottom buttons ===

    private final GuiPanel buttonPanel = new GuiPanel(contentPanel)
            .setLayout(new HorizontalLayout().setSpacing(4));

    private final GuiButton exportButton = new GuiButton(buttonPanel)
            .setLabel("Export")
            .setSize(100, 20)
            .onClick(this::export);

    private final GuiButton cancelButton = new GuiButton(buttonPanel)
            .setI18nLabel("replaymod.gui.cancel")
            .setSize(100, 20)
            .onClick(this::close);

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

        initState();
    }

    /**
     * Load saved settings (if any) and populate the initial field values.
     */
    private void initState() {
        SavedExportSettings saved = null;
        try {
            saved = SavedExportSettings.load(replayHandler.getReplayFile());
        } catch (Exception e) {
            LOGGER.error("Error reading export settings from replay file.", e);
        }

        int minSection = Objects.requireNonNull(client.level).getMinSectionY();
        int maxSection = client.level.getMaxSectionY();

        LocalPlayer player = client.player;
        var exportCenter = player != null ? SectionPos.of(player.blockPosition()) : SectionPos.of(0, 0, 0);
        if (saved != null && saved.exportCenter() != null) {
            exportCenter = saved.exportCenter();
        }
        setExportCenter(exportCenter);

        boundsWorld = ChunkSectionBox.from(exportCenter.x() - 4, minSection, exportCenter.z() - 4,
                exportCenter.x() + 4, maxSection, exportCenter.z() + 4);
        boundsEntity = boundsWorld;
        boundsUpdate = boundsWorld;

        if (saved != null) {
            if (saved.worldBounds() != null) boundsWorld = saved.worldBounds();
            if (saved.updateBounds() != null) boundsUpdate = saved.updateBounds();
            if (saved.entityBounds() != null) boundsEntity = saved.entityBounds();
            if (saved.exportWorld() != null) setExportWorld(saved.exportWorld());
            if (saved.exportUpdates() != null) setExportUpdates(saved.exportUpdates());
            if (saved.exportEntities() != null) setExportEntities(saved.exportEntities());
            if (saved.exportSpritesheets() != null) setExportSpritesheets(saved.exportSpritesheets());
        }
        updateExportUpdatesEnabled();

        setOutputFile(resolveOutputFile(saved));
    }

    // === Output file ===

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        outputFileButton.setLabel(outputFile.getName());
    }

    private void chooseOutputFile() {
        try {
            createOutputDir();
        } catch (IOException e) {
            LOGGER.error("Error creating output directory.", e);
        }

        GuiFileChooserPopup popup = GuiFileChooserPopup.openSaveGui(this, "replaymod.gui.save", "replay");
        popup.setFolder(outputFile.getParentFile());
        popup.setFileName(outputFile.getName());
        popup.onAccept(this::setOutputFile);
    }

    /**
     * Determine the output file to start with, preferring the one saved in the replay file.
     */
    private File resolveOutputFile(@Nullable SavedExportSettings saved) {
        if (saved == null || saved.outputFile() == null) return generateOutputFile();

        // GuiFileChooserPopup crashes if it opens on a folder that no longer exists.
        Path parent = saved.outputFile().getParent();
        if (parent == null || !Files.isDirectory(parent)) return generateOutputFile();

        return saved.outputFile().toFile();
    }

    private File generateOutputFile() {
        String fileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        File folder = FabricLoader.getInstance().getGameDir().resolve("replay_exports").toFile();
        return new File(folder, fileName + ".replay");
    }

    private void createOutputDir() throws IOException {
        Path parent = outputFile.toPath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    // === Bounds ===

    private void openBoundsEditor() {
        var level = client.level;
        if (level == null) return;

        // How far the editor's map extends from the center, in chunks.
        int mapRadius = Math.max(client.options.getEffectiveRenderDistance(), 1) * 2;

        EnumMap<EditMode, ChunkSectionBox> map = new EnumMap<>(EditMode.class);
        map.put(EditMode.WORLD, boundsWorld);
        map.put(EditMode.UPDATE, boundsUpdate);
        map.put(EditMode.ENTITY, boundsEntity);

        var exportCenter = getExportCenter();
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

    // === Export center ===

    public SectionPos getExportCenter() {
        return SectionPos.of(centerXField.getInteger(), centerYField.getInteger(), centerZField.getInteger());
    }

    public void setExportCenter(SectionPos center) {
        centerXField.setValue(center.getX());
        centerYField.setValue(center.getY());
        centerZField.setValue(center.getZ());
    }

    private void setCenterFromCamera() {
        LocalPlayer player = client.player;
        if (player != null) {
            setExportCenter(SectionPos.of(player.blockPosition()));
        }
    }

    // === Persistence & export ===

    /**
     * Capture the current state of this screen for saving.
     */
    public SavedExportSettings captureSettings() {
        return new SavedExportSettings(outputFile.toPath(), boundsWorld, boundsUpdate, boundsEntity, getExportCenter(),
                isExportWorld(), isExportUpdates(), isExportEntities(), isExportSpritesheets());
    }

    @Override
    public void close() {
        try {
            SavedExportSettings.save(replayHandler.getReplayFile(), captureSettings());
        } catch (Exception e) {
            LOGGER.error("Error saving export settings to replay file.", e);
        }
        super.close();
    }

    public void export() {

        ReplayExportSettings exportSettings = ReplayExportSettings.builder()
                .exportPath(outputFile.toPath())
                .worldBounds(boundsWorld)
                .entityBounds(boundsEntity.toBox())
                .updateBounds(boundsUpdate)
                .offset(getExportCenter().origin().multiply(-1))
                .exportWorld(exportWorld.isChecked())
                .exportUpdates(exportWorld.isChecked() && exportUpdates.isChecked())
                .exportEntities(exportEntities.isChecked())
                .build();

        List<ModMetadata> breaks = CompatChecker.checkModCompat();
        if (!breaks.isEmpty()) {
            GuiCompatWarning warning = new GuiCompatWarning(breaks);
            warning.setContCallback(() -> doExport(exportSettings));
            warning.setCancelCallback(screen::display);
            warning.display();
        } else {
            doExport(exportSettings);
        }
    }

    private void doExport(ReplayExportSettings settings) {
        close();
        try {
            createOutputDir();
            ReplayExporter exporter = new ReplayExporter(settings, replayHandler, timeline);
            exporter.exportReplay();
        } catch (Throwable e) {
            // This popup is already closed, so the error has to attach to the screen behind it.
            screen.display();
            Utils.error(LOGGER, screen, CrashReport.forThrowable(e, "Exporting Replay"), () -> {});
        }
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
