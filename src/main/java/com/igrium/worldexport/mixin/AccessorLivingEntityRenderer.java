package com.igrium.worldexport.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface AccessorLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Invoker("setupRotations")
    void invokeSetupRotations(S state, PoseStack matrices, float bodyYaw, float baseHeight);

    @Invoker("scale")
    void invokeScale(S state, PoseStack matrices);

    @Invoker("isBodyVisible")
    boolean invokeIsBodyVisible(S state);

    @Accessor("layers")
    List<RenderLayer<S, M>> getLayers();

    @Invoker("shouldRenderLayers")
    boolean invokeShouldRenderLayers(S state);
}
