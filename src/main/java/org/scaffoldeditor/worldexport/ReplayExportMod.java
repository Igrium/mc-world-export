package org.scaffoldeditor.worldexport;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModels;
import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntity;
import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntityRenderer;
import org.scaffoldeditor.worldexport.replaymod.ReplayModHooks;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;
import org.scaffoldeditor.worldexport.test.ExportCommand;
import org.scaffoldeditor.worldexport.test.ReplayTestCommand;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityType; 
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.registry.Registry;

public class ReplayExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");
    private static ReplayExportMod instance;

    public static final EntityType<AnimatedCameraEntity> ANIMATED_CAMERA = Registry.register(
            Registry.ENTITY_TYPE, AnimatedCameraEntity.ID,
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, AnimatedCameraEntity::new).disableSummon().build());

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
        EntityRendererRegistry.register(ANIMATED_CAMERA, AnimatedCameraEntityRenderer::new);

        // TODO: Make sure this only registers *after* the replay mod.
        ReplayModHooks.onReplayModInit(replayMod -> {
            cameraAnimationsModule.register();
            cameraAnimationsModule.registerKeyBindings(replayMod);
        });
        
    }

    public CameraAnimationModule getCameraAnimationsModule() {
        return cameraAnimationsModule;
    }
    
}
