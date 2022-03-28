package org.scaffoldeditor.worldexport.vcap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scaffoldeditor.worldexport.Constants;

/**
 * Represents the general metadata for a VCap file.
 */
public class VcapMeta {
    public String version = Constants.REPLAY_FORMAT_VERSION;
    public String encoder = "Minecraft World Exporter";
    public final List<String> faceLayers = new ArrayList<>();

    /**
     * A mapping of model IDs and the namespaced ids of the blocks they represent.
     * Used to infer the block id of any block within the file.
     */
    public final Map<String, String> blockTypes = new HashMap<>();

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
