package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.apache.commons.io.IOUtils;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Represents a simple material that can be imported into Blender.
 */
public class Material {
    public static class Field {
        public enum FieldType { SCALAR, VECTOR, TEXTURE }

        private double scalar;
        private Vector3dc vector;
        private String texture;
        public final FieldType mode;

        public Field(double scalar) {
            this.mode = FieldType.SCALAR;
            this.scalar = scalar;
        }

        public Field(Vector3dc vector) {
            this.mode = FieldType.VECTOR;
            this.vector = vector;
        }

        public Field(String texture) {
            this.mode = FieldType.TEXTURE;
            this.texture = texture;
        }

        public double getScalar() {
            if (mode != FieldType.SCALAR) throw new IllegalStateException("Tried to access a scalar on a field of type "+mode);
            return this.scalar;
        }

        public Vector3dc getVector() {
            if (mode != FieldType.VECTOR) throw new IllegalStateException("Tried to access a vector on a field of type "+mode);
            return this.vector;
        }

        public String getTexture() {
            if (mode != FieldType.TEXTURE) throw new IllegalStateException("Tried to access a texture on a field of type "+mode);
            return this.texture;
        }
    }

    private static class FieldSerializer implements JsonSerializer<Field> {

        public JsonElement serialize(Field src, Type typeOfSrc, JsonSerializationContext context) {
            switch(src.mode) {
                case SCALAR:
                    return new JsonPrimitive(src.getScalar());
                case VECTOR:
                    JsonArray array = new JsonArray();
                    Vector3dc vec = src.getVector();
                    array.add(vec.x()); array.add(vec.y()); array.add(vec.z());

                    return array;
                case TEXTURE:
                    return new JsonPrimitive(src.getTexture());
                default:
                    throw new RuntimeException("Somehow, the field has an unknown value type.");
            }
        }
    }

    private static class FieldDeserializer implements JsonDeserializer<Field> {

        @Override
        public Field deserialize(JsonElement src, Type typeOfSrc, JsonDeserializationContext context)
                throws JsonParseException {
            if (src.isJsonArray()) {
                JsonArray array = src.getAsJsonArray();
                return new Field(
                    new Vector3d(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble())
                );
            }
            if (!src.isJsonPrimitive()) {
                throw new JsonParseException("Field must be an array, a string, or a number.");
            }

            if (src.getAsJsonPrimitive().isNumber()) {
                return new Field(src.getAsDouble());
            } else if (src.getAsJsonPrimitive().isString()) {
                return new Field(src.getAsString());
            } else {
                throw new JsonParseException("Field must be an array, a string, or a number.");
            }
        }
        
    }

    public Field color;
    public Field roughness;
    public Field metallic;
    public Field normal;
    /**
     * Whether this material should be rendered with transparent shading (alpha hashed)
     */
    public boolean transparent;
    /**
     * If enabled, shader will multiply texture with vertex color.
     */
    public boolean useVertexColors;
    
    public String serialize() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Field.class, new FieldSerializer())
                .setPrettyPrinting()
                .create();

        return gson.toJson(this);
    }

    public void serialize(OutputStream out) {
        PrintWriter writer = new PrintWriter(out);
        writer.print(serialize());
        writer.flush();
    }

    public static Material load(String src) throws JsonParseException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Field.class, new FieldDeserializer())
                .create();
        
        return gson.fromJson(src, Material.class);
    }

    public static Material load(InputStream is) throws IOException, JsonParseException {
        return load(IOUtils.toString(is, Charset.forName("UTF-8")));
    }
}
