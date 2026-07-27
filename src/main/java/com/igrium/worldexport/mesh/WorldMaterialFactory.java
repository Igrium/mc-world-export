package com.igrium.worldexport.mesh;

import net.minecraft.world.level.block.state.BlockState;

public interface WorldMaterialFactory {
    String getMaterial(BlockState state);
}
