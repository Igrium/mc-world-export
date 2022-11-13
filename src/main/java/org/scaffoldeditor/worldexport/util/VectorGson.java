package org.scaffoldeditor.worldexport.util;

import java.lang.reflect.Type;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * A simple Gson adapter for Vector3ic
 */
public class VectorGson implements JsonSerializer<Vector3ic>, JsonDeserializer<Vector3ic> {

    @Override
    public Vector3ic deserialize(JsonElement element, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (!element.isJsonArray()) {
            throw new JsonParseException("Vector must be a json array!");
        }
        JsonArray array = element.getAsJsonArray();
        return new Vector3i(array.get(0).getAsInt(),
                array.get(1).getAsInt(),
                array.get(2).getAsInt());
    }

    @Override
    public JsonElement serialize(Vector3ic vector, Type type, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        array.add(vector.x());
        array.add(vector.y());
        array.add(vector.z());
        return array;
    }
    
}
