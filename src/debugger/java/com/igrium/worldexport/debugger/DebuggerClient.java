package com.igrium.worldexport.debugger;

import com.igrium.worldexport.debugger.raw.WorldExportCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebuggerClient implements ClientModInitializer {
    public static final String MOD_ID = "worldexport-debugger";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ReplayDebugger.registerMenuButton();
        ClientCommandRegistrationCallback.EVENT.register(WorldExportCommand::register);
    }
}
