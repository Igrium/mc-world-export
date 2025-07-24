package com.igrium.worldexport.debugger;

import com.igrium.craftui.app.CraftApp;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.craftui.screen.CraftAppScreen;
import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.replay.CompiledReplay;
import com.igrium.worldexport.replay.ReplayIO;
import de.javagl.obj.Mtl;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReplayDebugger extends CraftApp {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReplayDebugger.class);

    public static void registerMenuButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen tScreen) {
                Screens.getButtons(screen).add(ButtonWidget
                        .builder(Text.translatable("menu.worldexport.replaydebugger"), (b) -> open(client))
                        .build());
            }
        });
    }

    public static CraftAppScreen<ReplayDebugger> open(MinecraftClient client) {
        var screen = new CraftAppScreen<>(new ReplayDebugger());
        client.setScreen(screen);
        return screen;
    }

    public record ModelPartReference(String entity, String part) {
        public List<AnimationCurve> getCurves(Map<? extends String, ? extends CapturedEntity> entities) {
            CapturedEntity e = entities.get(entity);
            if (e != null) {
                var curves = e.getCurves().get(part);
                if (curves != null)
                    return curves;
            }
            return List.of();
        }
    }

    public record MaterialSelectionReference(String mtlLib, int index) {
        @Nullable
        public Mtl get(Map<? super String, ? extends List<? extends Mtl>> mtlLibs) {
            var mtlList = mtlLibs.get(mtlLib);
            if (mtlList != null) {
                if (index >= 0 && index < mtlList.size()) {
                    return mtlList.get(index);
                }
            }
            return null;
        }

        public static final MaterialSelectionReference EMPTY = new MaterialSelectionReference("", 0);
    }

    @Getter @Nullable
    private CompiledReplay replay;

    @Getter @Setter @NonNull
    private MaterialSelectionReference selectedMaterial;

    @Getter
    private final Set<ModelPartReference> selectedModelParts = new HashSet<>();

    private final ReplayOutlinerWindow outliner = new ReplayOutlinerWindow(this);
    private final MtlInspectorWindow mtlInspector = new MtlInspectorWindow(this);
    private final CurveViewerWindow curveViewer = new CurveViewerWindow(this);

    @Override
    protected void render(MinecraftClient minecraftClient) {
        ImGui.begin("Outliner", ImGuiWindowFlags.MenuBar);
        // MENU BAR
        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Open Unpacked Replay"))
                    showFolderSelector();

                if (ImGui.menuItem("Close"))
                    close();

                ImGui.endMenu();
            }
            ImGui.endMenuBar();
        }

        if (replay != null) {
            outliner.drawOutliner();
        }

        ImGui.end();

        ImGui.begin("Material Inspector");
        mtlInspector.drawMtlInspector();
        ImGui.end();

        ImGui.begin("Curve Viewer");
        curveViewer.drawCurveViewer();
        ImGui.end();
    }

    private void showFolderSelector() {
        FileDialogs.showOpenFolderDialog(FabricLoader.getInstance().getGameDir().toString())
                .thenAccept(opt -> opt.ifPresent(s -> loadReplayFolder(Paths.get(s))));
    }

    public void openReplay(CompiledReplay replay) {
        setSelectedMaterial(MaterialSelectionReference.EMPTY);
        selectedModelParts.clear();
        this.replay = replay;

    }

    public void loadReplayFolder(Path replayRoot) {
        LOGGER.info("Opening replay from {}", replayRoot);
        long startTime = Util.getMeasuringTimeMs();
        ReplayIO.loadReplayAsync(replayRoot, Util.getMainWorkerExecutor())
                .thenAcceptAsync(this::openReplay, MinecraftClient.getInstance())
                .thenRun(() -> LOGGER.info("Opened replay in {}ms", Util.getMeasuringTimeMs() - startTime))
                .exceptionally(e -> {
                    LOGGER.error("Error opening replay.", e);
                    return null;
                });
    }
}
