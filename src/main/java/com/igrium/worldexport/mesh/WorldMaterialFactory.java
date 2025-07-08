package com.igrium.worldexport.mesh;

import net.minecraft.block.BlockState;

public interface WorldMaterialFactory {
    String getMaterial(BlockState state);
}
