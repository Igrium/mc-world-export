package org.scaffoldeditor.worldexport.mat;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import net.minecraft.util.math.Vec3d;

/**
 * Represents a simple material that can be imported into Blender.
 */
public class Material {
    public static class Field {
        public enum FieldType { SCALAR, VECTOR, TEXTURE }

        private double scalar;
        private Vec3d vector;
        private String texture;
        public final FieldType mode;

        public Field(double scalar) {
            this.mode = FieldType.SCALAR;
            this.scalar = scalar;
        }

        public Field(Vec3d vector) {
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

        public Vec3d getVector() {
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
                    Vec3d vec = src.getVector();
                    array.add(vec.x); array.add(vec.y); array.add(vec.z);

                    return array;
                case TEXTURE:
                    return new JsonPrimitive(src.getTexture());
                default:
                    throw new RuntimeException("Somehow, the field has an unknown value type.");
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
}
