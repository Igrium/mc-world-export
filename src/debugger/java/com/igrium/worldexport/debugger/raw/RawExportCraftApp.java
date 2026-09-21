package com.igrium.worldexport.debugger.raw;

import com.igrium.craftui.api.app.CraftApp;
import com.igrium.craftui.api.file.FileDialogs;
import com.igrium.worldexport.compat.imgui.FileDialogCallback;
import com.igrium.worldexport.compat.imgui.ImGuiExportSettings;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RawExportCraftApp extends CraftApp {
    private final ImGuiExportSettings settings = new ImGuiExportSettings(this::showSaveDialog);

    @Override
    public void render(Minecraft minecraft) {
        ImGui.begin("Replay Export Settings");
        settings.drawSettings();
        ImGui.end();
    }

    private CompletableFuture<Optional<Path>> showSaveDialog(@Nullable Path defaultPath, @Nullable String defaultName, FileDialogCallback.FileFilter... filters) {
        var craftUiFilters = new FileDialogs.FileFilter[filters.length];
        for (int i = 0; i < filters.length; i++) {
            var filter = filters[i];
            craftUiFilters[i] = new FileDialogs.FileFilter(filter.name(), filter.extensions());
        }

        String dPathStr = defaultPath != null ? defaultPath.toString() : null;
        return FileDialogs.showSaveDialog(dPathStr, defaultName, craftUiFilters)
                .thenApply(opt -> opt.map(Paths::get));
    }

}
