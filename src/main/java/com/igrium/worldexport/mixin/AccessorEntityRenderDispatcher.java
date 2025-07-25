package com.igrium.worldexport.mixin;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public interface AccessorEntityRenderDispatcher {

    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?, ?>> getRenderers();
}
