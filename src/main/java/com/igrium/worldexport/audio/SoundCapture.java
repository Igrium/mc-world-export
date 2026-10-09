package com.igrium.worldexport.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.igrium.worldexport.mixin.AccessorEntityBoundSound;
import com.igrium.worldexport.mixin.AccessorSoundParameters;
import com.igrium.worldexport.replay.ReplayCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Hooks the selected sample before exporter muting suppresses its audio channel. */
public final class SoundCapture {
    private final ReplayCapture capture;
    private final SoundCueTrack track = new SoundCueTrack();
    private final Map<SoundInstance, JsonObject> movingLoops = new IdentityHashMap<>();
    public SoundCapture(ReplayCapture capture) { this.capture = capture; }
    public JsonObject snapshot() { return track.snapshot(); }
    private double tick() { return capture.getSoundTick(); }
    private static String category(SoundSource source) { return source.name().toLowerCase(Locale.ROOT); }

    public static void played(SoundInstance sound) {
        for (var capture : ReplayCapture.getActiveCaptures())
            if (capture.getSettings().isExportSoundCues()) capture.getSoundCapture().onPlay(sound);
    }
    public static void stopped(SoundInstance sound) {
        for (var capture : ReplayCapture.getActiveCaptures()) {
            capture.getSoundCapture().track.stop(sound, capture.getSoundTick(), "explicit_stop");
            capture.getSoundCapture().movingLoops.remove(sound);
        }
    }
    public static void stopped(Identifier event, SoundSource source) {
        for (var capture : ReplayCapture.getActiveCaptures())
            capture.getSoundCapture().track.stopMatching(event == null ? null : event.toString(),
                    source == null ? null : category(source), capture.getSoundTick(), "explicit_stop_filter");
    }
    public static void stoppedAll() {
        for (var capture : ReplayCapture.getActiveCaptures()) {
            capture.getSoundCapture().track.stopMatching(null,null,capture.getSoundTick(),"engine_stop_all");
            capture.getSoundCapture().movingLoops.clear();
        }
    }

    private Vec3 position(SoundInstance sound) {
        if (sound instanceof AccessorEntityBoundSound bound) return bound.worldexport$getEntity().position();
        return new Vec3(sound.getX(),sound.getY(),sound.getZ());
    }
    private JsonArray vector(Vec3 position) {
        var result = new JsonArray(); result.add(position.x); result.add(position.y); result.add(position.z); return result;
    }
    private JsonObject entity(Entity entity) {
        var result = new JsonObject();
        result.addProperty("instanceId", entity.getUUID().toString());
        result.addProperty("entityType", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return result;
    }
    private JsonObject block(BlockPos pos) {
        var result = new JsonObject();
        result.add("position",vector(Vec3.atLowerCornerOf(pos)));
        BlockState state = capture.getWorld().getBlockState(pos);
        result.addProperty("blockId",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        var properties = new JsonObject();
        state.getValues().forEach(value -> properties.addProperty(value.property().getName(), value.valueName()));
        result.add("properties",properties);
        return result;
    }
    private void onPlay(SoundInstance sound) {
        if (Minecraft.getInstance().level != capture.getWorld()) return;
        var cue = new JsonObject();
        cue.addProperty("soundId",sound.getIdentifier().toString());
        cue.addProperty("category",category(sound.getSource()));
        cue.addProperty("looping",sound.isLooping());
        cue.addProperty("loopDelayTicks",sound.getDelay() / (double) Math.max(1,capture.getSettings().getTickStride()));
        cue.addProperty("relative",sound.isRelative());
        cue.addProperty("attenuation",sound.getAttenuation().name().toLowerCase(Locale.ROOT));
        cue.addProperty("dimension",capture.getWorld().dimension().identifier().toString());
        var sample = sound.getSound();
        boolean resolved = sample != null && sample != SoundManager.EMPTY_SOUND && sample != SoundManager.INTENTIONALLY_EMPTY_SOUND;
        cue.addProperty("resolved",resolved);
        if (resolved) {
            cue.addProperty("resolvedFile",sample.getPath().toString());
            cue.addProperty("streaming",sample.shouldStream());
            Minecraft.getInstance().getResourceManager().getResource(sample.getPath())
                    .ifPresent(resource -> cue.addProperty("resourcePack",resource.sourcePackId()));
        }
        // Calling getVolume/getPitch here would consume the sample's RNG a second time.
        if (sound instanceof AccessorSoundParameters parameters) {
            cue.addProperty("volume",parameters.worldexport$getRequestedVolume());
            cue.addProperty("pitch",parameters.worldexport$getRequestedPitch());
            cue.addProperty("parameterSemantics","requested_before_sample_modifiers");
        } else cue.addProperty("parameterSemantics","unavailable_for_custom_instance");
        Vec3 worldPosition = position(sound);
        cue.add("worldPosition",vector(worldPosition));
        cue.add("position",vector(worldPosition.add(Vec3.atLowerCornerOf(capture.getSettings().getOffset()))));
        var source = new JsonObject();
        if (sound instanceof AccessorEntityBoundSound bound) {
            source = entity(bound.worldexport$getEntity());
            source.addProperty("kind","entity"); source.addProperty("association","explicit_entity_bound_sound");
        } else {
            source.addProperty("kind",sound.isRelative() ? "listener_relative" : "positional");
            source.addProperty("association","unknown");
            if (!sound.isRelative()) {
                var blockContext = new JsonArray();
                BlockPos pos = BlockPos.containing(worldPosition);
                blockContext.add(block(pos)); blockContext.add(block(pos.below()));
                source.add("blockContext",blockContext);
                var candidates = new JsonArray();
                capture.getWorld().getEntities(null,new net.minecraft.world.phys.AABB(worldPosition,worldPosition).inflate(1.5))
                        .stream().limit(8).forEach(e -> { var candidate=entity(e);
                            candidate.addProperty("distance",e.position().distanceTo(worldPosition));candidates.add(candidate); });
                source.add("nearbyEntityCandidates",candidates);
            }
        }
        cue.add("source",source);
        track.start(sound,cue,tick());
        if (sound.isLooping() && sound instanceof AccessorEntityBoundSound) {
            var positions = new JsonArray(); var key=new JsonObject();key.addProperty("tick",tick());key.add("position",cue.get("position").deepCopy());positions.add(key);
            cue.add("positionKeys",positions); movingLoops.put(sound,cue);
        }
    }
    public void onTick() {
        for (var entry : movingLoops.entrySet()) {
            var cue=entry.getValue(); if (cue.has("endTick")) continue;
            var keys=cue.getAsJsonArray("positionKeys");
            var current=vector(position(entry.getKey()).add(Vec3.atLowerCornerOf(capture.getSettings().getOffset())));
            if (!current.equals(keys.get(keys.size()-1).getAsJsonObject().get("position"))) {
                var key=new JsonObject();key.addProperty("tick",tick());key.add("position",current);keys.add(key);
            }
        }
    }
    public void finish() { track.finish(tick()); movingLoops.clear(); }
}
