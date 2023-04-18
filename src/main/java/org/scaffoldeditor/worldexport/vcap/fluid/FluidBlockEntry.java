package org.scaffoldeditor.worldexport.vcap.fluid;

import org.scaffoldeditor.worldexport.util.MeshComparator;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider.ModelInfo;

import net.minecraft.fluid.Fluid;

/**
 * A wrapper around an OBJ that permits for the quick comparison of fluids.
 */
public class FluidBlockEntry {
    private final Fluid fluid;
    private final ModelInfo model;

    public FluidBlockEntry(Fluid fluid, ModelInfo mesh) {
        this.fluid = fluid;
        this.model = mesh;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public ModelInfo getModel() {
        return model;
    }

    /**
     * Check if a fluid entry matches another fluid entry.
     * @param other The other fluid entry.
     * @param comparator Mesh comparator to use.
     * @param epsilon The threshold at which two vertices are considered equal.
     * @return
     */
    public boolean equals(FluidBlockEntry other, MeshComparator comparator, float epsilon) {
        if (!getFluid().equals(other.getFluid())) return false;
        return comparator.meshEquals(model.mesh(), other.getModel().mesh(), epsilon, 0);
    }
}
