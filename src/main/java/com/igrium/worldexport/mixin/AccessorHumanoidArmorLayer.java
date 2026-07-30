package com.igrium.worldexport.mixin;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HumanoidArmorLayer.class)
public interface AccessorHumanoidArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
//    @Accessor
//    A getInnerModel();
//
//    @Accessor
//    A getOuterModel();
//
//    @Accessor
//    A getInnerModelBaby();
//
//    @Accessor
//    A getOuterModelBaby();

    @Accessor
    EquipmentLayerRenderer getEquipmentRenderer();

    @Invoker
    A invokeGetArmorModel(S state, EquipmentSlot slot);

//    @Invoker
//    void invokeSetPartVisibility(A bipedModel, EquipmentSlot slot);
}
