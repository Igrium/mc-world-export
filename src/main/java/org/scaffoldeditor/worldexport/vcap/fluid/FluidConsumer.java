package org.scaffoldeditor.worldexport.vcap.fluid;

import java.util.Optional;

import net.minecraft.util.math.BlockPos;

public interface FluidConsumer {
    Optional<FluidDomain> fluidAt(BlockPos pos);

    /**
     * Add a fluid domain to the frame. This does <i>not</i> actually add the mesh
     * data. It just caches it for future use.
     * 
     * @param fluid The fluid domain.
     * @throws IndexOutOfBoundsException If the fluid domain extends out of the
     *                                   bounds of the vcap.
     */
    void putFluid(FluidDomain fluid) throws IndexOutOfBoundsException;
}
