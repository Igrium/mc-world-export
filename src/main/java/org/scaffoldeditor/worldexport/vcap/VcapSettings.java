package org.scaffoldeditor.worldexport.vcap;

public class VcapSettings {
    private boolean exportFluids = true;
    private int lowerDepth = Integer.MIN_VALUE;

    public boolean shouldExportFluids() {
        return exportFluids;
    }

    public VcapSettings exportFluids(boolean exportFluids) {
        this.exportFluids = exportFluids;
        return this;
    }

    public VcapSettings setLowerDepth(int lowerDepth) {
        this.lowerDepth = lowerDepth;
        return this;
    }

    public int getLowerDepth() {
        return lowerDepth;
    }
}
