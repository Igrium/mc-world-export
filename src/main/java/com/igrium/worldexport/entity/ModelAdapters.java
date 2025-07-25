package com.igrium.worldexport.entity;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.worldexport.mixin.AccessorEntityRenderDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class ModelAdapters {

    public interface ModelAdapterFactory<T extends Entity, S extends EntityRenderState> {
        ModelAdapter<T, ?> get();
    }

    private static final BiMap<EntityType<?>, ModelAdapterFactory<?, ?>> REGISTRY = HashBiMap.create();

    public static <T extends Entity, S extends EntityRenderState> void register(EntityType<T> type, ModelAdapterFactory<? super T, ? super S> factory) {
        REGISTRY.put(type, factory);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Entity, S extends EntityRenderState> ModelAdapterFactory<? super T, ? super S> getFactory(EntityType<T> entityType) {
        return (ModelAdapterFactory<? super T, ? super S>) REGISTRY.get(entityType);
    }

    // The amount of effort it takes to juggle all these generics is INSANE

    @SuppressWarnings("unchecked")
    public static <T extends Entity> ModelAdapter<? super T, ?> createModelAdapter(EntityType<T> entityType) {
        var factory = getFactory(entityType);
        if (factory != null) {
            return factory.get();
        } else if (LivingEntity.class.isAssignableFrom(entityType.getBaseClass())) {
            EntityRenderer<?, ?> renderer = ((AccessorEntityRenderDispatcher) MinecraftClient.getInstance()
                    .getEntityRenderDispatcher()).getRenderers().get(entityType);

            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                return modelAdapterFromRenderer(livingRenderer);
            }
        }
        // TODO: Dynamic model adapter generation
        return new BasicModelAdapter<>();
    }

    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> LivingModelAdapter<T, S, M> modelAdapterFromRenderer(LivingEntityRenderer<T, S, M> renderer) {
        S state = renderer.createRenderState();
        return new LivingModelAdapter<>((Class<? extends S>) state.getClass());
    }
}
