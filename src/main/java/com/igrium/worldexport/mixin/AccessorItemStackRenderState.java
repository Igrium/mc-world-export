package com.igrium.worldexport.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface AccessorItemStackRenderState {

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] getLayers();

    @Accessor("activeLayerCount")
    int getActiveLayerCount();
}
