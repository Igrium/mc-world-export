package com.igrium.worldexport.debugger;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.replay.CompiledReplay;
import imgui.ImGui;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiTreeNodeFlags;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class CurveViewerWindow {

    private record CurveReference(ReplayDebugger.ModelPartReference modelPart, int curveIndex) {
        @Nullable
        public AnimationCurve getCurve(Map<? extends String, ? extends CapturedEntity> entities) {
            if (curveIndex < 0)
                return null;
            List<AnimationCurve> curves = modelPart.getCurves(entities);
            return curveIndex < curves.size() ? curves.get(curveIndex) : null;
        }
    }

    private final ReplayDebugger replayDebugger;

    private final Map<CurveReference, IntSet> selectedChannels = new HashMap<>();
    private boolean fitPlot;

    public CurveViewerWindow(ReplayDebugger replayDebugger) {
        this.replayDebugger = replayDebugger;
    }

    private Stream<ReplayDebugger.ModelPartReference> selectedModelParts() {
        return selectedChannels.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> entry.getKey().modelPart);
    }

    private boolean isCurveSelected(CurveReference curveReference) {
        IntSet set = selectedChannels.get(curveReference);
        return set != null && !set.isEmpty();
    }

    private boolean isChannelSelected(CurveReference curveReference, int channel) {
        IntSet set = selectedChannels.get(curveReference);
        return set != null && set.contains(channel);
    }

    private boolean isModelPartSelected(ReplayDebugger.ModelPartReference modelPartReference) {
        return selectedModelParts().anyMatch(modelPartReference::equals);
    }

    private void selectAllCurves(ReplayDebugger.ModelPartReference modelPart, List<AnimationCurve> curves, boolean clear) {
        if (clear) {
            selectedChannels.clear();
        }

        int curveIndex = 0;
        for (AnimationCurve curve : curves) {
            selectedChannels.put(new CurveReference(modelPart, curveIndex), new IntArraySet(curve.getChannelIndices()));
            curveIndex++;
        }
    }

    private void selectCurve(CurveReference ref, AnimationCurve curve, boolean clear) {
        if (clear) {
            selectedChannels.clear();
        }

        selectedChannels.put(ref, new IntArraySet(curve.getChannelIndices()));
    }

    private void selectChannel(CurveReference ref, int channel, boolean clear) {
        if (clear) {
            selectedChannels.clear();
            selectedChannels.put(ref, new IntArraySet(new int[]{channel}));
        } else {
            selectedChannels.compute(ref, (key, val) -> {
                if (val == null) {
                    return new IntArraySet(new int[]{channel});
                } else {
                    val.add(channel);
                    return val;
                }
            });
        }
    }

    public void drawCurveViewer() {
        CompiledReplay replay = replayDebugger.getReplay();
        if (replay == null)
            return;

        if (fitPlot) {
            ImPlot.fitNextPlotAxes();
            fitPlot = false;
        }

        if (ImPlot.beginPlot("Animation Curves")) {
            for (var selectionEntry : selectedChannels.entrySet()) {
                var curveRef = selectionEntry.getKey();
                var curve = curveRef.getCurve(replay.getEntities());
                if (curve == null) continue;

                double[] ticks = new double[curve.size()];
                for (int i = 0; i < ticks.length; i++) {
                    ticks[i] = i + curve.getFrameOffset();
                }

                String partPrefix = curveRef.modelPart.entity() + ":" + curveRef.modelPart.part() + " ";

                for (int index : selectionEntry.getValue()) {
                    drawLine(partPrefix + AnimationCurve.nameFromCurveIndex(index), ticks, curve.getChannel(index));
                }
            }
            ImPlot.endPlot();
        }

        if (ImGui.button("Reset View")) {
            fitPlot = true;
        }

        ImGui.separator();
        drawChannelTree(replay);

    }


    private void drawLine(String name, double[] xData, float[] yData) {
        ImPlot.plotLine(name, xData, convertArray(yData), xData.length, 0);
    }

    private void drawChannelTree(CompiledReplay replay) {
        boolean ctrlPressed = ImGui.getIO().getKeyCtrl();

        ImGui.beginChild("Channel Selection");

        // For each model part
        for (var modelPartRef : replayDebugger.getSelectedModelParts()) {
            List<AnimationCurve> curves = modelPartRef.getCurves(replay.getEntities());
            int partFlags = ImGuiTreeNodeFlags.OpenOnArrow;
            if (isModelPartSelected(modelPartRef))
                partFlags |= ImGuiTreeNodeFlags.Selected;

            String partLabel = modelPartRef.entity() + ": " + modelPartRef.part();
            boolean expandPart = ImGui.treeNodeEx(partLabel, partFlags);
            if (ImGui.isItemClicked()) {
                selectAllCurves(modelPartRef, curves, !ctrlPressed);
            }

            if (expandPart) {
                // For each curve
                int curveIndex = 0;
                for (AnimationCurve curve : curves) {
                    CurveReference curveReference = new CurveReference(modelPartRef, curveIndex);
                    int curveFlags = ImGuiTreeNodeFlags.OpenOnArrow;
                    if (isCurveSelected(curveReference))
                        curveFlags |= ImGuiTreeNodeFlags.Selected;

                    String curveLabel = "Curve " + curveIndex;
                    boolean expandCurve = ImGui.treeNodeEx(curveLabel, curveFlags);
                    if (ImGui.isItemClicked()) {
                        selectCurve(curveReference, curve, !ctrlPressed);
                    }

                    if (expandCurve) {
                        ImGui.text("Curve Format: " + curveFormatName(curve.getFormat()));
                        ImGui.text("Frame Offset: " + curve.getFrameOffset());
                        ImGui.text("Curve Length: " + curve.size());

                        // For each channel
                        for (int channelIndex : curve.getChannelIndices()) {
                            int channelFlags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.Leaf;
                            if (isChannelSelected(curveReference, channelIndex))
                                channelFlags |= ImGuiTreeNodeFlags.Selected;

                            boolean expandChannel = ImGui.treeNodeEx(AnimationCurve.nameFromCurveIndex(channelIndex), channelFlags);
                            if (ImGui.isItemClicked()) {
                                selectChannel(curveReference, channelIndex, !ctrlPressed);
                            }
                            if (expandChannel) {
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

        ImGui.endChild();
    }

    private static String curveFormatName(AnimationCurve.CurveFormat format) {
        return switch(format) {
            case POS -> "Position Only";
            case POS_ROT -> "Position/Rotation";
            case POS_ROT_SCALE -> "Position/Rotation/Scale";
        };
    }

    private static double[] convertArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }

        return doubles;
    }
}
