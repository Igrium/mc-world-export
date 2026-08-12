package com.igrium.worldexport.mixin;

import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WingsLayer.class)
public interface AccessorWingsLayer {
    @Accessor
    ElytraModel getElytraModel();

    @Accessor
    ElytraModel getElytraBabyModel();

    @Accessor
    EquipmentLayerRenderer getEquipmentRenderer();

    @Invoker
    @Nullable
    static Identifier invokeGetPlayerElytraTexture(HumanoidRenderState state) {
        throw new AssertionError();
    }
}