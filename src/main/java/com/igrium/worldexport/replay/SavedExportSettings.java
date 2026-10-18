package com.igrium.worldexport.replay;

import com.google.gson.annotations.JsonAdapter;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplayExportSettings.ReplayExportSettingsBuilder;
import com.igrium.worldexport.util.JsonAdapters;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * User-configurable replay export settings that can be saved to disk
 */
@Getter @Setter
public class SavedExportSettings {
    @JsonAdapter(JsonAdapters.PathJsonAdapter.class)
    private @Nullable Path outputFile;

    private @NonNull ChunkSectionBox worldBounds = ChunkSectionBox.ZERO;
    private @NonNull ChunkSectionBox updateBounds = ChunkSectionBox.ZERO;
    private @NonNull ChunkSectionBox entityBounds = ChunkSectionBox.ZERO;

    @JsonAdapter(JsonAdapters.ChunkSectionPosAdapter.class)
    private @NonNull SectionPos exportCenter = SectionPos.of(0, 0, 0);

    private boolean exportWorld = true;
    private boolean exportUpdates = true;
    private boolean exportEntities = true;
    private boolean exportSpritesheets = true;

    public ReplayExportSettingsBuilder compile() {
        if (outputFile == null) {
            throw new IllegalStateException("Output file has not been set");
        }
        return new ReplayExportSettingsBuilder()
                .exportPath(outputFile)
                .worldBounds(worldBounds)
                .updateBounds(updateBounds)
                .entityBounds(entityBounds.toBox())

                .offset(exportCenter.origin())

                .exportWorld(exportWorld)
                .exportUpdates(exportUpdates)
                .exportEntities(exportEntities)
                .exportSpritesheets(exportSpritesheets);
    }
}
