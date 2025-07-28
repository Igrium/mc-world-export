package com.igrium.worldexport.compat.replaymod;

import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplaySettings;
import com.replaymod.render.capturer.RenderInfo;
import lombok.NonNull;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class ReplayExportPipeline {
    private final ReplayExportFrameCapturer frameCapture;
    private final ReplayExporter exporter;

    private volatile boolean abort;

    public void abort() {
        abort = true;
    }

    public ReplayExportPipeline(ReplayExportFrameCapturer frameCapture, ReplayExporter exporter) {
        this.frameCapture = frameCapture;
        this.exporter = exporter;
    }

    public ReplayExportPipeline(ReplaySettings settings, ReplayExporter exporter) {
        this(new ReplayExportFrameCapturer(exporter, settings), exporter);
    }

    public synchronized void run(ExportInfo.Mutable info) {
        info.setTotalFrames(exporter.getTotalFrames());

        frameCapture.setup();

        int framesDone = 0;
        while (!frameCapture.isDone() && !abort) {
            frameCapture.process();
            info.setFramesDone(++framesDone);
        }

        frameCapture.close();

        MutableObject<Throwable> exception = new MutableObject<>(null);

        CompletableFuture<?> finish = frameCapture.save().exceptionally(e -> {
            exception.setValue(e);
            return null;
        });

        while (!finish.isDone() && exporter.drawGui()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Throwable ex = exception.getValue();
        if (ex != null) {
            throw new CrashException(CrashReport.create(ex, "Exporting replay file"));
        }
    }
}
