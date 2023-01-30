package org.scaffoldeditor.worldexport.replay;

import java.io.Reader;

import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.scaffoldeditor.worldexport.Constants;
import org.scaffoldeditor.worldexport.ReplayExportMod;
import org.scaffoldeditor.worldexport.util.VectorGson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public class ReplayMeta {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vector3ic.class, new VectorGson())
            .setPrettyPrinting()
            .create();
    
    public ReplayMeta() {}

    public ReplayMeta(ReplayMeta other) {
        this.version = other.version;
        this.encoder = other.encoder;
        this.offset = new Vector3i(other.offset);
    }

    public String version = Constants.REPLAY_FORMAT_VERSION;
    public String encoder = "Igriums Replay Exporter " + ReplayExportMod.getInstance().getModVersion();
    public Vector3ic offset = new Vector3i();

    public static String toJson(ReplayMeta meta) {
        return GSON.toJson(meta);
    }

    public static ReplayMeta fromJson(String json) throws JsonParseException {
        return GSON.fromJson(json, ReplayMeta.class);
    }

    public static ReplayMeta fromJson(Reader json) throws JsonParseException {
        return GSON.fromJson(json, ReplayMeta.class);
    }
}
