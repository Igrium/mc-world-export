import com.google.gson.JsonObject;
import com.igrium.worldexport.audio.SoundCueTrack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoundCueTrackTest {
    private JsonObject cue(String event, String category, boolean loop) {
        var cue=new JsonObject();cue.addProperty("soundId",event);cue.addProperty("category",category);cue.addProperty("looping",loop);return cue;
    }
    @Test void repeatedSoundsRemainDistinctAtFractionalTicks() {
        var track=new SoundCueTrack();track.start(new Object(),cue("step","blocks",false),1.25);track.start(new Object(),cue("step","blocks",false),1.75);
        var cues=track.snapshot().getAsJsonArray("cues");assertEquals(2,cues.size());assertEquals(1.25,cues.get(0).getAsJsonObject().get("startTick").getAsDouble());
        assertNotEquals(cues.get(0).getAsJsonObject().get("cueId"),cues.get(1).getAsJsonObject().get("cueId"));
    }
    @Test void stopFilterDoesNotStopUnrelatedCategories() {
        var track=new SoundCueTrack();track.start(new Object(),cue("same","blocks",true),0);track.start(new Object(),cue("same","ambient",true),0);
        track.stopMatching("same","blocks",5,"explicit_stop_filter");var cues=track.snapshot().getAsJsonArray("cues");
        assertEquals(5,cues.get(0).getAsJsonObject().get("endTick").getAsDouble());assertFalse(cues.get(1).getAsJsonObject().has("endTick"));
    }
    @Test void captureEndBoundsLoopsWithoutInventingOneShotDuration() {
        var track=new SoundCueTrack();track.start(new Object(),cue("once","neutral",false),1);track.start(new Object(),cue("rain","weather",true),2);track.finish(10);
        var cues=track.snapshot().getAsJsonArray("cues");assertFalse(cues.get(0).getAsJsonObject().has("endTick"));assertEquals("capture_end",cues.get(1).getAsJsonObject().get("endReason").getAsString());assertFalse(track.snapshot().get("audioIncluded").getAsBoolean());
    }
    @Test void identityIsUsedAndSnapshotsAreIndependent() {
        var track=new SoundCueTrack();Object first=new String("same"),second=new String("same");track.start(first,cue("one","blocks",false),0);track.start(second,cue("two","blocks",false),0);
        var before=track.snapshot();track.stop(first,3,"stop");assertFalse(before.getAsJsonArray("cues").get(0).getAsJsonObject().has("endTick"));assertFalse(track.snapshot().getAsJsonArray("cues").get(1).getAsJsonObject().has("endTick"));
    }
}
