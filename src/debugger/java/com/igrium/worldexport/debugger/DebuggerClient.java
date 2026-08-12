package com.igrium.worldexport.debugger;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebuggerClient implements ClientModInitializer {
    public static final String MOD_ID = "worldexport-debugger";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ReplayDebugger.registerMenuButton();
    }
}
