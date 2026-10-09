# Opt-in semantic event API

The public client exporter has no quest/job/story/server-framework dependency.
Integrations choose the event fields to expose. Types below are in
`com.igrium.worldexport.timeline`; both paths feed the same Blender/event schema.

## Client submission

Call on the client thread during an active export:

```java
var data = new com.google.gson.JsonObject();
data.addProperty("level", 2);
TimelineCapture.recordEvent(client.level,
    new ReplayEvent("session:42", "example:level_changed", "example",
        "client_receipt_tick", data));
```

Kind/source/timing must be nonblank. JSON is copied at construction and access.
Prefer namespaced kinds. `(source, id)` deduplicates retransmission; use a session
plus occurrence sequence. A null ID preserves separate observations. No active
export or a different world means no cue. A live-only UI callback will not survive
ReplayMod export: invoke from a replayed handler or use the packet adapter below.

## Adapt an existing decoded Fabric play payload

Register once during client initialization:

```java
TimelineCapture.registerPayloadAdapter(MyProgressPayload.TYPE.id(), payload -> {
    var progress = (MyProgressPayload) payload;
    var data = new com.google.gson.JsonObject();
    data.addProperty("progress", progress.progress());
    return new ReplayEvent(null, "example:progress_snapshot", "example",
        "snapshot_receipt_tick_not_event_occurrence", data);
});
```

Your mod owns the codec and its normal receiver. The exporter observes opt-in
client-thread Fabric delivery, including replayed packets, before the UI handler.
Duplicate adapter registration throws; return null to omit a packet. Adapter
errors are logged/omitted without aborting capture. No adapter runs outside an
active capture. Expose chosen editing fields, not whole private/chat/account
payloads. The dispatch mixin targets this build's Fabric networking version and
requires revalidation on other versions. Stale HUD state after a seek cannot
establish exact event occurrence.

## Optional interoperable server protocol

`StudioEventPayload.java` defines `worldexport_studio:event`, version-1 codec:

1. `version`: VarInt, currently 1.
2. `session`: UTF, maximum 64 characters.
3. `sequence`: VarLong.
4. `kind`: UTF, maximum 128 characters.
5. `json`: UTF, maximum 262144 characters, containing a JSON object.

Implement this codec in your integration's common/server sources. Check client
support (e.g. `ServerPlayNetworking.canSend(player, TYPE)`) and route only to
clients your server authorizes. The exporter is client-only; do not install it
on a dedicated server. No server listener/framework adapter is bundled.

Source is `worldexport_studio`, ID is `session:sequence`, timing is
`client_receipt_tick_with_server_timestamp`. Optional `serverUnixMillis` and
`serverGameTimeAtDispatch` stay raw metadata; receipt keys are not relabeled as
exact server time. Unknown versions/malformed or non-object JSON are omitted.
The synthetic harness verifies this protocol and an independent opt-in `example`
adapter during real recording/playback/export, including retransmission and
invalid-payload handling.
