package com.igrium.worldexport.timeline;

import com.google.gson.JsonObject;
import java.util.Objects;

/** A semantic cue supplied explicitly by an integration, never inferred from unrelated packets. */
public record ReplayEvent(String id, String kind, String source, String timing, JsonObject data) {
    public ReplayEvent {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("Event kind is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Event source is required");
        if (timing == null || timing.isBlank()) throw new IllegalArgumentException("Timing semantics are required");
        data = Objects.requireNonNull(data, "Event data").deepCopy();
    }

    /** Integrations cannot mutate a cue after submitting it. */
    @Override public JsonObject data() { return data.deepCopy(); }
}
