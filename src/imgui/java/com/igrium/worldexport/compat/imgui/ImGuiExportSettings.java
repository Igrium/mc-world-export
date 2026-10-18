package com.igrium.worldexport.compat.imgui;


import com.igrium.worldexport.math.ChunkSectionBox;
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

public class ImGuiExportSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/ImGuiExportSettings");

    public enum ReturnState {
        NONE, CANCEL, EXPORT
    }

    private final Minecraft client = Minecraft.getInstance();

    @Getter @Setter
    private @NonNull Path outputFile = FabricLoader.getInstance().getGameDir().resolve("replay_exports");
    private final @Nullable FileDialogCallback fileDialogCallback;

    public ImGuiExportSettings(@Nullable FileDialogCallback fileDialogCallback) {
        this.fileDialogCallback = fileDialogCallback;
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

    private final ImString outPathStr = new ImString();

    private final ImBoolean exportWorld = new ImBoolean();

    public boolean isExportWorld() {
        return exportWorld.get();
    }

    public void setExportWorld(boolean value) {
        this.exportWorld.set(value);
    }

    private final ImBoolean exportUpdates = new ImBoolean();

    public boolean isExportUpdates() {
        return exportUpdates.get();
    }

    public void setExportUpdates(boolean value) {
        this.exportUpdates.set(value);
    }

    private final ImBoolean exportSpritesheets = new ImBoolean();

    public boolean isExportSpritesheets() {
        return exportSpritesheets.get();
    }

    public void setExportSpritesheets(boolean value) {
        this.exportSpritesheets.set(value);
    }

    private final ImBoolean exportEntities = new ImBoolean();

    public boolean isExportEntities() {
        return exportEntities.get();
    }

    public void setExportEntities(boolean value) {
        this.exportEntities.set(value);
    }

    public ReturnState drawSettings() {
        if (ImGui.inputTextWithHint("##outputFile", tt("worldexport.gui.export.outFile"), outPathStr)) {
            if (ImGui.isItemDeactivatedAfterEdit()) {
                try {
                    outputFile = Paths.get(outPathStr.get());
                } catch (InvalidPathException e) {
                    LOGGER.error("Invalid export path: {}", outPathStr.get(), e);
                }
            }
        } else {
            // Not the most efficient to do every frame, but whatever
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
            ImGui.checkbox(t("worldexport.gui.export.updates"), exportUpdates);
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
