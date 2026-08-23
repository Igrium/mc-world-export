package com.igrium.worldexport.mixin_helper;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface SpriteAnimMetaProvider {
    @Nullable AnimationMetadataSection worldexport$getAnimData();

    static @Nullable AnimationMetadataSection getAnimData(SpriteContents contents) {
        return ((SpriteAnimMetaProvider) contents).worldexport$getAnimData();
    }
}
