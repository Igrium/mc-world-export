package com.igrium.worldexport.compat.replaymod.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ExportPhase {

    public static final String INIT = "worldexport.gui.status.init";
    public static final String CAPTURE = "worldexport.gui.status.capture";
    public static final String SERIALIZATION = "worldexport.gui.status.serialization";

    public static final String WORLD = "worldexport.gui.status.world";
    public static final String ENTITIES = "worldexport.gui.status.entities";
    public static final String MATERIALS = "worldexport.gui.status.materials";
    public static final String FINISHED = "worldexport.gui.status.finished";
}
