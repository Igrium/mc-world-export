package com.igrium.worldexport.entity;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages the capturing entity movements.
 */
public class EntityCapture {
    private final Map<EntityType<?>, EntityModelAdapter<?, ?>> modelAdapters = new HashMap<>();

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

    /**
     * All entities that have been captured with their corresponding animation data.
     */
    @Getter
    private final Map<Entity, CapturedEntity> captures = new HashMap<>();

    public EntityCapture(@NotNull Box bounds) {
        this.bounds = bounds;
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityModelAdapter<? super T, ?> getModelAdapter(EntityType<T> entityType) {
        return (EntityModelAdapter<? super T, ?>) modelAdapters.computeIfAbsent(entityType, this::createModelAdapter);
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityModelAdapter<? super T, ?> getModelAdapter(T entity) {
        EntityType<T> type = (EntityType<T>) entity.getType();
        return getModelAdapter(type);
    }

    private <T extends Entity> EntityModelAdapter<? super T, ?> createModelAdapter(EntityType<T> entityType) {
        var adapter = ModelAdapters.createModelAdapter(entityType);
        adapter.setGlobalOffset(globalOffset);
        return adapter;
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
        CapturedEntity capture = captures.computeIfAbsent(entity, e -> new CapturedEntity());
        modelAdapter.capture(entity, capture, tick);
    }
}
