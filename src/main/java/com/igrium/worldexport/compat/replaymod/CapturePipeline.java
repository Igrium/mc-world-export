package com.igrium.worldexport.compat.replaymod;

import com.igrium.worldexport.replay.ReplayCapture;

public class CapturePipeline {
    private final ReplayCapture replayCapture;

    private volatile boolean abort;

    public CapturePipeline(ReplayCapture replayCapture) {
        this.replayCapture = replayCapture;
    }

    public synchronized void run(ExportInfo.Mutable info) {

    }

    public void cancel() {

    }
}
