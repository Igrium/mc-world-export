package org.scaffoldeditor.worldexport.replaymod.export;

import java.io.File;

import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

public final class ReplayExportSettings {
    private int viewDistance = 8;
    private int lowerDepth = 0;

    private FluidMode fluidMode = FluidMode.DYNAMIC;

    private File outputFile = new File("output.replay");

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

    public File getOutputFile() {
        return outputFile;
    }

    public ReplayExportSettings setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        return this;
    }
}
