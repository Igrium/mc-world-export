package com.igrium.worldexport.entity.features;

import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FeatureAdapters {
    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends FeatureRenderer>, FeatureAdapter.Factory> REGISTRY = new HashMap<>();

    public static <S extends EntityRenderState> void register(
            Class<? extends FeatureRenderer<S, ?>> renderClass, FeatureAdapter.Factory<S, ?> factory) {
        REGISTRY.put(renderClass, factory);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState> FeatureAdapter.Factory<S, ?> getFactory(
            Class<? extends FeatureRenderer<S, ?>> renderClass) {
        return (FeatureAdapter.Factory<S, ?>) REGISTRY.get(renderClass);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState> FeatureAdapter<S, ?> create(FeatureRenderer<S, ?> renderer) {
        Class<? extends FeatureRenderer<S, ?>> clazz = (Class<? extends FeatureRenderer<S, ?>>) renderer.getClass();
        var factory = getFactory(clazz);
        return factory != null ? factory.create(renderer) : null;
    }

    static {
        // TODO: Find a type safe(r) way to do this. I'm tired of all this generic bullshit.
        REGISTRY.put(ArmorFeatureRenderer.class, ArmorFeatureAdapter::create);
        REGISTRY.put(HeldItemFeatureRenderer.class, HeldItemFeatureAdapter::create);
        REGISTRY.put(PlayerHeldItemFeatureRenderer.class, HeldItemFeatureAdapter::create);
    }
}
