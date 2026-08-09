package com.igrium.worldexport.mesh;

import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Forces a custom block model to be used on a block during export
 *
 * @param model    Model to use
 * @param material Material to assign the block. <code>null</code> to use the default world material.
 */
public record BlockModelOverride(BlockStateModel model, @Nullable String material) {
    public interface Factory {
        @Nullable BlockModelOverride get(BlockStateModelSet models, BlockState state);
    }
}
