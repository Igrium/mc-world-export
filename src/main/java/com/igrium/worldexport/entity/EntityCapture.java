package com.igrium.worldexport.entity;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Tolerate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages the capturing entity movements.
 */
public class EntityCapture {
    private final Map<EntityType<?>, ModelAdapter<?, ?>> modelAdapters = new HashMap<>();

    /**
     * The world-space bounds of the export.
     */
    @Getter @Setter @NonNull
    private Box bounds;

    /**
     * A predicate to determine if any given entity should be exported.
     */
    @Getter @Setter @NonNull
    private Predicate<? super Entity> entityPredicate = e -> true;

    /**
     * An offset to apply to each entity after exporting the replay file.
     */
    @Getter @Setter @NonNull
    private Vec3d globalOffset = Vec3d.ZERO;

    @Tolerate
    public void setGlobalOffset(Vec3i offset) {
        this.globalOffset = new Vec3d(offset.getX(), offset.getY(), offset.getZ());
    }

    /**
     * All entities that have been captured with their corresponding animation data.
     */
    @Getter
    private final Map<Entity, CapturedEntity> entities = new HashMap<>();

    public EntityCapture(@NotNull Box bounds) {
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
    public void captureFrame(World world, int tick) {
        var entities = world.getOtherEntities(null, bounds, entityPredicate);
        for (var entity : entities) {
            captureEntity(entity, tick);
        }
    }

    private <T extends Entity> void captureEntity(T entity, int tick) {
        var modelAdapter = getModelAdapter(entity);
        captureModelAdapter(modelAdapter, entity, tick);
    }

    private <T extends Entity, S extends EntityRenderState> void captureModelAdapter(ModelAdapter<T, S> modelAdapter, T entity, int tick) {
        S state = modelAdapter.getAndUpdateRenderState(entity);
        CapturedEntity capture = entities.computeIfAbsent(entity, e -> new CapturedEntity());
        modelAdapter.capture(entity, state, capture, globalOffset, tick);
    }
}
