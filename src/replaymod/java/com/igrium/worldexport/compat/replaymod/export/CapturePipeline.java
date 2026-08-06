package com.igrium.worldexport.compat.replaymod.export;

import com.igrium.worldexport.compat.replaymod.ExportInfo;
import com.igrium.worldexport.replay.ExportPhase;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplayCompiler;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.igrium.worldexport.replay.ReplayIO;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class CapturePipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/CapturePipeline");

    private final ReplayExporter exporter;

    @Getter
    private @Nullable ReplayCapture replayCapture;

    private volatile boolean abort;

    private ReplayExportSettings getSettings() {
        return exporter.getSettings();
    }

    public CapturePipeline(ReplayExporter exporter) {
        this.exporter = exporter;
    }

    public synchronized void run(ExportInfo.Mutable info) {
        info.setTotalFrames(exporter.getTotalFrames());
        var capture = setup(info);

        try {
            int framesDone = 0;
            while (framesDone < exporter.getTotalFrames() && !abort) {
                // updateForNextFrame will automatically trigger replay capture
                exporter.updateForNextFrame();
                info.setFramesDone(++framesDone);
            }
        } finally {
            capture.finish();
        }

        CompletableFuture<?> finish = saveAsync(info);

        while (!finish.isDone()) {
            exporter.drawGui();
            try {
                //noinspection BusyWait
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!finish.isCompletedExceptionally()) {
            try {
                finish.join();
            } catch (Throwable e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new ReportedException(CrashReport.forThrowable(cause, "Exporting replay file"));
            }
        }

        info.setPhase(ExportPhase.FINISHED);
    }

    private @NonNull ReplayCapture setup(ExportInfo.Mutable info) {
        if (replayCapture != null) {
            throw new IllegalArgumentException("Replay capture already setup");
        }

        replayCapture = new ReplayCapture(Minecraft.getInstance().level, getSettings());
        replayCapture.beginCapture();

        var mesher = replayCapture.getWorldCapture().getMesher();
        // TODO: calculate this based on total possible chunks; queue size will change
        info.setTotalChunks(mesher.getTaskManager().getQueue().size());

        AtomicInteger sectionsDone = new AtomicInteger(0);
        mesher.setOnSectionTessellated(_ -> info.setChunksDone(sectionsDone.incrementAndGet()));
        return replayCapture;
    }

    private CompletableFuture<?> saveAsync(ExportInfo.Mutable info) {
        LOGGER.info("Saving replay as {}", replayCapture);

        ReplayCompiler compiler = new ReplayCompiler(replayCapture);
        compiler.setPhaseListener(info::setPhase);

        return compiler.compile().thenCompose(replay -> {
            CompletableFuture<?> result;
            if (getSettings().isExportZip()) {
                Path exportPath = getSettings().getExportPath();
                result = ReplayIO.saveReplayZip(exportPath, replay, Util.backgroundExecutor());
            } else {
                result = ReplayIO.saveReplayAsync(getSettings().getExportPath(), replay, Util.backgroundExecutor());
            }
            return result;
        });
    }

    public void cancel() {
        abort = true;
    }
}
