package org.scaffoldeditor.worldexport.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resource.metadata.ResourceMetadata;
import org.scaffoldeditor.worldexport.mat.sprite.SpriteAnimMetaProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.util.Identifier;

@Mixin(SpriteContents.class)
public class SpriteMixin implements SpriteAnimMetaProvider {
    
    @Unique
    private AnimationResourceMetadata animMeta;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(Identifier id, SpriteDimensions dimensions, NativeImage image, ResourceMetadata metadata, CallbackInfo ci, @Local AnimationResourceMetadata animMeta) {
        this.animMeta = animMeta;
    }

    public AnimationResourceMetadata getAnimData() {
        return animMeta;
    }
}
