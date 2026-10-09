package com.igrium.worldexport.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.IdentityHashMap;
import java.util.Map;

/** Timing and lifetime of sound requests; no audio buffers or sample durations. */
public final class SoundCueTrack {
    private final JsonArray cues = new JsonArray();
    private final Map<Object, JsonObject> active = new IdentityHashMap<>();

    public void start(Object instance, JsonObject cue, double tick) {
        stop(instance, tick, "restarted");
        cue.addProperty("cueId", "sound." + cues.size());
        cue.addProperty("startTick", tick);
        cue.addProperty("durationKnown", false);
        cues.add(cue);
        active.put(instance, cue);
    }

    public void stop(Object instance, double tick, String reason) {
        var cue = active.remove(instance);
        if (cue == null) return;
        cue.addProperty("endTick", Math.max(tick, cue.get("startTick").getAsDouble()));
        cue.addProperty("endReason", reason);
    }

    public void stopMatching(String event, String category, double tick, String reason) {
        for (var instance : new java.util.ArrayList<>(active.keySet())) {
            var cue = active.get(instance);
            if ((event == null || event.equals(cue.get("soundId").getAsString()))
                    && (category == null || category.equals(cue.get("category").getAsString())))
                stop(instance, tick, reason);
        }
    }

    public void finish(double tick) {
        for (var instance : new java.util.ArrayList<>(active.keySet())) {
            var cue = active.get(instance);
            if (cue.get("looping").getAsBoolean()) stop(instance, tick, "capture_end");
        }
        active.clear();
    }

    public JsonObject snapshot() {
        var result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.addProperty("timeUnit", "export_ticks_20_per_second");
        result.addProperty("timingPrecision", "client_tick");
        result.addProperty("audioIncluded", false);
        result.addProperty("coverage", "client_sound_requests_during_capture");
        result.addProperty("originalClientAmbienceGuaranteed", false);
        result.add("cues", cues.deepCopy());
        return result;
    }
}
