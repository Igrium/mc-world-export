package org.scaffoldeditor.worldexport.replaymod;

import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

public final class ReplayExportSettings {
    private int viewDistance = 8;
    private int lowerDepth = 0;

    private FluidMode fluidMode = FluidMode.DYNAMIC;

    public int getViewDistance() {
        return viewDistance;
    }

    public ReplayExportSettings setViewDistance(int viewDistance) {
        if (viewDistance < 1) {
            throw new IllegalArgumentException("Minimum view distance is 1.");
        }

        this.viewDistance = viewDistance;
        return this;
    }
    
    /**
     * Get the lower depth.
     * @return Lower depth in section coordinates.
     */
    public int getLowerDepth() {
        return lowerDepth;
    }

    /**
     * Set the lower depth.
     * @param lowerDepth Lower depth in section coordinates.
     * @return <code>this</code>
     */
    public ReplayExportSettings setLowerDepth(int lowerDepth) {
        this.lowerDepth = lowerDepth;
        return this;
    }

    public FluidMode getFluidMode() {
        return fluidMode;
    }

    public ReplayExportSettings setFluidMode(FluidMode fluidMode) {
        this.fluidMode = fluidMode;
        return this;
    }
}
