package com.igrium.worldexport.debugger;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.replay.CompiledReplay;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.*;

public class ReplayOutlinerWindow {

    private final Map<CapturedEntity, Collection<CapturedEntity.ModelPartTreeNode>> entityTreeCache = new WeakHashMap<>();

    private final ReplayDebugger replayDebugger;

    public ReplayOutlinerWindow(ReplayDebugger replayDebugger) {
        this.replayDebugger = replayDebugger;
    }

    public void drawOutliner() {
        if (ImGui.collapsingHeader("Materials")) {
            drawMtlList();
        }
        if (ImGui.collapsingHeader("World Meshes")) {
            drawWorldMeshTree();
        }
        if (ImGui.collapsingHeader("Entities")) {
            drawAllEntities();
        }
    }

    private void drawMtlList() {
        CompiledReplay replay = replayDebugger.getReplay();
        if (replay == null)
            return;

        for (var mtlEntry : replay.getMtlLibs().entrySet()) {
            if (ImGui.treeNodeEx(mtlEntry.getKey())) {
                int mtlIndex = 0;
                for (var mtl : mtlEntry.getValue()) {
                    int flags = ImGuiTreeNodeFlags.Leaf;
                    if (replayDebugger.getSelectedMaterial().mtlLib().equals(mtlEntry.getKey())
                            && replayDebugger.getSelectedMaterial().index() == mtlIndex) {
                        flags |= ImGuiTreeNodeFlags.Selected;
                    }

                    boolean nodeOpen = ImGui.treeNodeEx(mtl.mtl().getName(), flags);
                    if (ImGui.isItemClicked()) {
                        replayDebugger.setSelectedMaterial(new ReplayDebugger.MaterialSelectionReference(mtlEntry.getKey(), mtlIndex));
                    }
                    if (nodeOpen)
                        ImGui.treePop();

                    mtlIndex++;
                }
                ImGui.treePop();
            }
        }
    }

    private void drawWorldMeshTree() {
        CompiledReplay replay = replayDebugger.getReplay();
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

    private void drawAllEntities() {
        CompiledReplay replay = replayDebugger.getReplay();
        if (replay == null)
            return;

        for (var entEntry : replay.getEntities().entrySet()) {
            drawEntity(entEntry.getKey(), entEntry.getValue());
        }
    }

    private void drawEntity(String entName, CapturedEntity entity) {
        Collection<CapturedEntity.ModelPartTreeNode> tree = entityTreeCache.computeIfAbsent(entity, CapturedEntity::generatePartHierarchy);

        if (ImGui.treeNodeEx(entName)) {
            for (var node : tree) {
                drawModelPart(entName, node);
            }
            ImGui.treePop();
        }
    }

    private void drawModelPart(String entName, CapturedEntity.ModelPartTreeNode node) {
        ReplayDebugger.ModelPartReference selection = new ReplayDebugger.ModelPartReference(entName, node.partName());
        boolean ctrlPressed = ImGui.getIO().getKeyCtrl();

        int flags = ImGuiTreeNodeFlags.OpenOnArrow;
        if (node.children().isEmpty())
            flags |= ImGuiTreeNodeFlags.Leaf;
        if (replayDebugger.getSelectedModelParts().contains(selection))
            flags |= ImGuiTreeNodeFlags.Selected;

        boolean expanded = ImGui.treeNodeEx(node.partName(), flags);

        if (ImGui.isItemHovered()) {
            if (ImGui.isMouseDoubleClicked(0)) {
                selectPartsRecursive(entName, node, ctrlPressed);
            } else if (ImGui.isMouseClicked(0)) {
                selectModelPart(selection, ctrlPressed);
            }
        }
        if (expanded) {
            for (var child : node.children()) {
                drawModelPart(entName, child);
            }
            ImGui.treePop();
        }
    }

    private void selectModelPart(ReplayDebugger.ModelPartReference selection, boolean add) {
        if (!add)
            replayDebugger.getSelectedModelParts().clear();
        replayDebugger.getSelectedModelParts().add(selection);
    }

    private void selectPartsRecursive(String entName, CapturedEntity.ModelPartTreeNode part, boolean add) {
        if (!add)
            replayDebugger.getSelectedModelParts().clear();
        selectPartsRecursive(entName, part);
    }

    private void selectPartsRecursive(String entName, CapturedEntity.ModelPartTreeNode part) {
        replayDebugger.getSelectedModelParts().add(new ReplayDebugger.ModelPartReference(entName, part.partName()));
        for (var child : part.children()) {
            selectPartsRecursive(entName, child);
        }
    }
}
