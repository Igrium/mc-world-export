package com.igrium.worldexport.mixin;

import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FeatureRenderer.class)
public interface AccessorFeatureRenderer<S extends EntityRenderState, M extends EntityModel<? super S>> {

    @Accessor("context")
    FeatureRendererContext<S, M> getContext();
}
