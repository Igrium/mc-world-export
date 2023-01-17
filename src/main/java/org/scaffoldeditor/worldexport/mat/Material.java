package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import org.joml.Vector3dc;

/**
 * Represents a simple material that can be imported into Blender.
 */
public class Material {
    public enum BlendMode {
        MULTIPLY("multiply"),
        MIX("mix"),
        DARKEN("darken"),
        BURN("burn"),
        LIGHTEN("lighten"),
        SCREEN("screen"),
        DODGE("dodge"),
        ADD("add"),
        OVERLAY("overlay"),
        SOFT_LIGHT("soft_light"),
        LINEAR_LIGHT("linear_light"),
        DIFFERENCE("difference"),
        SUBTRACT("subtract"),
        DIVIDE("divide"),
        HUE("hue"),
        SATURATION("saturation"),
        COLOR("color"),
        VALUE("value");
        

        private final String name;

        private BlendMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public enum AlphaMode {
        OPAQUE, CLIP, HASHED, BLEND
    }

    public static final class DEFAULT_OVERRIDES {
        private DEFAULT_OVERRIDES() {};

        /**
         * The vertex color or the vcap block color of a given pixel.
         */
        public static final String VERTEX_COLOR = "$VERTEX_COLOR";
    }

    private Field color;
    /**
     * If present, will be multiplied with color
     */
    private Field color2;
    private BlendMode color2BlendMode;

    private Field roughness;
    private Field metallic;
    private Field normal;
    private Field emission;

    private float emissionStrength;

    private Set<String> tags = new HashSet<>();
    private Map<String, String> overrides = new HashMap<>();

    public Set<String> tags() {
        return tags;
    }

    public Map<String, String> overrides() {
        return overrides;
    }

    /**
     * Add a material override to this material. Material overrides allow attributes to be controlled per-object.
     * @param field The field to add the override to.
     * @param value The The override name.
     * @return <code>this</code>
     */
    public Material addOverride(String field, String value) {
        overrides.put(field, value);
        return this;
    }
    
    private AlphaMode blendMode;

    public Field getColor() {
        return color;
    }

    public Material setColor(Field color) {
        this.color = color;
        return this;
    }

    public Material setColor(String texture) {
        return setColor(new Field(texture));
    }

    public Material setColor(Vector3dc vector) {
        return setColor(new Field(vector));
    }

    public Field getColor2() {
        return color2;
    }

    public Material setColor2(Field color2) {
        this.color2 = color2;
        return this;
    }

    public Material setColor2(String texture) {
        return setColor2(new Field(texture));
    }

    public Material setColor2(Vector3dc vector) {
        return setColor2(new Field(vector));
    }

    public BlendMode getColor2BlendMode() {
        return color2BlendMode == null ? BlendMode.MULTIPLY : color2BlendMode;
    }

    public Material setColor2BlendMode(BlendMode color2BlendMode) {
        this.color2BlendMode = color2BlendMode;
        return this;
    }

    public Field getRoughness() {
        return roughness;
    }

    public Material setRoughness(Field roughness) {
        this.roughness = roughness;
        return this;
    }

    public Material setRoughness(String texture) {
        return setRoughness(new Field(texture));
    }

    public Material setRoughness(float scalar) {
        return setRoughness(new Field(scalar));
    }

    public Field getMetallic() {
        return metallic;
    }

    public Material setMetallic(Field metallic) {
        this.metallic = metallic;
        return this;
    }

    public Field getNormal() {
        return normal;
    }

    public Material setNormal(Field normal) {
        this.normal = normal;
        return this;
    }

    public Field getEmission() {
        return emission;
    }

    public void setEmission(Field emissive) {
        this.emission = emissive;
    }

    public void setEmission(float scalar) {
        this.emission = new Field(scalar);
    }

    public void setEmission(String texture) {
        this.emission = new Field(texture);
    }

    public void setEmission(Vector3dc vector) {
        this.emission = new Field(vector);
    }

    public float getEmissionStrength() {
        return emissionStrength;
    }

    public void setEmissionStrength(float emissionStrength) {
        this.emissionStrength = emissionStrength;
    }

    @Deprecated
    public boolean getTransparent() {
        return blendMode != AlphaMode.OPAQUE;
    }

    public Material setTransparent(boolean transparent) {
        this.blendMode = transparent ? AlphaMode.HASHED : AlphaMode.OPAQUE;
        return this;
    }
    
    public AlphaMode getBlendMode() {
        return blendMode;
    }

    public Material setBlendMode(AlphaMode blendMode) {
        this.blendMode = blendMode;
        return this;
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    
    public String serialize() {
        return GSON.toJson(this);
    }

    public void serialize(OutputStream out) {
        PrintWriter writer = new PrintWriter(out);
        writer.print(serialize());
        writer.flush();
    }

    public static Material load(String src) throws JsonParseException {
        return GSON.fromJson(src, Material.class);
    }

    public static Material load(InputStream is) throws IOException, JsonParseException {
        return GSON.fromJson(new InputStreamReader(is), Material.class);
    }
}
