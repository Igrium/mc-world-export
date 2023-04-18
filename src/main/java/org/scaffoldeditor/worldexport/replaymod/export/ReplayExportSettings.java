package org.scaffoldeditor.worldexport.replaymod.export;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.replaymod.replaystudio.lib.guava.base.Optional;
import com.replaymod.replaystudio.replay.ReplayFile;

import net.minecraft.util.math.BlockBox;

public final class ReplayExportSettings {

    @JsonAdapter(BoxSerializer.class)
    private BlockBox bounds;

    private FluidMode fluidMode = FluidMode.DYNAMIC;

    @JsonAdapter(FileSerializer.class)
    private File outputFile = new File("output.replay");

    public BlockBox getBounds() {
        return bounds;
    }

    public ReplayExportSettings setBounds(BlockBox bounds) {
        this.bounds = bounds;
        return this;
    }

    public FluidMode getFluidMode() {
        return fluidMode;
    }

    public ReplayExportSettings setFluidMode(FluidMode fluidMode) {
        this.fluidMode = fluidMode;
        return this;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public ReplayExportSettings setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    private static class FileSerializer extends TypeAdapter<File> {

        @Override
        public File read(JsonReader reader) throws IOException {
            return new File(reader.nextString());
        }

        @Override
        public void write(JsonWriter writer, File file) throws IOException {
            writer.value(file.toString());
        }

    }

    private static class BoxSerializer implements JsonSerializer<BlockBox>, JsonDeserializer<BlockBox> {

        @Override
        public BlockBox deserialize(JsonElement element, Type type, JsonDeserializationContext context) throws JsonParseException {
            if (!element.isJsonArray()) {
                throw new JsonParseException("Bounding box must be a json array.");
            }
            JsonArray array = element.getAsJsonArray();
            
            return new BlockBox(array.get(0).getAsInt(),
                    array.get(1).getAsInt(),
                    array.get(2).getAsInt(),
                    array.get(3).getAsInt(),
                    array.get(4).getAsInt(),
                    array.get(5).getAsInt());
        }

        @Override
        public JsonElement serialize(BlockBox box, Type type, JsonSerializationContext context) {
            JsonArray array = new JsonArray(6);
            
            array.add(box.getMinX());
            array.add(box.getMinY());
            array.add(box.getMinZ());
            array.add(box.getMaxX());
            array.add(box.getMaxY());
            array.add(box.getMaxZ());

            return array;
        }
        
    }

    private static final Gson gson = new Gson();
    private static final String SETTINGS_FILE = "worldexport-exportsettings.json";

    @Nullable
    public static ReplayExportSettings readFromFile(ReplayFile file) throws IOException {
        Optional<InputStream> in = file.get(SETTINGS_FILE);
        if (in.isPresent()) {
            try (InputStream is = in.get()) {
                return gson.fromJson(new InputStreamReader(in.get()), ReplayExportSettings.class);
            }
        } else {
            return null;
        }
    }

    public static void writeToFile(ReplayFile file, ReplayExportSettings settings) throws IOException {
        try (OutputStream out = file.write(SETTINGS_FILE)) {
            out.write(gson.toJson(settings).getBytes(StandardCharsets.UTF_8));
        }
    }
}