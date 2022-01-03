package org.scaffoldeditor.worldexport.vcap;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the general metadata for a VCap file.
 */
public class VcapMeta {
    public String version = "0.1.0";
    public String encoder = "Minecraft World Exporter";
    public final List<String> faceLayers = new ArrayList<>();

    /**
     * Create a VCap metadata object.
     * @param numLayers The number of face layers in the file.
     */
    public VcapMeta(int numLayers) {
        for (int i = 0; i < numLayers; i++) {
            faceLayers.add(MeshWriter.genGroupName(i));
        }
    }
}
