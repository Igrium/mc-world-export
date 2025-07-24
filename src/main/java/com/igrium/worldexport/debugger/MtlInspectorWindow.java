package com.igrium.worldexport.debugger;

import com.igrium.worldexport.replay.CompiledReplay;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlWriter;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import lombok.Lombok;
import lombok.SneakyThrows;

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
        Mtl selected = replayDebugger.getSelectedMaterial().get(replay.getMtlLibs());
        if (selected != null) {
            text = serializedMtlCache.computeIfAbsent(selected, MtlInspectorWindow::serializeMtl);
        } else {
            text = emptyString;
        }
        ImGui.pushItemWidth(-1);
        ImGui.inputTextMultiline("##MTL Data", text, ImGuiInputTextFlags.ReadOnly);
        ImGui.popItemWidth();
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
