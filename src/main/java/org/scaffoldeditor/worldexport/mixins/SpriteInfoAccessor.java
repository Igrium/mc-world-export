package org.scaffoldeditor.worldexport.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.Sprite;

@Mixin(Sprite.Info.class)
public interface SpriteInfoAccessor {
    
    /**
     * It's <i>really</i> stupid that a mixin is need for this, but whatever.
     */
    @Accessor("animationData")
    AnimationResourceMetadata getAnimData();
}
