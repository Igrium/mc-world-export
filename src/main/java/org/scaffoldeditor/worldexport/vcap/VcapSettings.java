package org.scaffoldeditor.worldexport.vcap;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

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
    private BlockBox bounds = BlockBox.infinite();
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

    public BlockBox getBounds() {
        return bounds;
    }

    public void setBounds(BlockBox bounds) {
        this.bounds = bounds;
    }

    /**
     * Check if a given block is within the export region.
     * @param pos The block to check.
     * @return Is it in the export region?
     */
    public boolean isInExport(BlockPos pos) {
        return bounds.contains(ChunkSectionPos.from(pos));
    }
}
