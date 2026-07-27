package com.igrium.worldexport.entity;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.worldexport.entity.models.ItemModelAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

public class ModelAdapters {

    public interface ModelAdapterFactory<T extends Entity> {
        ModelAdapter<T, ?> get(EntityRenderer<? super T, ?> renderer);
    }

    private static final BiMap<EntityType<?>, ModelAdapterFactory<?>> REGISTRY = HashBiMap.create();

    public static <T extends Entity> void register(EntityType<T> type, ModelAdapterFactory<T> factory) {
        REGISTRY.put(type, factory);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Entity> ModelAdapterFactory<T> getFactory(EntityType<T> entityType) {
        return (ModelAdapterFactory<T>) REGISTRY.get(entityType);
    }

    public static <T extends Entity> ModelAdapter<T, ?> createModelAdapter(T entity) {
        var factory = getFactory(getEntityType(entity));
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);

        if (factory != null) {
            return factory.get(renderer);
        } else if (renderer instanceof LivingEntityRenderer) {
            return LivingModelAdapter.fromEntityRenderer(renderer);
        } else {
            return new BasicModelAdapter<>(renderer);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> EntityType<T> getEntityType(T entity) {
        return (EntityType<T>) entity.getType();
    }

    // The amount of effort it takes to juggle all these generics is INSANE

    static {
        register(EntityType.ITEM, r -> new ItemModelAdapter((ItemEntityRenderer) r));
    }
}
