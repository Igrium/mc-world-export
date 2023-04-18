package org.scaffoldeditor.worldexport;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModels;
import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntity;
import org.scaffoldeditor.worldexport.replaymod.ReplayModHooks;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;
import org.scaffoldeditor.worldexport.replaymod.render.CameraEntityRenderer;
import org.scaffoldeditor.worldexport.replaymod.render.CameraPathRenderer;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshotManager;

import com.replaymod.simplepathing.ReplayModSimplePathing;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType; 
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ReplayExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");
    private static ReplayExportMod instance;

    public static final EntityType<AnimatedCameraEntity> ANIMATED_CAMERA = Registry.register(
            Registries.ENTITY_TYPE, AnimatedCameraEntity.ID,
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, AnimatedCameraEntity::new)
                    .dimensions(EntityDimensions.fixed(.75f, .75f))
                    .disableSummon().build());
        
    public static final EntityModelLayer CAMERA_MODEL_LAYER = new EntityModelLayer(new Identifier("worldexport", "camera"), "main");

    public static ReplayExportMod getInstance() {
        return instance;
    }

    private final MinecraftClient client = MinecraftClient.getInstance();

    private Set<ClientBlockPlaceCallback> blockUpdateListeners = new HashSet<>();
    private final CameraAnimationModule cameraAnimationsModule = new CameraAnimationModule();
    private CameraPathRenderer cameraPathRenderer;

    private final Version modVersion = FabricLoader.getInstance().getModContainer("worldexport").get().getMetadata().getVersion();
    
    private WorldSnapshotManager worldSnapshotManager;
    
    public Version getModVersion() {
        return modVersion;
    }

    public void onBlockUpdated(ClientBlockPlaceCallback listener) {
        blockUpdateListeners.add(listener);
    }

    public boolean removeOnBlockUpdated(ClientBlockPlaceCallback listener) {
        return blockUpdateListeners.remove(listener);
    }

    public WorldSnapshotManager getWorldSnapshotManager() {
        return worldSnapshotManager;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
        }


        ClientBlockPlaceCallback.EVENT.register((pos, oldState, state, world) -> {
            blockUpdateListeners.forEach(listener -> listener.place(pos, oldState, state, world));
        });

        worldSnapshotManager = new WorldSnapshotManager();

        ReplayModels.registerDefaults();
        EntityRendererRegistry.register(ANIMATED_CAMERA, CameraEntityRenderer::new);

        ReplayModHooks.onReplayModInit(replayMod -> {
            cameraAnimationsModule.register();
            cameraAnimationsModule.registerKeyBindings(replayMod);
            cameraPathRenderer = new CameraPathRenderer(cameraAnimationsModule, ReplayModSimplePathing.instance);
            cameraPathRenderer.register();
            
            WorldRenderEvents.AFTER_ENTITIES.register(cameraPathRenderer::render);
        });

        EntityModelLayerRegistry.registerModelLayer(CAMERA_MODEL_LAYER, CameraEntityRenderer::getTexturedModelData);
        
        // Allows you to spectate camera entity in replay editor.
        WorldRenderEvents.AFTER_SETUP.register(context -> {
            if (client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                Entity ent = ((EntityHitResult) client.crosshairTarget).getEntity();
                if (ent instanceof AnimatedCameraEntity) {
                    client.targetedEntity = ent;
                }
            }
        });
    }

    public CameraAnimationModule getCameraAnimationsModule() {
        return cameraAnimationsModule;
    }
    
    public CameraPathRenderer getCameraPathRenderer() {
        return cameraPathRenderer;
    }
}
