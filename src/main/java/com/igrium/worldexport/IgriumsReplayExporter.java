package com.igrium.worldexport;

import com.igrium.worldexport.command.ProfileDiffsCommand;
import com.igrium.worldexport.command.WorldCaptureCommand;
import com.igrium.worldexport.compat.replaymod.ReplayModHooks;
import com.igrium.worldexport.compat.replaymod.ReplayModInterop;
import com.igrium.worldexport.debugger.ReplayDebugger;
import com.igrium.worldexport.event.ClientBlockUpdatedEvent;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplayCompiler;
import com.igrium.worldexport.replay.ReplayIO;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.replaymod.core.ReplayMod;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class IgriumsReplayExporter implements ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Getter
    private static IgriumsReplayExporter instance;

    @Getter
    private volatile ReplayCapture activeRecording;

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientCommandRegistrationCallback.EVENT.register(WorldCaptureCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ProfileDiffsCommand::register);

        ClientTickEvents.END_CLIENT_TICK.register(ReplayCapture::globalEndClientTick);
        ClientBlockUpdatedEvent.EVENT.register(ReplayCapture::globalClientBlockUpdated);
        ClientChunkEvents.CHUNK_LOAD.register(ReplayCapture::globalClientChunkLoad);

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(ReplayCapture::globalClientWorldChange);

        if (FabricLoader.getInstance().isModLoaded("craftui")) {
            ReplayDebugger.registerMenuButton();
        }

        if (FabricLoader.getInstance().isModLoaded("replaymod")) {
            ReplayModHooks.onInit(ReplayModInterop::onInitReplayMod);
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
