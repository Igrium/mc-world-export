package com.igrium.worldexport.mixin;

import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(EquipmentRenderer.class)
public interface AccessorEquipmentRenderer {
    @Accessor("equipmentModelLoader")
    EquipmentModelLoader getEquipmentModelLoader();
}
