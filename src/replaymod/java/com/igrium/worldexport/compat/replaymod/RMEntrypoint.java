package com.igrium.worldexport.compat.replaymod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;

public class RMEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LoggerFactory.getLogger(RMEntrypoint.class).info("Initializing ReplayMod WorldExport");
        ReplayModHooks.onInit(ReplayModInterop::onInitReplayMod);
    }
}
