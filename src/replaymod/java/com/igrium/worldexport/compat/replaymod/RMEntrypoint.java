package com.igrium.worldexport.compat.replaymod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused") // Created via reflection
public class RMEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LoggerFactory.getLogger(RMEntrypoint.class).info("Initializing ReplayMod WorldExport");
        ReplayModHooks.onInit(ReplayModInterop::onInitReplayMod);
    }
}
