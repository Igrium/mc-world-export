package org.scaffoldeditor.worldexport.vcap;

public class VcapSettings {
    private boolean exportFluids = true;

    public boolean isExportFluids() {
        return exportFluids;
    }

    public VcapSettings setExportFluids(boolean exportFluids) {
        this.exportFluids = exportFluids;
        return this;
    }
}
