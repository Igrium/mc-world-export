package org.scaffoldeditor.worldexport.replaymod.util;

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
    public static class Mutable implements ExportInfo {
        private volatile int framesDone;
        private int totalFrames;

        private volatile int chunksDone;
        private int totalChunks;

        private volatile String phase = ExportPhase.INIT;

        public int getFramesDone() {
            return framesDone;
        }

        public void setFramesDone(int framesDone) {
            this.framesDone = framesDone;
        }

        public int getTotalFrames() {
            return totalFrames;
        }

        public void setTotalFrames(int totalFrames) {
            this.totalFrames = totalFrames;
        }

        public int getChunksDone() {
            return chunksDone;
        }

        public void setChunksDone(int sectionsDone) {
            this.chunksDone = sectionsDone;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public void setTotalChunks(int totalSections) {
            this.totalChunks = totalSections;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }
    }
}
