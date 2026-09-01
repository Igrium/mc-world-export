package com.igrium.worldexport.mixin;

import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.function.Function;

@Mixin(SkullBlockRenderer.class)
public interface AccessorSkullBlockRenderer {
    @Accessor("modelByType")
    Function<SkullBlock.Type, SkullModelBase> getModelByType();

    @Accessor("SKIN_BY_TYPE")
    static Map<SkullBlock.Type, Identifier> getSkinByType() {
        throw new AssertionError();
    }

    @Accessor("playerSkinRenderCache")
    PlayerSkinRenderCache getPlayerSkinRenderCache();
}
