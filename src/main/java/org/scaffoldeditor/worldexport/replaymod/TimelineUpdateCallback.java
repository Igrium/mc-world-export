package org.scaffoldeditor.worldexport.replaymod;

import com.replaymod.replaystudio.pathing.path.Timeline;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Called whenever the playhead on the replay timeline updates.
 */
public interface TimelineUpdateCallback {
    Event<TimelineUpdateCallback> EVENT = EventFactory.createArrayBacked(TimelineUpdateCallback.class, 
    listeners -> (timeline, replayHandler, time) -> {
        for (TimelineUpdateCallback listener : listeners) {
            listener.onUpdate(null, replayHandler, time);
        }
    });

    /**
     * Called whenever the playhead on the replay timeline moves.
     * @param timeline The timeline instance.
     * @param replayHandler The current replay handler.
     * @param time The new time.
     */
    void onUpdate(Timeline timeline, Object replayHandler, long time);
}
