package compat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import com.replaymod.core.ReplayMod;
import com.replaymod.recording.ReplayModRecording;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.simplepathing.SPTimeline;
import com.replaymod.simplepathing.InterpolatorType;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.render.utils.FlawlessFrames;
import java.nio.file.*;
import java.util.Comparator;

public class CompatHarness implements ClientModInitializer {
    private static CompatHarness instance;
    private boolean ready; private boolean updated; private boolean changedFixture; private boolean soundFixture; private int soundFrames; private net.minecraft.client.resources.sounds.SoundInstance fixtureLoop;
    private int stage, ticks;
    private long phaseStarted = System.currentTimeMillis();
    private final long started = System.currentTimeMillis();
    private double x, y, z;
    private boolean busy;
    private void log(String value) { System.out.println("[COMPAT] " + value); }
    @Override public void onInitializeClient() {
        instance = this;
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(ExamplePayload.TYPE, ExamplePayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(ExamplePayload.TYPE, (payload, context) -> {});
        com.igrium.worldexport.timeline.TimelineCapture.registerPayloadAdapter(ExamplePayload.TYPE.id(), payload -> {
            var data = com.google.gson.JsonParser.parseString(((ExamplePayload) payload).json()).getAsJsonObject();
            return new com.igrium.worldexport.timeline.ReplayEvent(null, "example:progress_snapshot", "example_adapter", "snapshot_receipt_tick_not_event_occurrence", data);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { ready = true; soundFrame(mc); });
        log("Replay Exporter harness initialized");
    }
    public static void frame(Minecraft mc) {
        if (instance != null && instance.ready) instance.tick(mc);
    }
    public record ExamplePayload(String json) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<ExamplePayload> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("example", "progress"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ExamplePayload> CODEC = net.minecraft.network.protocol.common.custom.CustomPacketPayload.codec(
            (p,b) -> b.writeUtf(p.json(), 4096), b -> new ExamplePayload(b.readUtf(4096)));
        @Override public Type<ExamplePayload> type() { return TYPE; }
    }
    private void soundFrame(Minecraft mc) {
        if (com.igrium.worldexport.replay.ReplayCapture.getActiveCaptures().isEmpty()) return;
        soundFrames++;
        if (!soundFixture) {
            soundFixture = true;
            net.minecraft.world.entity.Entity sourceEntity = null;
            for (var entity : mc.level.entitiesForRendering()) {
                if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("pig") && entity.position().distanceToSqr(new net.minecraft.world.phys.Vec3(x,y,z)) < 100) { sourceEntity = entity; break; }
            }
            if (sourceEntity == null) throw new IllegalStateException("Missing fixture pig");
            fixtureLoop = new net.minecraft.client.resources.sounds.EntityBoundSoundInstance(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),net.minecraft.sounds.SoundSource.AMBIENT,.3f,.75f,sourceEntity,123L) {{ looping = true; }};
            mc.getSoundManager().play(fixtureLoop);
            mc.getSoundManager().playDelayed(new net.minecraft.client.resources.sounds.SimpleSoundInstance(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HARP.value(),net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.25f,net.minecraft.util.RandomSource.create(42L),sourceEntity.position().x,sourceEntity.position().y,sourceEntity.position().z),3);
            log("SOUND_FIXTURE_STARTED");
        }
        if (soundFrames == 8) { mc.getSoundManager().stop(fixtureLoop); log("SOUND_LOOP_STOPPED"); }
    }
    private long elapsed() { return System.currentTimeMillis() - phaseStarted; }
    private void tick(Minecraft mc) {
        if (busy || stage == 99) return;
        busy = true;
        try {
            ticks++;
            if (System.currentTimeMillis() - started > 240000) throw new IllegalStateException("Timed out at stage " + stage);
            if (stage == 0 && elapsed() > 10000 && mc.gui.overlay() == null) {
                log("STARTUP_OK; Sodium Flawless Frames registered=" + FlawlessFrames.hasSodium());
                String[] conflict = VideoRenderer.checkCompat(new RenderSettings());
                if (conflict != null) throw new IllegalStateException(String.join("; ", conflict));
                var settings = ReplayMod.instance.getSettingsRegistry();
                settings.set(com.replaymod.recording.Setting.AUTO_POST_PROCESS, false);
                settings.set(com.replaymod.recording.Setting.RENAME_DIALOG, false);
                settings.set(com.replaymod.recording.Setting.RECORD_SINGLEPLAYER, true);
                settings.set(com.replaymod.recording.Setting.AUTO_START_RECORDING, true);
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(6);
                stage = 1; ticks = 0; phaseStarted = System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel("compat-" + started,
                    new LevelSettings("ReplayMod compatibility test", GameType.CREATIVE,
                        LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(263L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.hasSingleplayerServer() && mc.gui.overlay() == null) {
                log("WORLD_OK; recording=" + (ReplayModRecording.instance.getConnectionEventHandler().getPacketListener() != null));
                if (ReplayModRecording.instance.getConnectionEventHandler().getPacketListener() == null) throw new IllegalStateException("Recording did not start");
                x = mc.player.getX(); y = mc.player.getY(); z = mc.player.getZ();
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "weather clear");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "time set 1000");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "summon minecraft:item_display ~3 ~2 ~2 {Tags:[\"capture_item\"],item:{id:\"minecraft:stone\",count:1},transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[0.8f,1.2f,0.6f],right_rotation:[0f,0.3826834f,0f,0.9238795f]}}");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "summon minecraft:text_display ~4 ~2 ~2 {text:\"Synthetic fixture\"}");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "summon minecraft:block_display ~5 ~2 ~2 {block_state:{Name:\"minecraft:stone\"}}");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "setblock ~1 ~-1 ~1 minecraft:grass_block");
                    for (String mob : new String[]{"cow", "sheep", "pig", "villager", "armor_stand"}) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            "summon minecraft:" + mob + " " + (x + 2) + " " + (y + 1) + " " + (z + 3));
                    }
                });
                stage = 2; ticks = 0; phaseStarted = System.currentTimeMillis();
            } else if (stage == 2 && elapsed() > 2500 && !changedFixture) {
                changedFixture = true;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "weather rain");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "time set 18000");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "item replace entity @e[type=minecraft:armor_stand,limit=1] armor.head with minecraft:paper");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "data merge entity @e[tag=capture_item,limit=1] {item:{id:\"minecraft:gold_block\",count:1},transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[1.2f,0.8f,0.6f],right_rotation:[0f,0.3826834f,0f,0.9238795f]}}");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "playsound minecraft:block.note_block.harp block @a ~ ~ ~ .7 1.1");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(x,y,z)), "playsound minecraft:ambient.cave ambient @a ~ ~ ~ .4 .8");
                    for (var player : server.getPlayerList().getPlayers()) {
                        for (String kind : new String[]{"example:quest_completed", "example:level_changed", "example:story_beat"}) {
                            var data = new com.google.gson.JsonObject(); data.addProperty("level",2);
                            data.addProperty("serverGameTimeAtDispatch",server.overworld().getGameTime());
                            data.addProperty("serverUnixMillis",System.currentTimeMillis());
                            var event = new com.igrium.worldexport.timeline.StudioEventPayload(1,"synthetic-fixture",kind.hashCode()&0x7fffffff,kind,data.toString());
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,event);
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,event);
                        }
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new ExamplePayload("{\"progress\":1}"));
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new ExamplePayload("{\"progress\":2}"));
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new ExamplePayload("malformed"));
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new com.igrium.worldexport.timeline.StudioEventPayload(1,"bad",0,"example:bad","[]"));
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new com.igrium.worldexport.timeline.StudioEventPayload(2,"unknown",0,"example:bad","{}"));
                    }
                    var pig=server.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("pig") && e.position().distanceToSqr(new net.minecraft.world.phys.Vec3(x,y,z))<100).getFirst();
                    server.overworld().playSeededSound(null,pig,net.minecraft.sounds.SoundEvents.AMBIENT_CAVE,net.minecraft.sounds.SoundSource.NEUTRAL,.8f,.9f,12L);
                });
                log("GENERIC_EVENT_PACKETS_SENT");
            } else if (stage == 2 && elapsed() > 7000 && !updated) {
                updated = true;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:diamond_sword");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "item replace entity @e[type=minecraft:armor_stand,limit=1] armor.head with minecraft:paper");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "setblock " + ((int) x + 1) + " " + ((int) y) + " " + ((int) z + 1) + " minecraft:chest");
                    String[] pbrBlocks = {"iron_block", "copper_block", "stone", "glowstone"};
                    for (int i=0; i<pbrBlocks.length; i++) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            "setblock " + ((int) x - 2 + i) + " " + ((int) y) + " " + ((int) z - 2) + " minecraft:" + pbrBlocks[i]);
                    }
                });
                log("TEST_EQUIPMENT_AND_BLOCK_CHANGE_SENT");
            } else if (stage == 2 && elapsed() > 9000) {
                log("RECORDING_OK; disconnecting test world");
                stage = 3; ticks = 0; phaseStarted = System.currentTimeMillis();
                mc.disconnect(new TitleScreen(), false);
            } else if (stage == 3 && elapsed() > 1500 && mc.level == null) {
                Path folder = ReplayMod.instance.folders.getReplayFolder();
                Path replay;
                try (var files = Files.list(folder)) {
                    replay = files.filter(p -> p.toString().endsWith(".mcpr"))
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
                }
                if (replay != null && replay.toFile().lastModified() > started) {
                    log("SAVED_REPLAY=" + replay);
                    stage = 4; ticks = 0; phaseStarted = System.currentTimeMillis();
                    ReplayModReplay.instance.startReplay(replay.toFile());
                }
            } else if (stage == 4 && elapsed() > 3000 && mc.level != null && ReplayModReplay.instance.getReplayHandler() != null) {
                var handler = ReplayModReplay.instance.getReplayHandler();
                if (handler.getCameraEntity() == null) return;
                mc.mouseHandler.grabMouse();
                mc.mouseHandler.releaseMouse();
                log("INPUT_OK; keyboard state polling and mouse capture succeeded");
                var overlay = handler.getOverlay();
                int keyT = com.mojang.blaze3d.platform.InputConstants.KEY_T;
                mc.keyboardHandler.keyPress(mc.getWindow().handle(), 1, new net.minecraft.client.input.KeyEvent(keyT, 't', 0));
                mc.keyboardHandler.keyPress(mc.getWindow().handle(), 0, new net.minecraft.client.input.KeyEvent(keyT, 't', 0));
                ((com.replaymod.core.versions.MCVer.MinecraftMethodAccessor) mc).replayModProcessKeyBinds();
                if (!overlay.isMouseVisible()) throw new IllegalStateException("T key did not release the replay cursor");
                log("REPLAY_KEYBOARD_OK; native T key opened the controls");
                var screen = mc.gui.screen();
                if (screen == null) throw new IllegalStateException("Replay controls did not open");
                var click = new net.minecraft.client.input.MouseButtonEvent(15, 15,
                    new net.minecraft.client.input.MouseButtonInfo(1, 0));
                boolean paused = handler.getReplaySender().paused();
                screen.mouseClicked(click, false);
                screen.mouseReleased(click);
                if (handler.getReplaySender().paused() == paused) throw new IllegalStateException("Play/pause mouse button did not respond");
                screen.mouseClicked(click, false);
                screen.mouseReleased(click);
                if (handler.getReplaySender().paused() != paused) throw new IllegalStateException("Second play/pause click did not respond");
                overlay.setMouseVisible(false);
                log("REPLAY_BUTTONS_OK; native SDL left-click toggled play/pause twice");
                log("PLAYBACK_OK; duration=" + handler.getReplayDuration());
                handler.doJump(Math.min(3000, handler.getReplayDuration()), false);
                stage = 5; ticks = 0; phaseStarted = System.currentTimeMillis();
            } else if (stage == 5 && elapsed() > 4000) {
                stage = 6;
                var handler = ReplayModReplay.instance.getReplayHandler();
                var timeline = new SPTimeline();
                timeline.setDefaultInterpolatorType(InterpolatorType.LINEAR);
                int start = 1000;
                timeline.addTimeKeyframe(0, start);
                timeline.addTimeKeyframe(1000, start + 4000);
                timeline.addPositionKeyframe(0, x, y + 3, z - 4, 0, 25, 0, -1);
                timeline.addPositionKeyframe(1000, x + 2, y + 3, z - 4, 0, 25, 0, -1);
                var preset = RenderSettings.EncodingPreset.MP4_CUSTOM;
                var output = mc.gameDirectory.toPath().resolve("compat-export.mp4").toFile();
                var settings = new RenderSettings(RenderSettings.RenderMethod.DEFAULT, preset, 640, 360, 20, 4000000,
                    output, true, false, false, false, false, null, 360, 180, false, false, true,
                    RenderSettings.AntiAliasing.NONE, "ffmpeg", preset.getValue(), false);
                log("EXPORT_START");
                var renderer = new VideoRenderer(settings, handler, timeline.getTimeline());
                boolean result = renderer.renderVideo();
                if (!result || !output.isFile() || output.length() == 0) throw new IllegalStateException("Export failed");
                log("EXPORT_OK; frames=" + renderer.getFramesDone() + "; bytes=" + output.length());
                var renderGui = new com.replaymod.render.gui.GuiRenderSettings(
                    new com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen(), handler, timeline.getTimeline());
                boolean exportButton = renderGui.buttonPanel.getElements().keySet().stream()
                    .filter(e -> e instanceof com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton)
                    .map(e -> ((com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton) e).getLabel())
                    .anyMatch(label -> label.contains("Export Replay"));
                if (!exportButton) throw new IllegalStateException("Export Replay button was not injected");
                log("EXPORTER_BUTTON_OK");
                int sx = Math.floorDiv((int) Math.floor(x), 16);
                int sy = Math.floorDiv((int) Math.floor(y), 16);
                int sz = Math.floorDiv((int) Math.floor(z), 16);
                var sceneFile = mc.gameDirectory.toPath().resolve("compat-scene.replay");
                var sceneSettings = com.igrium.worldexport.replay.ReplayExportSettings.builder()
                    .exportPath(sceneFile).worldBounds(new com.igrium.worldexport.math.ChunkSectionBox(sx-1, sy-1, sz-1, 3, 3, 3))
                    .maxThreads(4).exportWorld(true).exportUpdates(true).exportEntities(true).build();
                log("SCENE_EXPORT_START");
                var sceneExporter = new com.igrium.worldexport.compat.replaymod.export.ReplayExporter(
                    sceneSettings, handler, timeline.getTimeline());
                boolean sceneOk = sceneExporter.exportReplay();
                if (!sceneOk || !Files.isRegularFile(sceneFile) || Files.size(sceneFile) == 0)
                    throw new IllegalStateException("Scene export failed");
                log("SCENE_EXPORT_OK; frames=" + sceneExporter.getFramesDone() + "; bytes=" + Files.size(sceneFile));
                handler.endReplay();
                Files.writeString(mc.gameDirectory.toPath().resolve("compat-test-success.txt"), "Startup, world, recording, playback and export passed\n");
                log("ALL_TESTS_OK");
                stage = 99;
                mc.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            log("FAILED stage=" + stage + "; " + failure);
            stage = 99;
            try { Files.writeString(mc.gameDirectory.toPath().resolve("compat-test-failure.txt"), failure.toString()); } catch (Exception ignored) {}
            mc.stop();
        } finally { busy = false; }
    }
}
