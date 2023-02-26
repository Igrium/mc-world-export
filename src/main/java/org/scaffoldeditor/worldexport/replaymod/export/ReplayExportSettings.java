package org.scaffoldeditor.worldexport.replaymod.export;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.scaffoldeditor.worldexport.vcap.VcapSettings.FluidMode;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.replaymod.replaystudio.lib.guava.base.Optional;
import com.replaymod.replaystudio.replay.ReplayFile;

public final class ReplayExportSettings {
    private int viewDistance = 8;
    private int lowerDepth = 0;

    private FluidMode fluidMode = FluidMode.DYNAMIC;

    @JsonAdapter(FileSerializer.class)
    private File outputFile = new File("output.replay");

    public int getViewDistance() {
        return viewDistance;
    }

    public ReplayExportSettings setViewDistance(int viewDistance) {
        if (viewDistance < 1) {
            throw new IllegalArgumentException("Minimum view distance is 1.");
        }

        this.viewDistance = viewDistance;
        return this;
    }
    
    /**
     * Get the lower depth.
     * @return Lower depth in section coordinates.
     */
    public int getLowerDepth() {
        return lowerDepth;
    }

    /**
     * Set the lower depth.
     * @param lowerDepth Lower depth in section coordinates.
     * @return <code>this</code>
     */
    public ReplayExportSettings setLowerDepth(int lowerDepth) {
        this.lowerDepth = lowerDepth;
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