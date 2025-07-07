package com.igrium.worldexport;

import com.igrium.worldexport.command.ProfileDiffsCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgriumsReplayExporter implements ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ProfileDiffsCommand::register);
    }
}
