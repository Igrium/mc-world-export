package com.igrium.worldexport.mixin;

import net.minecraft.client.model.object.bell.BellModel;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BellRenderer.class)
public interface AccessorBellRenderer {
    @Accessor("model")
    BellModel getModel();
}
