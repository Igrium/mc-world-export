package com.igrium.worldexport.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShulkerBoxRenderer.class)
public interface AccessorShulkerBoxRenderer {
    @Accessor("model")
    ShulkerBoxRenderer.ShulkerBoxModel getModel();
}
