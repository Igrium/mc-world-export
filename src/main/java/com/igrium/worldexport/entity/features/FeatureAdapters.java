package com.igrium.worldexport.entity.features;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FeatureAdapters {
    @SuppressWarnings("rawtypes")
    private static final Map<Class<? extends RenderLayer>, FeatureAdapter.Factory> REGISTRY = new HashMap<>();

    public static <S extends EntityRenderState> void register(
            Class<? extends RenderLayer<S, ?>> renderClass, FeatureAdapter.Factory<S, ?> factory) {
        REGISTRY.put(renderClass, factory);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState> FeatureAdapter.Factory<S, ?> getFactory(
            Class<? extends RenderLayer<S, ?>> renderClass) {
        return (FeatureAdapter.Factory<S, ?>) REGISTRY.get(renderClass);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState> FeatureAdapter<S, ?> create(RenderLayer<S, ?> renderer) {
        Class<? extends RenderLayer<S, ?>> clazz = (Class<? extends RenderLayer<S, ?>>) renderer.getClass();
        var factory = getFactory(clazz);
        return factory != null ? factory.create(renderer) : null;
    }

    static {
        // TODO: Find a type safe(r) way to do this. I'm tired of all this generic bullshit.
        REGISTRY.put(HumanoidArmorLayer.class, ArmorFeatureAdapter::create);
        REGISTRY.put(ItemInHandLayer.class, HeldItemFeatureAdapter::create);
        REGISTRY.put(PlayerItemInHandLayer.class, HeldItemFeatureAdapter::create);
    }
}
