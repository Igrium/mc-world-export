package com.igrium.worldexport;

import com.google.common.collect.ImmutableMap;
import com.igrium.worldexport.event.ClientBlockUpdatedEvent;
import com.igrium.worldexport.mesh.ExportModels;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplayCompiler;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.igrium.worldexport.replay.ReplayIO;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class IgriumsReplayExporter implements ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    private static final Map<String, String> COMPAT_ENTRYPOINTS = ImmutableMap.of(
            "replaymod", "com.igrium.worldexport.compat.replaymod.RMEntrypoint");

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * The version tag to be included in replay files written by this version of them mod
     */
    public static final String REPLAY_VERSION = "2.2";

    @Getter
    private static IgriumsReplayExporter instance;

    @Getter
    private volatile ReplayCapture activeRecording;


    @Override
    public void onInitializeClient() {
        instance = this;

        ExportModels.register();

        ClientTickEvents.END_CLIENT_TICK.register(ReplayCapture::globalEndClientTick);
        ClientBlockUpdatedEvent.EVENT.register(ReplayCapture::globalClientBlockUpdated);
        ClientChunkEvents.CHUNK_LOAD.register(ReplayCapture::globalClientChunkLoad);

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(ReplayCapture::globalClientWorldChange);

        FabricLoader fabric = FabricLoader.getInstance();
        for (var entry : COMPAT_ENTRYPOINTS.entrySet()) {
            if (fabric.isModLoaded(entry.getKey())) {
                try {
                    Class<?> clazz = Class.forName(entry.getValue());
                    var entrypoint = (ClientModInitializer) clazz.getDeclaredConstructor().newInstance();
                    entrypoint.onInitializeClient();
                } catch (Exception e) {
                    LOGGER.error("Unable to load compatibility entrypoint for '{}'", entry.getKey(), e);
                }
            }
        }
    }

    public ReplayCapture startRecording(ClientLevel world, ReplayExportSettings settings) {
        activeRecording = new ReplayCapture(world, settings);
        activeRecording.beginCapture();
        return activeRecording;
    }

    public CompletableFuture<?> saveRecording() {
        if (activeRecording == null) {
            throw new IllegalStateException("Not recording");
        }

        activeRecording.finish();
        ReplayCompiler compiler = new ReplayCompiler(activeRecording);
        return compiler.compile().thenCompose(r -> {
            activeRecording = null;
            return ReplayIO.saveReplayZip(
                    FabricLoader.getInstance().getGameDir().resolve("ReplayTest.zip"), r, Util.ioPool());
        }).exceptionally(e -> {
            LOGGER.error("Error saving replay: ", e);
            return null;
        });
    }
}
