package org.scaffoldeditor.worldexport.replaymod.util;

public final class ExportPhase {
    private ExportPhase() {};

    public static final String INIT = "worldexport.gui.status.init";
    public static final String CAPTURE = "worldexport.gui.status.capture";
    public static final String SERIALIZATION = "worldexport.gui.status.serialization";
    public static final String COMPILING_FRAMES = "worldexport.gui.status.compiling_frames";
    public static final String MESHES = "worldexport.gui.status.writing_meshes";
    public static final String VCAP_META = "orldexport.gui.status.vcap_meta";
    
    public static final String WORLD = "worldexport.gui.status.world";
    public static final String ENTITIES = "worldexport.gui.status.entities";
    public static final String MATERIALS = "worldexport.gui.status.materials";
    public static final String FINISHED = "worldexport.gui.status.finished";
}
