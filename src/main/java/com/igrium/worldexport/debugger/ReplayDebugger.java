package com.igrium.worldexport.debugger;

import com.igrium.craftui.app.CraftApp;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.craftui.screen.CraftAppScreen;
import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.replay.CompiledReplay;
import com.igrium.worldexport.replay.ReplayIO;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlWriter;
import de.javagl.obj.Mtls;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

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

    @Getter
    private final Set<CurveSelectionReference> selectedCurveRefs = new HashSet<>();

    public record DirectChannelRef(AnimationCurve curve, int index) {};

    public Stream<DirectChannelRef> getSelectedCurves() {
        if (replay == null) {
            return Stream.empty();
        }
        return selectedCurveRefs.stream().map(ref ->
                new DirectChannelRef(ref.getCurve(replay.getEntities()), ref.channelIndex()));
    }

    @Getter @Setter @NonNull
    private MaterialSelectionReference selectedMaterial = MaterialSelectionReference.EMPTY;

    private final Map<Mtl, ImString> serializedMtlCache = new WeakHashMap<>();
    private final ImString emptyString = new ImString("");

    @Override
    protected void render(MinecraftClient minecraftClient) {
        ImGui.begin("Replay Debugger", ImGuiWindowFlags.MenuBar);
        drawMenuBar();

        if (showErrorPopup) {
            drawErrorPopup();
        }

        if (replay != null) {
            if (ImGui.treeNodeEx("Materials", ImGuiTreeNodeFlags.DefaultOpen)) {
                drawMtlList();
                ImGui.treePop();
            }
            ImGui.separator();

            if (ImGui.treeNodeEx("World Meshes", ImGuiTreeNodeFlags.DefaultOpen)) {
                drawWorldMeshTree();
                ImGui.treePop();
            }
            ImGui.separator();

            if (ImGui.treeNodeEx("Entities", ImGuiTreeNodeFlags.DefaultOpen)) {
                drawEntityTree();
                ImGui.treePop();
            }
            ImGui.separator();

        } else {
            ImGui.text("Please open a replay.");
        }

        ImGui.end();

        if (replay != null) {
            ImGui.begin("Animation Curves");
            drawCurves();
            ImGui.end();

            ImGui.begin("MTL Inspector");
            drawMtlInspector();
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

    private void drawMtlList() {
        if (replay == null)
            return;

        for (var mtlEntry : replay.getMtlLibs().entrySet()) {
            if (ImGui.treeNodeEx(mtlEntry.getKey())) {
                int mtlIndex = 0;
                for (var mtl : mtlEntry.getValue()) {
                    int flags = ImGuiTreeNodeFlags.Leaf;
                    if (selectedMaterial.mtlLib().equals(mtlEntry.getKey())
                            && selectedMaterial.index() == mtlIndex) {
                        flags |= ImGuiTreeNodeFlags.Selected;
                    }

                    boolean nodeOpen = ImGui.treeNodeEx(mtl.getName(), flags);
                    if (ImGui.isItemClicked()) {
                        selectedMaterial = new MaterialSelectionReference(mtlEntry.getKey(), mtlIndex);
                    }
                    if (nodeOpen)
                        ImGui.treePop();

                    mtlIndex++;
                }
                ImGui.treePop();
            }
        }
    }

    private void drawMtlInspector() {
        if (replay == null)
            return;

        ImString text;
        Mtl selected = selectedMaterial.get(replay.getMtlLibs());
        if (selected != null) {
            text = serializedMtlCache.computeIfAbsent(selected, m -> {
                StringWriter writer = new StringWriter();
                try {
                    MtlWriter.write(List.of(m), writer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return new ImString(writer.toString());
            });
        } else {
            text = emptyString;
        }

        ImGui.inputTextMultiline("##MTL data", text, ImGui.getWindowWidth(), ImGui.getWindowHeight() -48 , ImGuiInputTextFlags.ReadOnly);
    }

    private void drawWorldMeshTree() {
        if (replay == null)
            return;

        for (var meshEntry : replay.getWorldMeshes().entrySet()) {
            if (ImGui.treeNodeEx(meshEntry.getKey())) {
                var meta = meshEntry.getValue().meta();
                ImGui.text("Num faces: " + meshEntry.getValue().obj().getNumFaces());

                ImGui.text("Start tick: " + meta.getStartTick());
                ImGui.text("End tick: " + meta.getEndTick());
                ImGui.text("3D offset: [%f, %f, %f]".formatted(meta.getOffset().x, meta.getOffset().y, meta.getOffset().z));

                ImGui.treePop();
            }
        }
    }

    private void drawEntityTree() {
        if (replay == null)
            return;

        ImGuiIO io = ImGui.getIO();
        boolean shiftPressed = io.getKeyShift();

        for (var entEntry : replay.getEntities().entrySet()) {
            int flags = ImGuiTreeNodeFlags.OpenOnArrow;

            int entityFlags = flags;
            if (CurveSelectionReference.isEntitySelected(selectedCurveRefs, entEntry.getKey()))
                entityFlags |= ImGuiTreeNodeFlags.Selected;

            boolean showPartTree = ImGui.treeNodeEx(entEntry.getKey(), entityFlags);
            if (ImGui.isItemClicked()) {
                selectAllCurves(entEntry.getKey(), entEntry.getValue(), !shiftPressed);
            }
            // Model Part
            if (showPartTree) {
                for (var partEntry : entEntry.getValue().getCurves().entrySet()) {

                    int partFlags = flags;
                    if (CurveSelectionReference.isModelPartSelected(selectedCurveRefs, entEntry.getKey(), partEntry.getKey()))
                        partFlags |= ImGuiTreeNodeFlags.Selected;

                    boolean showCurveTree = ImGui.treeNodeEx(partEntry.getKey(), partFlags);
                    if (ImGui.isItemClicked()) {
                        selectAllPartCurves(entEntry.getKey(), partEntry.getKey(), partEntry.getValue(), !shiftPressed);
                    }
                    // Curve
                    if (showCurveTree) {
                        int curveIndex = 0;
                        for (var curve : partEntry.getValue()) {

                            int curveFlags = flags;
                            if (CurveSelectionReference.isCurveIndexSelected(selectedCurveRefs, entEntry.getKey(), partEntry.getKey(), curveIndex))
                                curveFlags |= ImGuiTreeNodeFlags.Selected;

                            boolean showChannelTree = ImGui.treeNodeEx("Curve " + curveIndex, curveFlags);
                            if (ImGui.isItemClicked()) {
                                selectAllChannels(entEntry.getKey(), partEntry.getKey(), curveIndex, !shiftPressed);
                            }
                            // Channel
                            if (showChannelTree) {
                                ImGui.text("Frame Offset: " + curve.getFrameOffset());
                                ImGui.text("Curve Length: " + curve.size());

                                for (int i = 0; i < AnimationCurve.NUM_CHANNELS; i++) {
                                    CurveSelectionReference channelRef = new CurveSelectionReference(entEntry.getKey(), partEntry.getKey(), curveIndex, i);

                                    int channelFlags = flags | ImGuiTreeNodeFlags.Leaf;
                                    if (selectedCurveRefs.contains(channelRef))
                                        channelFlags |= ImGuiTreeNodeFlags.Selected;

                                    boolean openedLeaf = ImGui.treeNodeEx(AnimationCurve.nameFromCurveIndex(i), channelFlags);
                                    if (ImGui.isItemClicked()) {
                                        selectCurveChannel(channelRef, !shiftPressed);
                                    }
                                    if (openedLeaf) {
                                        ImGui.treePop();
                                    }

                                }
                                ImGui.treePop();
                            }
                            curveIndex++;
                        }

                        ImGui.treePop();
                    }
                }
                ImGui.treePop();
            }
        }

    }

    private void selectAllCurves(String entityName, CapturedEntity entity, boolean clear) {
        if (clear)
            selectedCurveRefs.clear();
        for (var partEntry : entity.getCurves().entrySet()) {
            selectAllPartCurves(entityName, partEntry.getKey(), partEntry.getValue(), false);
        }
    }

    private void selectAllPartCurves(String entityName, String partName, Collection<? super AnimationCurve> curves, boolean clear) {
        if (clear)
            selectedCurveRefs.clear();
        for (int i = 0; i < curves.size(); i++) {
            selectAllChannels(entityName, partName, i, false);
        }
    }

    private void selectAllChannels(String entityName, String partName, int curveIndex, boolean clear) {
        if (clear)
            selectedCurveRefs.clear();
        for (int i = 0; i < AnimationCurve.NUM_CHANNELS; i++) {
            selectedCurveRefs.add(new CurveSelectionReference(entityName, partName, curveIndex, i));
        }
    }

    private void selectCurveChannel(CurveSelectionReference ref, boolean clear) {
        if (clear)
            selectedCurveRefs.clear();
        selectedCurveRefs.add(ref);
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

            getSelectedCurves().forEach(r -> {
                double[] ticks = new double[r.curve.size()];
                for (int i = 0; i < ticks.length; i++) {
                    ticks[i] = i + r.curve.getFrameOffset();
                }

                drawLine(AnimationCurve.nameFromCurveIndex(r.index), ticks, r.curve.getCurve(r.index));
            });

            ImPlot.endPlot();
        }
        if (ImGui.button("Reset View"))
            fitPlot = true;

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
        selectedCurveRefs.clear();
        setSelectedMaterial(MaterialSelectionReference.EMPTY);
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
