package org.scaffoldeditor.worldexport;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.replay.models.ReplayModels;
import org.scaffoldeditor.worldexport.test.ReplayTestCommand;

import net.fabricmc.api.ClientModInitializer;

public class WorldExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");
    private static WorldExportMod instance;

    public static WorldExportMod getInstance() {
        return instance;
    }

    private Set<ClientBlockPlaceCallback> blockUpdateListeners = new HashSet<>();
    
    public void onBlockUpdated(ClientBlockPlaceCallback listener) {
        blockUpdateListeners.add(listener);
    }

    public boolean removeOnBlockUpdated(ClientBlockPlaceCallback listener) {
        return blockUpdateListeners.remove(listener);
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        ExportCommand.register();
        ReplayTestCommand.register();

        ClientBlockPlaceCallback.EVENT.register((pos, state, world) -> {
            blockUpdateListeners.forEach(listener -> listener.place(pos, state, world));
        });

        ReplayModels.registerDefaults();
    }
    
}
