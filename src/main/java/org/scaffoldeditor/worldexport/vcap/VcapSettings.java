package org.scaffoldeditor.worldexport.vcap;

import net.minecraft.util.math.ChunkPos;

public class VcapSettings {
    public enum FluidMode { 
        NONE(false, false),
        STATIC(true, false),
        DYNAMIC(true, true);

        private final boolean exportStatic;
        private final boolean exportDynamic;

        FluidMode(boolean exportStatic, boolean exportDynamic) {
            this.exportStatic = exportStatic;
            this.exportDynamic = exportDynamic;
        }

        public boolean exportStatic() {
            return exportStatic;
        }

        public boolean exportDynamic() {
            return exportDynamic;
        }
    }

    private FluidMode fluidMode = FluidMode.STATIC;
    private int lowerDepth = Integer.MIN_VALUE;
    private ChunkPos minChunk = new ChunkPos(0, 0);
    private ChunkPos maxChunk = new ChunkPos(0, 0);

    @Deprecated
    public boolean shouldExportFluids() {
        return fluidMode == FluidMode.DYNAMIC;
    }

    @Deprecated
    public VcapSettings exportFluids(boolean exportFluids) {
        fluidMode = exportFluids ? FluidMode.DYNAMIC : FluidMode.NONE;
        return this;
    }

    public FluidMode getFluidMode() {
        return fluidMode;
    }

    public VcapSettings setFluidMode(FluidMode fluidMode) {
        this.fluidMode = fluidMode;
        return this;
    }

    public boolean exportStaticFluids() {
        return fluidMode.exportStatic();
    }

    public boolean exportDynamicFluids() {
        return fluidMode.exportDynamic();
    }

    /**
     * Set the lower depth.
     * @param lowerDepth Lower depth in section coordinates.
     * @return <code>this</code>
     */
    public VcapSettings setLowerDepth(int lowerDepth) {
        this.lowerDepth = lowerDepth;
        return this;
    }

    /**
     * Get the lower depth.
     * @return Lower depth in section coordinates.
     */
    public int getLowerDepth() {
        return lowerDepth;
    }

    public ChunkPos getMinChunk() {
        return minChunk;
    }

    public ChunkPos getMaxChunk() {
        return maxChunk;
    }

    public VcapSettings setBBox(ChunkPos minChunk, ChunkPos maxChunk) {
        if (minChunk.x > maxChunk.x || minChunk.z > maxChunk.z) {
            throw new IllegalArgumentException("Min chunk "+minChunk+" must be less than max chunk "+maxChunk);
        }
        this.minChunk = minChunk;
        this.maxChunk = maxChunk;

        return this;
    }
}
