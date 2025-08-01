package com.igrium.worldexport.mixin;

import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ArmorFeatureRenderer.class)
public interface AccessorArmorFeatureRenderer<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> {
    @Accessor
    A getInnerModel();

    @Accessor
    A getOuterModel();

    @Accessor
    A getBabyInnerModel();

    @Accessor
    A getBabyOuterModel();

    @Accessor
    EquipmentRenderer getEquipmentRenderer();

    @Invoker
    A invokeGetModel(S state, EquipmentSlot slot);

    @Invoker
    void invokeSetVisible(A bipedModel, EquipmentSlot slot);
}
