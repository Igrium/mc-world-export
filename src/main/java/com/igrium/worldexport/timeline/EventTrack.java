package com.igrium.worldexport.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;

/** Explicit event messages and observational snapshots keep different timing guarantees. */
public final class EventTrack {
    private final JsonArray cues = new JsonArray();
    private final Set<String> seen = new HashSet<>();
    public void event(double tick, String id, String kind, String source, String precision, JsonObject data) {
        if (id != null && !seen.add(source + ":" + id)) return;
        var cue = new JsonObject();cue.addProperty("cueId", "event." + cues.size());
        cue.addProperty("tick", tick);cue.addProperty("kind", kind);cue.addProperty("source", source);
        cue.addProperty("timing", precision);if(id != null)cue.addProperty("sourceEventId", id);
        cue.add("data", data.deepCopy());cues.add(cue);
    }
    public JsonObject snapshot() {
        var root = new JsonObject();root.addProperty("schemaVersion",1);
        root.addProperty("timeUnit", "export_ticks_20_per_second");
        root.addProperty("coverage", "explicit_events_and_opt_in_payload_adapters");
        root.addProperty("completeServerHistoryGuaranteed", false);
        root.add("cues", cues.deepCopy());return root;
    }
}
