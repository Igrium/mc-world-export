package com.igrium.worldexport.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.class)
public interface AccessorItemRenderState {

    @Accessor("layers")
    ItemRenderState.LayerRenderState[] getLayers();
}
