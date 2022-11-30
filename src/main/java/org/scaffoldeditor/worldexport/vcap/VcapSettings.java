package org.scaffoldeditor.worldexport.vcap;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class VcapSettings {
    public enum FluidMode { 
        NONE("None", false, false),
        STATIC("Static", true, false),
        DYNAMIC("Dynamic", true, true);

        private final boolean exportStatic;
        private final boolean exportDynamic;
        private final String name;

        FluidMode(String name, boolean exportStatic, boolean exportDynamic) {
            this.name = name;
            this.exportStatic = exportStatic;
            this.exportDynamic = exportDynamic;
        }

        public boolean exportStatic() {
            return exportStatic;
        }

        public boolean exportDynamic() {
            return exportDynamic;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private FluidMode fluidMode = FluidMode.STATIC;
    private int lowerDepth = Integer.MIN_VALUE;
    private ChunkPos minChunk = new ChunkPos(0, 0);
    private ChunkPos maxChunk = new ChunkPos(0, 0);

    private int fluidChunkSize = 16;

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
    
    public int getFluidChunkSize() {
        return fluidChunkSize;
    }

    public VcapSettings setFluidChunkSize(int fluidChunkSize) {
        this.fluidChunkSize = fluidChunkSize;
        return this;
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

    /**
     * Check if a given block is within the export region.
     * @param pos The block to check.
     * @return Is it in the export region?
     */
    public boolean isInExport(BlockPos pos) {
        return pos.getY() >= (getLowerDepth() * 16) && isInBBox(pos, minChunk, maxChunk);
    }

    private static boolean isInBBox(BlockPos pos, ChunkPos minChunk, ChunkPos maxChunk) {
        return (minChunk.getStartX() <= pos.getX()) && (pos.getX() < maxChunk.getStartX())
            && (minChunk.getStartZ() <= pos.getZ()) && (pos.getZ() < maxChunk.getStartZ());
    }
}
