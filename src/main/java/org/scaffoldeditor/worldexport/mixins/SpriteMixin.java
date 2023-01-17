package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.mat.sprite.SpriteAnimMetaProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;

@Mixin(Sprite.class)
public class SpriteMixin implements SpriteAnimMetaProvider {
    
    AnimationResourceMetadata animData;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(SpriteAtlasTexture atlas, Sprite.Info info, int maxLevel, int atlasWidth, int atlasHeight, int x,
            int y, NativeImage image, CallbackInfo ci) {
        animData = ((SpriteInfoAccessor) (Object) info).getAnimData();
    }

    public AnimationResourceMetadata getAnimData() {
        return animData;
    }
}
