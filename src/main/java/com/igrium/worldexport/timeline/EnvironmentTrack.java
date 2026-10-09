package com.igrium.worldexport.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Client-observed environment, sampled on the same export clock as entity and sound tracks. */
public final class EnvironmentTrack {
    private final JsonArray keys = new JsonArray();
    public void sample(double tick, String dimension, long gameTime, long defaultTime, long overworldTime,
                       boolean weatherAllowed, boolean raining, boolean thundering, float rain, float thunder) {
        var key = new JsonObject();
        key.addProperty("tick", tick); key.addProperty("dimension", dimension);
        key.addProperty("gameTime", gameTime); key.addProperty("defaultClockTime", defaultTime);
        key.addProperty("overworldClockTime", overworldTime);
        key.addProperty("weatherAllowed", weatherAllowed); key.addProperty("raining", raining);
        key.addProperty("thundering", thundering); key.addProperty("rain", rain); key.addProperty("thunder", thunder);
        // One sample per export timestamp; initial state may be replaced by the first end-tick sample.
        if (!keys.isEmpty() && keys.get(keys.size()-1).getAsJsonObject().get("tick").getAsDouble() == tick)
            keys.set(keys.size()-1, key);
        else keys.add(key);
    }
    public JsonObject snapshot() {
        var root = new JsonObject();root.addProperty("schemaVersion", 1);
        root.addProperty("timeUnit", "export_ticks_20_per_second");
        root.addProperty("coverage", "client_observed_state_during_capture");
        root.addProperty("clockInterpretation", "26.3 default and overworld clock total ticks; no fixed day length assumed");
        root.addProperty("weatherInterpretation", "dimension-wide state; local biome precipitation and weather geometry not included");
        root.add("keys", keys.deepCopy());return root;
    }
}
