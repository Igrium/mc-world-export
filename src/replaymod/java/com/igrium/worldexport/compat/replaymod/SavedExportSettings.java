package com.igrium.worldexport.compat.replaymod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.util.JsonAdapters;
import com.replaymod.replaystudio.replay.ReplayFile;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;

public record SavedExportSettings(@Nullable Path outputFile,
                                  @Nullable ChunkSectionBox worldBounds,
                                  @Nullable ChunkSectionBox updateBounds,
                                  @Nullable ChunkSectionBox entityBounds,
                                  @Nullable SectionPos exportCenter) {
    private static final String SETTINGS_FILE = "worldexport-settings.json";

    private static final Gson GSON = JsonAdapters.registerAdapters(new GsonBuilder())
            .setPrettyPrinting().create();

    /**
     * Read saved export settings from a replay file
     *
     * @param file File to read from
     * @return The export settings; <code>null</code> if they haven't been set for this file.
     * @throws IOException If an exception occurs reading the file
     */
    public static @Nullable SavedExportSettings load(ReplayFile file) throws IOException {
        var entry = file.get(SETTINGS_FILE);
        if (!entry.isPresent()) return null;

        try (Reader reader = new InputStreamReader(entry.get())) {
            return GSON.fromJson(reader, SavedExportSettings.class);
        }
    }

    /**
     * Save export settings into a replay file
     *
     * @param file     File to write to
     * @param settings Settings to save
     * @throws IOException If an exception occurs writing the file
     */
    public static void save(ReplayFile file, SavedExportSettings settings) throws IOException {
        try (Writer writer = new OutputStreamWriter(file.write(SETTINGS_FILE))) {
            GSON.toJson(settings, SavedExportSettings.class, writer);
        }
    }
}
