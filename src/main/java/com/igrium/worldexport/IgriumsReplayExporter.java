package com.igrium.worldexport;

import com.igrium.worldexport.world.WorldRecorderManager;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgriumsReplayExporter implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Getter
    private static IgriumsReplayExporter instance;

    @Getter
    private final WorldRecorderManager worldRecorderManager = new WorldRecorderManager();

    @Override
    public void onInitialize() {
        instance = this;

    }

    @Override
    public void onInitializeClient() {

    }
}