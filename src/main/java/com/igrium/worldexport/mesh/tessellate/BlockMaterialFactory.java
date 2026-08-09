package com.igrium.worldexport.mesh.tessellate;

import net.minecraft.world.level.block.state.BlockState;

public interface BlockMaterialFactory {
    // TODO: do we want to call ModelRendererer.forceOpaque anywhere?
    String getMaterial(BlockState state);
}
