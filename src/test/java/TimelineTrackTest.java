import com.google.gson.JsonObject;
import com.igrium.worldexport.timeline.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TimelineTrackTest {
    @Test void duplicateInitialTimestampIsReplaced(){
        var t=new EnvironmentTrack();t.sample(0,"overworld",1,1000,1000,true,false,false,0,0);t.sample(0,"overworld",2,1001,1001,true,true,false,.1f,0);
        var keys=t.snapshot().getAsJsonArray("keys");assertEquals(1,keys.size());assertTrue(keys.get(0).getAsJsonObject().get("raining").getAsBoolean());
    }
    @Test void clockJumpsAndFractionalExportTicksArePreserved(){
        var t=new EnvironmentTrack();t.sample(0,"overworld",1,23999,23999,true,false,false,0,0);t.sample(.25,"overworld",2,1000,1000,true,true,true,.5f,.25f);
        var key=t.snapshot().getAsJsonArray("keys").get(1).getAsJsonObject();assertEquals(.25,key.get("tick").getAsDouble());assertEquals(1000,key.get("defaultClockTime").getAsLong());assertEquals(.25,key.get("thunder").getAsDouble());
    }
    @Test void environmentSnapshotsAreIndependent(){
        var t=new EnvironmentTrack();t.sample(0,"nether",1,0,2000,false,false,false,0,0);var before=t.snapshot();t.sample(1,"nether",2,0,2001,false,false,false,0,0);assertEquals(1,before.getAsJsonArray("keys").size());assertFalse(before.getAsJsonArray("keys").get(0).getAsJsonObject().get("weatherAllowed").getAsBoolean());
    }
    @Test void duplicateServerEventsAreDeduplicatedBySessionAndSequence(){
        var t=new EventTrack();var d=new JsonObject();d.addProperty("level",2);t.event(1,"run:1","job_level","bridge","receipt",d);t.event(2,"run:1","job_level","bridge","receipt",d);t.event(3,"otherRun:1","job_level","bridge","receipt",d);
        assertEquals(2,t.snapshot().getAsJsonArray("cues").size());
    }
    @Test void snapshotsAndRepeatedIdlessEventsRemainDistinct(){
        var t=new EventTrack();var d=new JsonObject();d.addProperty("baseline",true);t.event(0,null,"snapshot","client","observed",d);t.event(1,null,"snapshot","client","observed",d);assertEquals(2,t.snapshot().getAsJsonArray("cues").size());assertFalse(t.snapshot().get("completeServerHistoryGuaranteed").getAsBoolean());
    }
    @Test void eventCopiesDoNotFollowMutations(){
        var t=new EventTrack();var d=new JsonObject();d.addProperty("level",2);t.event(0,"run:1","job_level","bridge","receipt",d);d.addProperty("level",3);assertEquals(2,t.snapshot().getAsJsonArray("cues").get(0).getAsJsonObject().getAsJsonObject("data").get("level").getAsInt());
    }
}
