package com.igrium.worldexport.entity;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
        var renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);

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

//    @SuppressWarnings("unchecked")
//    public static <T extends Entity> ModelAdapter<? super T, ?> createModelAdapter(EntityType<T> entityType) {
//        var factory = getFactory(entityType);
//        if (factory != null) {
//            return factory.get();
//        } else if (LivingEntity.class.isAssignableFrom(entityType.getBaseClass())) {
//            EntityRenderer<?, ?> renderer = ((AccessorEntityRenderDispatcher) MinecraftClient.getInstance()
//                    .getEntityRenderDispatcher()).getRenderers().get(entityType);
//
//            if (renderer instanceof LivingEntityRenderer livingRenderer) {
//                return modelAdapterFromRenderer(livingRenderer);
//            }
//        }
//        // TODO: Dynamic model adapter generation
//        return new BasicModelAdapter<>();
//    }
}
