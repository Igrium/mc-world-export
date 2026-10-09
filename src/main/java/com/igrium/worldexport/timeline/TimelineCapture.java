package com.igrium.worldexport.timeline;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.replay.ReplayCapture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Shared export clock, automatic environment state, and explicit opt-in semantic events. */
public final class TimelineCapture {
    private static final Map<Identifier, Function<CustomPacketPayload, ReplayEvent>> ADAPTERS = new ConcurrentHashMap<>();
    private final ReplayCapture capture;
    private final EnvironmentTrack environment = new EnvironmentTrack();
    private final EventTrack events = new EventTrack();

    public TimelineCapture(ReplayCapture capture) { this.capture = capture; }
    public JsonObject environment() { return environment.snapshot(); }
    public JsonObject events() { return events.snapshot(); }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(StudioEventPayload.TYPE, StudioEventPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(StudioEventPayload.TYPE, (payload, context) -> {
            if (payload.version() != 1) return;
            try {
                var data = JsonParser.parseString(payload.json()).getAsJsonObject();
                recordEvent(context.client().level, new ReplayEvent(payload.session() + ":" + payload.sequence(),
                        payload.kind(), "worldexport_studio", "client_receipt_tick_with_server_timestamp", data));
            } catch (RuntimeException error) {
                IgriumsReplayExporter.LOGGER.warn("Ignored invalid replay event: {}", error.toString());
            }
        });
    }

    /**
     * Submit a cue at the current export tick on the client thread. No active export means no cue.
     * For ReplayMod exports, integrations must invoke this from a handler replayed during export,
     * rather than relying on a live-only HUD callback. Idless repeated events remain independent.
     */
    public static void recordEvent(Level world, ReplayEvent event) {
        if (!Minecraft.getInstance().isSameThread())
            throw new IllegalStateException("Record replay events on the client thread");
        Objects.requireNonNull(event, "event");
        for (var capture : ReplayCapture.getActiveCaptures()) {
            if (world != null && capture.getWorld() == world) {
                capture.getTimelineCapture().events.event(capture.getSoundTick(), event.id(), event.kind(),
                        event.source(), event.timing(), event.data());
            }
        }
    }

    /**
     * Opt in to one decoded Fabric play-payload type. Return null to omit a packet.
     * Do not register private chat/account payloads or blindly serialize arbitrary packet fields.
     * The integration owns its codec/receiver; registering this adapter does not register a codec.
     */
    public static void registerPayloadAdapter(Identifier payloadId, Function<CustomPacketPayload, ReplayEvent> adapter) {
        Objects.requireNonNull(payloadId, "payloadId");
        Objects.requireNonNull(adapter, "adapter");
        if (ADAPTERS.putIfAbsent(payloadId, adapter) != null)
            throw new IllegalArgumentException("A replay adapter is already registered for " + payloadId);
    }

    /** Observe opt-in decoded Fabric delivery, including replayed packets, before its UI handler. */
    public static void payloadReceived(CustomPacketPayload payload) {
        var adapter = ADAPTERS.get(payload.type().id());
        if (adapter == null || ReplayCapture.getActiveCaptures().isEmpty()) return;
        try {
            ReplayEvent event = adapter.apply(payload);
            if (event != null) recordEvent(Minecraft.getInstance().level, event);
        } catch (RuntimeException error) {
            IgriumsReplayExporter.LOGGER.warn("Replay adapter failed for {}: {}", payload.type().id(), error.toString());
        }
    }

    public void sample() {
        var world = capture.getWorld();
        environment.sample(capture.getSoundTick(), world.dimension().identifier().toString(), world.getGameTime(),
                world.getDefaultClockTime(), world.getOverworldClockTime(), world.canHaveWeather(), world.isRaining(),
                world.isThundering(), world.getRainLevel(1), world.getThunderLevel(1));
    }
}
