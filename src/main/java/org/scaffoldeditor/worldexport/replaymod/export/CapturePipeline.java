package org.scaffoldeditor.worldexport.replaymod.export;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.scaffoldeditor.worldexport.replaymod.ReplayFrameCapturer;
import org.scaffoldeditor.worldexport.replaymod.util.ExportInfo;
import org.scaffoldeditor.worldexport.util.FutureUtils;

import com.mojang.logging.LogUtils;

import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class CapturePipeline {

    private final ReplayFrameCapturer frameCapture;
    private final ReplayExporter exporter;

    private volatile boolean abort;

    public CapturePipeline(ReplayFrameCapturer frameCapture, ReplayExporter exporter) {
        this.frameCapture = frameCapture;
        this.exporter = exporter;
    }

    public synchronized void run(ExportInfo.Mutable info) {
        info.setTotalFrames(exporter.getTotalFrames());

        CompletableFuture<?> worldCapture = frameCapture.setup((x, y, index, total) -> {
            info.setChunksDone(index + 1);
            info.setTotalChunks(total);
        });

        // Bullshit workaround to let skins finish downloading before attempting to capture them.
        // TODO: Find a proper solution to this.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            LogUtils.getLogger().error("Error waiting for skin downloads", e);
        }

        int framesDone = 0;
        while (!frameCapture.isDone() && !abort) {
            frameCapture.captureFrame();
            info.setFramesDone(++framesDone);
        }
        
        CompletableFuture<?> finish = finishAsync(worldCapture, info);
        // Wait for world capture to finish.
        while (!finish.isDone() && exporter.drawGui()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        Optional<Throwable> exception = FutureUtils.getException(finish);
        
        if (exception.isPresent()) {
            throw new CrashException(CrashReport.create(exception.get(), "Exporting replay file"));
        }
    }

    private CompletableFuture<?> finishAsync(CompletableFuture<?> worldCapture, ExportInfo.Mutable info) {
        return worldCapture.thenRunAsync(() ->{
            try {
                frameCapture.save(info::setPhase);
                frameCapture.close();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, frameCapture.getWorldCaptureService());
    }

    public ReplayFrameCapturer getFrameCapture() {
        return frameCapture;
    }

    public void cancel() {
        abort = true;
    }
    
}
