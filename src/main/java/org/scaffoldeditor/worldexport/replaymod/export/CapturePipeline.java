package org.scaffoldeditor.worldexport.replaymod.export;

import org.scaffoldeditor.worldexport.replaymod.ReplayFrameCapturer;

import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class CapturePipeline implements Runnable {

    private final ReplayFrameCapturer frameCapture;

    private volatile boolean abort;

    public CapturePipeline(ReplayFrameCapturer frameCapture) {
        this.frameCapture = frameCapture;
    }

    @Override
    public synchronized void run() {
        while (!frameCapture.isDone() && !abort) {
            frameCapture.process();
        }
        try {
            frameCapture.close();
        } catch (Throwable e) {
            throw new CrashException(CrashReport.create(e, "Saving replay file."));
        }
    }

    public ReplayFrameCapturer getFrameCapture() {
        return frameCapture;
    }

    public void cancel() {
        abort = true;
    }
    
}
