package com.igrium.worldexport.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface AccessorLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Invoker("setupTransforms")
    void invokeSetupTransforms(S state, MatrixStack matrices, float bodyYaw, float baseHeight);

    @Invoker("scale")
    void invokeScale(S state, MatrixStack matrices);

    @Invoker("isVisible")
    boolean invokeIsVisible(S state);
}
