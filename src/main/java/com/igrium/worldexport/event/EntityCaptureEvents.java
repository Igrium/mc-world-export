package com.igrium.worldexport.event;

import com.igrium.worldexport.entity.EntityCapture;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;

public class EntityCaptureEvents {
    private EntityCaptureEvents() {
    }

    /**
     * Called before an entity is captured during replay export. Can be used to cancel entity capture by returning false.
     */
    public static final Event<BeforeCaptureEntityCallback> BEFORE_CAPTURE_ENTITY = EventFactory.createArrayBacked(
            BeforeCaptureEntityCallback.class, listeners -> (capture, entity, tick) -> {
                for (var l : listeners) {
                    if (!l.beforeCaptureEntity(capture, entity, tick)) {
                        return false;
                    }
                }
                return true;
            });

    public interface BeforeCaptureEntityCallback {
        boolean beforeCaptureEntity(EntityCapture entityCapture, Entity entity, int tick);
    }

}
