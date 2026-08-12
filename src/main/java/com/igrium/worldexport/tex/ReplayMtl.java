package com.igrium.worldexport.tex;

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
public record ReplayMtl(@NonNull Mtl mtl, @NonNull Map<String, Property<?>> properties) {
    public ReplayMtl(Mtl mtl) {
        this(mtl, new HashMap<>());
    }

    public String getName() {
        return mtl.getName();
    }

    @JsonAdapter(PropertyJsonAdapter.class)
    public sealed interface Property<T> {
        static Property<Number> of(Number number) {
            return new NumberProperty(number);
        }

        static Property<String> of(String string) {
            return new StringProperty(string);
        }

        static Property<Boolean> of(boolean bool) {
            return new BooleanProperty(bool);
        }

        T getValue();

        default boolean isNumber() {
            return false;
        }

        default boolean isString() {
            return false;
        }

        default boolean isBoolean() {
            return false;
        }

        default Number getNumber() throws ClassCastException {
            throw new ClassCastException("This property is not a number.");
        }

        default String getString() throws ClassCastException {
            throw new ClassCastException("This property is not a string.");
        }
        default boolean getBool() throws ClassCastException {
            throw new ClassCastException("This property is not a boolean.");
        }
    }

    @JsonAdapter(PropertyJsonAdapter.class)
    @EqualsAndHashCode
    private static final class NumberProperty implements Property<Number> {
        private final Number value;

        public NumberProperty(Number value) {
            this.value = value;
        }

        @Override
        public Number getValue() {
            return value;
        }

        @Override
        public boolean isNumber() {
            return true;
        }

        @Override
        public Number getNumber() throws ClassCastException {
            return value;
        }
    }

    @JsonAdapter(PropertyJsonAdapter.class)
    @EqualsAndHashCode
    private static final class StringProperty implements Property<String> {
        private final String value;

        public StringProperty(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String getString() throws ClassCastException {
            return value;
        }
    }

    @JsonAdapter(PropertyJsonAdapter.class)
    @EqualsAndHashCode
    private static final class BooleanProperty implements Property<Boolean> {
        private final boolean value;

        private BooleanProperty(boolean value) {
            this.value = value;
        }

        @Override
        public Boolean getValue() {
            return value;
        }

        @Override
        public boolean isBoolean() {
            return true;
        }

        @Override
        public boolean getBool() throws ClassCastException {
            return value;
        }
    }

    private static class PropertyJsonAdapter extends TypeAdapter<Property<?>> {

        @Override
        public void write(JsonWriter out, Property<?> value) throws IOException {
            if (value.isNumber()) {
                out.value(value.getNumber());
            } else if (value.isString()) {
                out.value(value.getString());
            } else if (value.isBoolean()) {
                out.value(value.getBool());
            } else {
                throw new IllegalStateException("Unknown property type");
            }
        }

        @Override
        public Property<?> read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            return switch(token) {
                case NUMBER -> new NumberProperty(in.nextDouble());
                case STRING -> new StringProperty(in.nextString());
                case BOOLEAN -> new BooleanProperty(in.nextBoolean());
                default -> throw new JsonParseException("Invalid token type for mtl property: " + token.name());
            };
        }
    }
}
