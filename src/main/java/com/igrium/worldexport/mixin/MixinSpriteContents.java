package com.igrium.worldexport.mixin;

import com.igrium.worldexport.mixin_helper.SpriteAnimMetaProvider;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Mixin(SpriteContents.class)
public class MixinSpriteContents implements SpriteAnimMetaProvider {

    @Unique
    private @Nullable AnimationMetadataSection animationMetadataSection;

    @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
            at = @At("RETURN"))
    void onInit(final Identifier name,
                final FrameSize frameSize,
                final NativeImage image,
                final Optional<AnimationMetadataSection> animationInfo,
                final List<MetadataSectionType.WithValue<?>> additionalMetadata,
                final Optional<TextureMetadataSection> textureInfo, CallbackInfo ci) {
        this.animationMetadataSection = animationInfo.orElse(null);
    }


    @Override
    public @Nullable AnimationMetadataSection worldexport$getAnimData() {
        return this.animationMetadataSection;
    }
}
