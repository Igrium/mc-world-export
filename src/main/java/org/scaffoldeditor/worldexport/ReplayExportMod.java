package org.scaffoldeditor.worldexport;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModels;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;
import org.scaffoldeditor.worldexport.test.ExportCommand;
import org.scaffoldeditor.worldexport.test.ReplayTestCommand;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class ReplayExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");
    private static ReplayExportMod instance;

    public static ReplayExportMod getInstance() {
        return instance;
    }

    private Set<ClientBlockPlaceCallback> blockUpdateListeners = new HashSet<>();
    private CameraAnimationModule cameraAnimationsModule = new CameraAnimationModule();
    
    public void onBlockUpdated(ClientBlockPlaceCallback listener) {
        blockUpdateListeners.add(listener);
    }

    public boolean removeOnBlockUpdated(ClientBlockPlaceCallback listener) {
        return blockUpdateListeners.remove(listener);
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientCommandRegistrationCallback.EVENT.register(ExportCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ReplayTestCommand::register);

        ClientBlockPlaceCallback.EVENT.register((pos, state, world) -> {
            blockUpdateListeners.forEach(listener -> listener.place(pos, state, world));
        });

        ReplayModels.registerDefaults();

        // TODO: Make sure this only registers *after* the replay mod.
        cameraAnimationsModule.register();
        cameraAnimationsModule.registerKeyBindings();
    }

    public CameraAnimationModule getCameraAnimationsModule() {
        return cameraAnimationsModule;
    }
    
}
