package org.scaffoldeditor.worldexport.mat.sprite;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;

/**
 * A mixin-supported interface for extracting sprite initialization metadata.
 */
public interface SpriteAnimMetaProvider {
    AnimationResourceMetadata getAnimData();
}
