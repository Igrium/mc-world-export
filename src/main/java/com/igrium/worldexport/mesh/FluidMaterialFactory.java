package com.igrium.worldexport.mesh;

import net.minecraft.world.level.material.FluidState;

public interface FluidMaterialFactory {
    String getMaterial(FluidState fluidState);
}
