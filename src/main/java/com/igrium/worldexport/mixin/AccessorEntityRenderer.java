package com.igrium.worldexport.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public interface AccessorEntityRenderer {
    @Accessor("reusedState")
    EntityRenderState getReusedState();
}
