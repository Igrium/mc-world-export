package com.igrium.worldexport.tex;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.javagl.obj.Mtl;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A material with additional, custom properties for blender to use.
 *
 * @param mtl        Material data.
 * @param properties Custom properties.
 */
public record ReplayMtl(@NonNull Mtl mtl, @NonNull JsonObject properties) {
    public ReplayMtl(Mtl mtl) {
        this(mtl, new JsonObject());
    }

    public String getName() {
        return mtl.getName();
    }
}
