package com.igrium.worldexport.mixin;

import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(EquipmentLayerRenderer.class)
public interface AccessorEquipmentLayerRenderer {
    @Accessor("equipmentAssets")
    EquipmentAssetManager getEquipmentAssets();
}
