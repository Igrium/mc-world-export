package com.igrium.worldexport.entity;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public class ModelAdapters {

    public interface ModelAdapterFactory<T extends Entity> {
        EntityModelAdapter<T, ?> get();
    }

    private static final BiMap<EntityType<?>, ModelAdapterFactory<?>> REGISTRY = HashBiMap.create();

    public static <T extends Entity> void register(EntityType<T> type, ModelAdapterFactory<? super T> factory) {
        REGISTRY.put(type, factory);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Entity> ModelAdapterFactory<? super T> getFactory(EntityType<T> entityType) {
        return (ModelAdapterFactory<? super T>) REGISTRY.get(entityType);
    }

    public static <T extends Entity> EntityModelAdapter<? super T, ?> createModelAdapter(EntityType<T> entityType) {
        var factory = getFactory(entityType);
        if (factory != null) {
            return factory.get();
        } else {
            // TODO: Dynamic model adapter generation
            return new EntityModelAdapter<>();
        }
    }
}
