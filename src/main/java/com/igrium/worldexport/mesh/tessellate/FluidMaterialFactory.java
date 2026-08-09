package com.igrium.worldexport.mesh.tessellate;

import net.minecraft.world.level.material.FluidState;

public interface FluidMaterialFactory {
    String getMaterial(FluidState fluidState);
}
