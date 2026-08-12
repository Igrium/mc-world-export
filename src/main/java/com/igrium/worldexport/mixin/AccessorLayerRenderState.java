package com.igrium.worldexport.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface AccessorLayerRenderState {
    @Accessor("quads")
    @Nullable List<BakedQuad> getModel();

    @Accessor("itemTransform")
    ItemTransform getItemTransform();

    @Accessor("localTransform")
    Matrix4f getLocalTransform();
}