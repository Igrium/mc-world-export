package com.igrium.worldexport.compat.imgui;


import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.SavedExportSettings;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImGuiExportSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/ImGuiExportSettings");

    public enum ReturnState {
        NONE, CANCEL, EXPORT
    }

    private final Minecraft client = Minecraft.getInstance();

    @Getter @Setter
    private @NonNull Path outputFile;

    private final @Nullable FileDialogCallback fileDialogCallback;

    public ImGuiExportSettings(@Nullable FileDialogCallback fileDialogCallback) {
        this.fileDialogCallback = fileDialogCallback;
        initState();
    }

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsWorld = ChunkSectionBox.ZERO;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsUpdate = ChunkSectionBox.ZERO;

    @Getter @Setter
    private @NonNull ChunkSectionBox boundsEntity = ChunkSectionBox.ZERO;

    @Getter
    private final int[] center = new int[3];

    public void setExportCenter(Vec3i center) {
        this.center[0] = center.getX();
        this.center[1] = center.getY();
        this.center[2] = center.getZ();
    }

    public SectionPos getExportCenter() {
        return SectionPos.of(center[0], center[1], center[2]);
    }

    private final ImString outPathStr = new ImString();

    private final ImBoolean exportWorld = new ImBoolean(true);

    public boolean isExportWorld() {
        return exportWorld.get();
    }

    public void setExportWorld(boolean value) {
        this.exportWorld.set(value);
    }

    private final ImBoolean exportUpdates = new ImBoolean(true);

    public boolean isExportUpdates() {
        return exportUpdates.get();
    }

    public void setExportUpdates(boolean value) {
        this.exportUpdates.set(value);
    }

    private final ImBoolean exportSpritesheets = new ImBoolean(true);

    public boolean isExportSpritesheets() {
        return exportSpritesheets.get();
    }

    public void setExportSpritesheets(boolean value) {
        this.exportSpritesheets.set(value);
    }

    private final ImBoolean exportEntities = new ImBoolean(true);

    public boolean isExportEntities() {
        return exportEntities.get();
    }

    public void setExportEntities(boolean value) {
        this.exportEntities.set(value);
    }

    /**
     * Populate the initial field values with sensible defaults
     */
    private void initState() {
        var level = client.level;
        int minSection = level != null ? level.getMinSectionY() : 0;
        int maxSection = level != null ? level.getMaxSectionY() : 0;

        LocalPlayer player = client.player;
        var exportCenter = player != null ? SectionPos.of(player.blockPosition()) : SectionPos.of(0, 0, 0);
        setExportCenter(exportCenter);

        boundsWorld = ChunkSectionBox.from(exportCenter.x() - 4, minSection, exportCenter.z() - 4,
                exportCenter.x() + 4, maxSection, exportCenter.z() + 4);
        boundsEntity = boundsWorld;
        boundsUpdate = boundsWorld;

        outputFile = generateOutputFile();
    }

    /**
     * Load the state of this window from a previously-captured settings object.
     */
    public void loadSettings(SavedExportSettings saved) {
        if (saved.getOutputFile() != null) setOutputFile(saved.getOutputFile());
        setBoundsWorld(saved.getWorldBounds());
        setBoundsUpdate(saved.getUpdateBounds());
        setBoundsEntity(saved.getEntityBounds());
        setExportCenter(saved.getExportCenter());

        setExportWorld(saved.isExportWorld());
        setExportUpdates(saved.isExportUpdates());
        setExportEntities(saved.isExportEntities());
        setExportSpritesheets(saved.isExportSpritesheets());
    }

    /**
     * Capture the current state of this window for saving.
     */
    public SavedExportSettings captureSettings() {
        SavedExportSettings settings = new SavedExportSettings();
        settings.setOutputFile(outputFile);
        settings.setWorldBounds(boundsWorld);
        settings.setUpdateBounds(boundsUpdate);
        settings.setEntityBounds(boundsEntity);
        settings.setExportCenter(getExportCenter());

        settings.setExportWorld(isExportWorld());
        settings.setExportUpdates(isExportUpdates());
        settings.setExportEntities(isExportEntities());
        settings.setExportSpritesheets(isExportSpritesheets());
        return settings;
    }

    private static Path generateOutputFile() {
        String fileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        Path folder = FabricLoader.getInstance().getGameDir().resolve("replay_exports");
        return folder.resolve(fileName + ".replay");
    }

    public ReturnState drawSettings() {
        ImGui.inputTextWithHint("##outputFile", tt("worldexport.gui.export.outFile"), outPathStr);
        if (ImGui.isItemDeactivatedAfterEdit()) {
            try {
                outputFile = Paths.get(outPathStr.get());
            } catch (InvalidPathException e) {
                LOGGER.error("Invalid export path: {}", outPathStr.get(), e);
            }
        } else if (!ImGui.isItemActive()) {
            outPathStr.set(outputFile.toString());
        }

        ImGui.sameLine();
        if (ImGui.button(t("worldexport.gui.export.browse")) && fileDialogCallback != null) {
            fileDialogCallback.showSaveDialog(outputFile.getParent(), outputFile.getFileName().toString()).thenAccept(result -> {
                result.ifPresent(path -> outputFile = path);
            });
        }

        ImGui.separator();

        if (ImGui.button(t("worldexport.gui.export.edit_bounds"))) {
            // export bounds
        }

        ImGui.inputInt3(t("worldexport.gui.export.export_center"), center);

        if (ImGui.beginPopupContextItem("##export_center_context")) {

            if (ImGui.menuItem(t("worldexport.gui.export.use_camera_pos"))) {
                setCenterFromCamera();
            }

            ImGui.endPopup();
        }

        if (ImGui.treeNodeEx(t("worldexport.gui.export.include"), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.checkbox(t("worldexport.gui.export.world"), exportWorld);

            ImGui.beginDisabled(!exportWorld.get());
            ImGui.checkbox(t("worldexport.gui.export.updates"), exportUpdates);
            ImGui.endDisabled();

            ImGui.checkbox(t("worldexport.gui.export.spritesheets"), exportSpritesheets);
            ImGui.checkbox(t("worldexport.gui.export.entities"), exportEntities);

            ImGui.treePop();
        }

        ImGui.separator();

        ReturnState rState = ReturnState.NONE;
        if (ImGui.button(t("worldexport.gui.export"))) {
            rState = ReturnState.EXPORT;
        }
        ImGui.sameLine();
        if (ImGui.button(t("worldexport.gui.cancel"))) {
            rState = ReturnState.CANCEL;
        }

        return rState;
    }

    private void setCenterFromCamera() {
        LocalPlayer player = client.player;
        if (player != null) {
            setExportCenter(SectionPos.of(player.blockPosition()));
        }
    }

    private static String tt(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
