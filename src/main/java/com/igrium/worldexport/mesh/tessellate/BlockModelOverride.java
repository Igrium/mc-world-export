package com.igrium.worldexport.mesh.tessellate;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import org.jetbrains.annotations.Nullable;

/**
 * Forces a custom block model to be used on a block during export
 *
 * @param model    Model to use
 * @param material Material to assign the block. <code>null</code> to use the default world material.
 * @param faceMats If set, faces with specific tint indices will use a different material.
 * @apiNote For any given export section, any tint indices present in <code>faceMats</code> will be proxied to 0.
 * Choose values that won't conflict with other mods.
 */
public record BlockModelOverride(BlockStateModel model, @Nullable String material,
                                 @Nullable Int2ObjectMap<String> faceMats) {

}
