package com.igrium.worldexport.debugger;

import com.igrium.worldexport.replay.CompiledReplay;
import com.igrium.worldexport.tex.ReplayMtl;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlWriter;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class MtlInspectorWindow {
    private final ReplayDebugger replayDebugger;
    private final Map<Mtl, ImString> serializedMtlCache = new WeakHashMap<>();
    private final ImString emptyString = new ImString("");

    public MtlInspectorWindow(ReplayDebugger replayDebugger) {
        this.replayDebugger = replayDebugger;
    }

    public void drawMtlInspector() {
        CompiledReplay replay = replayDebugger.getReplay();
        if (replay == null)
            return;

        ImString text;
        ReplayMtl selected = replayDebugger.getSelectedMaterial().get(replay.getMtlLibs());
        if (selected != null) {
            text = serializedMtlCache.computeIfAbsent(selected.mtl(), MtlInspectorWindow::serializeMtl);
        } else {
            text = emptyString;
        }
        ImGui.pushItemWidth(-1);
        ImGui.inputTextMultiline("##MTL Data", text, ImGuiInputTextFlags.ReadOnly);
        ImGui.popItemWidth();

        ImGui.separator();

        if (ImGui.beginTable("Properties", 2)) {
            if (selected != null) {
                for (var entry : selected.properties().entrySet()) {
                    ImGui.tableNextRow();
                    ImGui.tableSetColumnIndex(0);
                    ImGui.text(entry.getKey());
                    ImGui.tableSetColumnIndex(1);
                    ImGui.text(entry.getValue().toString());
                }
            }
            ImGui.endTable();
        }
    }

    private static ImString serializeMtl(Mtl mtl) {
        try {
            StringWriter writer = new StringWriter();
            MtlWriter.write(List.of(mtl), writer);
            return new ImString(writer.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
