package org.scaffoldeditor.worldexport.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;

@Mixin(Sprite.class)
public interface SpriteAccessor {

    @Accessor("images")
    NativeImage[] getImages();
}
