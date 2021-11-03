package org.scaffoldeditor.worldexport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;

public class WorldExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing world export mod.");
        ExportCommand.register();
    }
    
}
