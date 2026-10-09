import com.google.gson.JsonObject;
import com.igrium.worldexport.timeline.ReplayEvent;
import com.igrium.worldexport.timeline.EventTrack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayEventTest {
    @Test void eventSubmissionAndAccessCannotAlterCapturedData() {
        var data = new JsonObject(); data.addProperty("level", 2);
        var event = new ReplayEvent("session:1", "example:level_changed", "example", "receipt", data);
        data.addProperty("level", 3);
        event.data().addProperty("level", 4);
        var track = new EventTrack();
        track.event(.25, event.id(), event.kind(), event.source(), event.timing(), event.data());
        var cue = track.snapshot().getAsJsonArray("cues").get(0).getAsJsonObject();
        assertEquals(2, cue.getAsJsonObject("data").get("level").getAsInt());
        assertEquals(.25, cue.get("tick").getAsDouble());
    }
    @Test void identifiersOnlyDeduplicateWithinTheirSource() {
        var track = new EventTrack(); var data = new JsonObject();
        track.event(0, "session:1", "example:level_changed", "first", "receipt", data);
        track.event(1, "session:1", "example:level_changed", "first", "receipt", data);
        track.event(1, "session:1", "example:level_changed", "second", "receipt", data);
        assertEquals(2, track.snapshot().getAsJsonArray("cues").size());
    }
    @Test void missingKindSourceOrTimingAreRejected() {
        var data = new JsonObject();
        assertThrows(IllegalArgumentException.class, () -> new ReplayEvent(null, " ", "example", "receipt", data));
        assertThrows(IllegalArgumentException.class, () -> new ReplayEvent(null, "example:event", "", "receipt", data));
        assertThrows(IllegalArgumentException.class, () -> new ReplayEvent(null, "example:event", "example", "", data));
        assertThrows(NullPointerException.class, () -> new ReplayEvent(null, "example:event", "example", "receipt", null));
    }
}
