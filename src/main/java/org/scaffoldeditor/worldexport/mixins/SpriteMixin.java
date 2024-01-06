package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.mat.sprite.SpriteAnimMetaProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.util.Identifier;

@Mixin(SpriteContents.class)
public class SpriteMixin implements SpriteAnimMetaProvider {
    
    AnimationResourceMetadata animData;

    @Inject(method = "<init>", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    public void onInit(Identifier id, SpriteDimensions dimensions, NativeImage image, ResourceMetadata metadata,
            CallbackInfo ci, AnimationResourceMetadata animationResourceMetadata) {
        this.animData = animationResourceMetadata;
    }

    public AnimationResourceMetadata getAnimData() {
        return animData;
    }
}
