package org.scaffoldeditor.worldexport.vcap;

import net.minecraft.util.math.ChunkPos;

public class VcapSettings {
    private boolean exportFluids = true;
    private int lowerDepth = Integer.MIN_VALUE;
    private ChunkPos minChunk = new ChunkPos(0, 0);
    private ChunkPos maxChunk = new ChunkPos(0, 0);

    public boolean shouldExportFluids() {
        return exportFluids;
    }

    public VcapSettings exportFluids(boolean exportFluids) {
        this.exportFluids = exportFluids;
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
}
