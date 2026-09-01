package com.igrium.worldexport.event;

import com.igrium.worldexport.entity.EntityCapture;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EntityCaptureEvents {
    private EntityCaptureEvents() {
    }

    /**
     * Called before an entity is captured during replay export.
     * Can be used to cancel entity capture by returning false.
     */
    public static final Event<BeforeCaptureEntityCallback> BEFORE_CAPTURE_ENTITY = EventFactory.createArrayBacked(
            BeforeCaptureEntityCallback.class, listeners -> (capture, entity, tick) -> {
                for (var l : listeners) {
                    if (!l.beforeCaptureEntity(capture, entity, tick)) {
                        return false;
                    }
                }
                return true;
            }
    );

    /**
     * Called before a block entity is captured during replay export.
     * Can be used to cancel entity capture by returning false.
     */
    public static final Event<BeforeCaptureBlockEntityCallback> BEFORE_CAPTURE_BLOCK_ENTITY =
            EventFactory.createArrayBacked(
            BeforeCaptureBlockEntityCallback.class, listeners -> (capture, entity, tick) -> {
                for (var l : listeners) {
                    if (!l.beforeCaptureBlockEntity(capture, entity, tick)) {
                        return false;
                    }
                }
                return true;
            }
    );

    public interface BeforeCaptureEntityCallback {
        boolean beforeCaptureEntity(EntityCapture entityCapture, Entity entity, int tick);
    }

    public interface BeforeCaptureBlockEntityCallback {
        boolean beforeCaptureBlockEntity(EntityCapture entityCapture, BlockEntity blockEntity, int tick);
    }
}
