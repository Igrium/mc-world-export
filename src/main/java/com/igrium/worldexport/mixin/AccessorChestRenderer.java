package com.igrium.worldexport.mixin;

import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChestRenderer.class)
public interface AccessorChestRenderer {
    @Accessor("models")
    MultiblockChestResources<ChestModel> getModels();
}
