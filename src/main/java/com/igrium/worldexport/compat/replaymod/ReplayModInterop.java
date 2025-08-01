package com.igrium.worldexport.compat.replaymod;

import com.igrium.worldexport.event.EntityCaptureEvents;
import com.replaymod.core.ReplayMod;
import com.replaymod.replay.camera.CameraEntity;

public class ReplayModInterop {
    public static void onInitReplayMod(ReplayMod replayMod) {
        // Don't export camera entity
        EntityCaptureEvents.BEFORE_CAPTURE_ENTITY.register((capture, entity, tick) -> !(entity instanceof CameraEntity));
    }
}
