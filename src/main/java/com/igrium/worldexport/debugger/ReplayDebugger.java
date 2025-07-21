package com.igrium.worldexport.debugger;

import com.igrium.craftui.app.CraftApp;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.craftui.screen.CraftAppScreen;
import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.replay.CompiledReplay;
import com.igrium.worldexport.replay.ReplayIO;
import imgui.ImGui;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
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
import java.util.List;

public class ReplayDebugger extends CraftApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayDebugger.class);

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

    @Nullable
    @Getter
    private CompiledReplay replay;

    private boolean showErrorPopup;

    @Nullable
    private String selectedEntity;

    @Override
    protected void render(MinecraftClient minecraftClient) {
        ImGui.begin("Replay Debugger", ImGuiWindowFlags.MenuBar);
        drawMenuBar();

        if (showErrorPopup) {
            drawErrorPopup();
        }

        if (replay != null) {
            ImGui.text("Entities");
            ImGui.separator();
            drawEntityTree();
        } else {
            ImGui.text("Please open a replay.");
        }

        ImGui.end();

        if (replay != null) {
            ImGui.begin("Animation Curves");
            drawCurves();
            ImGui.end();
        }
    }

    private void drawMenuBar() {
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
    }

    private void drawEntityTree() {
        if (replay == null)
            return;

        for (var entEntry : replay.getEntities().entrySet()) {
            int flags = ImGuiTreeNodeFlags.OpenOnArrow;
            if (entEntry.getKey().equals(selectedEntity)) flags |= ImGuiTreeNodeFlags.Selected;

            boolean showTree = ImGui.treeNodeEx(entEntry.getKey(), flags);
            if (ImGui.isItemClicked()) {
                selectedEntity = entEntry.getKey();
            }
            if (showTree) {
                ImGui.treePop();
            }
        }
    }

    private void drawErrorPopup() {
        if (ImGui.beginPopupModal("Error")) {
            ImGui.text("Error opening replay. See console for details.");
            if (ImGui.button("OK")) {
                showErrorPopup = false;
            }
        }
    }

    private boolean fitPlot;

    private void drawCurves() {

        if (fitPlot) {
            ImPlot.fitNextPlotAxes();
            fitPlot = false;
        }
        if (ImPlot.beginPlot("Animation Curves")) {
            if (replay != null && selectedEntity != null) {
                var entity = replay.getEntities().get(selectedEntity);
                entity.getCurves().values().stream().flatMap(List::stream).forEach(this::drawCurve);
            }
            ImPlot.endPlot();
        }
        if (ImGui.button("Reset View"))
            fitPlot = true;

    }

    private void drawCurve(AnimationCurve curve) {
        double[] ticks = new double[curve.size()];
        int offset = curve.getFrameOffset();
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = i + offset;
        }

        drawLine("Location X", ticks, curve.getXPosCurve());
        drawLine("Location Y", ticks, curve.getYPosCurve());
        drawLine("Location Z", ticks, curve.getZPosCurve());

        drawLine("Rotation W", ticks, curve.getWRotCurve());
        drawLine("Rotation X", ticks, curve.getXRotCurve());
        drawLine("Rotation Y", ticks, curve.getYRotCurve());
        drawLine("Rotation Z", ticks, curve.getZRotCurve());

        drawLine("Scale X", ticks, curve.getXScaleCurve());
        drawLine("Scale Y", ticks, curve.getYScaleCurve());
        drawLine("Scale Z", ticks, curve.getZScaleCurve());
    }

    private void drawLine(String name, double[] xData, float[] yData) {
        ImPlot.plotLine(name, xData, convertArray(yData), xData.length, 0);
    }

    private static double[] convertArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }

    private void showFolderSelector() {
        FileDialogs.showOpenFolderDialog(FabricLoader.getInstance().getGameDir().toString()).thenAccept(opt -> {
            opt.ifPresent(s -> openReplayFolder(Paths.get(s)));
        });
    }

    public void openReplay(CompiledReplay replay) {
        selectedEntity = null;
        this.replay = replay;
    }

    public void openReplayFolder(Path replayRoot) {
        LOGGER.info("Opening replay from {}", replayRoot);
        long startTime = Util.getMeasuringTimeMs();
        ReplayIO.loadReplayAsync(replayRoot, Util.getMainWorkerExecutor())
                .thenAcceptAsync(this::openReplay, MinecraftClient.getInstance())
                .thenRun(() -> LOGGER.info("Opened replay in {}ms", Util.getMeasuringTimeMs() - startTime))
                .exceptionally(e -> {
                    LOGGER.error("Error opening replay.", e);
                    showErrorPopup = true;
                    return null;
                });
    }
}
