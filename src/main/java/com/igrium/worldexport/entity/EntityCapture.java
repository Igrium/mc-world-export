package com.igrium.worldexport.entity;

import com.igrium.worldexport.event.EntityCaptureEvents;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayTexture;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Tolerate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Manages the capturing entity movements.
 */
public class EntityCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityCapture.class);

    private final Map<EntityType<?>, ModelAdapter<?, ?>> modelAdapters = new HashMap<>();

    /**
     * The world-space bounds of the export.
     */
    @Getter @Setter @NonNull
    private AABB bounds;

    /**
     * A predicate to determine if any given entity should be exported.
     */
    @Getter @Setter @NonNull
    private Predicate<? super Entity> entityPredicate = e -> true;

    /**
     * An offset to apply to each entity after exporting the replay file.
     */
    @Getter @Setter @NonNull
    private Vec3 globalOffset = Vec3.ZERO;

    /**
     * Any textures that entities require will be added to this map.
     */
    @Getter @Setter @NonNull
    private MaterialHolder materialHolder = new MaterialHolder();

    @Tolerate
    public void setGlobalOffset(Vec3i offset) {
        this.globalOffset = new Vec3(offset.getX(), offset.getY(), offset.getZ());
    }

    /**
     * All entities that have been captured with their corresponding animation data.
     */
    @Getter
    private final Map<Entity, CapturedEntity> entities = new HashMap<>();

    public EntityCapture(@NotNull AABB bounds) {
        this.bounds = bounds;
    }


    @SuppressWarnings("unchecked")
    public <T extends Entity> ModelAdapter<T, ?> getModelAdapter(T entity) {
        EntityType<?> type = entity.getType(); // EntityType<T> for unchecked
        return (ModelAdapter<T, ?>) modelAdapters.computeIfAbsent(type, t -> ModelAdapters.createModelAdapter(entity));
    }


    /**
     * Capture all entity poses for this frame.
     * @param world World to get entities from.
     * @param tick The frame index in the replay file.
     */
    public void captureFrame(Level world, int tick) {
        Minecraft client = Minecraft.getInstance();
        client.getEntityRenderDispatcher().prepare(world, client.gameRenderer.getMainCamera(), client.crosshairPickEntity);

        var entities = world.getEntities((Entity) null, bounds, entityPredicate);
        for (var entity : entities) {
            try {
                captureEntity(entity, tick);
            } catch (Exception e) {
                LOGGER.error("Error capturing pose for entity {} on tick {}:", entity.getName(), tick, e);
            }
        }
    }

    private <T extends Entity> void captureEntity(T entity, int tick) {
        if (!EntityCaptureEvents.BEFORE_CAPTURE_ENTITY.invoker().beforeCaptureEntity(this, entity, tick)) {
            return;
        }
        var modelAdapter = getModelAdapter(entity);
        captureModelAdapter(modelAdapter, entity, tick);
    }

    private <T extends Entity, S extends EntityRenderState> void captureModelAdapter(ModelAdapter<T, S> modelAdapter, T entity, int tick) {
        S state = modelAdapter.getAndUpdateRenderState(entity);
        CapturedEntity capture = entities.computeIfAbsent(entity, e -> new CapturedEntity());
        modelAdapter.capture(entity, state, capture, materialHolder, globalOffset, tick);
    }

    public static String getEntityTexturePath(ResourceLocation id) {
        String path = id.getPath();
        if (!path.endsWith(".png"))
            path += ".png";
        return id.getNamespace() + "/" + path;
    }
}
