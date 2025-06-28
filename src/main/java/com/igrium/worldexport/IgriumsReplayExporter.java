package com.igrium.worldexport;

import com.igrium.worldexport.command.test.WorldCaptureCommand;
import com.igrium.worldexport.event.BeforeSetBlockCallback;
import com.igrium.worldexport.event.ClientBlockPlaceCallback;
import com.igrium.worldexport.world.WorldCapture;
import com.igrium.worldexport.world.world_snapshot.WorldSnapshotManager;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgriumsReplayExporter implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Getter
    private static IgriumsReplayExporter instance;

    @Getter
    @Setter
    @Nullable
    private WorldCapture currentWorldCapture;

    @Getter
    private WorldSnapshotManager worldSnapshotManager;

    @Override
    public void onInitialize() {
        instance = this;



        BeforeSetBlockCallback.EVENT.register((pos, block, world) -> {
            if (currentWorldCapture != null)
                currentWorldCapture.beforeSetBlock(pos, block);
        });
    }

    @Override
    public void onInitializeClient() {
        worldSnapshotManager = new WorldSnapshotManager();
        ClientBlockPlaceCallback.EVENT.register(worldSnapshotManager);

        ClientCommandRegistrationCallback.EVENT.register(WorldCaptureCommand::register);
    }
}