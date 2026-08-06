package com.igrium.worldexport.compat.replaymod;

import com.igrium.worldexport.replay.ExportPhase;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Contains various information about the state of a replay export.
 */
public interface ExportInfo {
    int getFramesDone();
    int getTotalFrames();

    int getChunksDone();
    int getTotalChunks();

    String getPhase();

    /**
     * A thread-safe, mutable implementation of ExportInfo.
     */
    @Setter @Getter
    class Mutable implements ExportInfo {
        private volatile int framesDone;
        private int totalFrames;

        private volatile int chunksDone;
        private int totalChunks;

        @NonNull
        private volatile String phase = ExportPhase.INIT;
    }
}
