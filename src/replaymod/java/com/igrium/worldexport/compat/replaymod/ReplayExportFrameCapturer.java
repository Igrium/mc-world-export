package com.igrium.worldexport.compat.replaymod;

import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplayCompiler;
import com.igrium.worldexport.replay.ReplayIO;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.utils.ByteBufferPool;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

public class ReplayExportFrameCapturer implements FrameCapturer<BitmapFrame> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayExportFrameCapturer.class);

    private int framesDone;

    @Getter
    private final RenderInfo renderInfo;
    private final ReplayExportSettings settings;

    @Getter
    @Nullable
    private ReplayCapture replayCapture;

    public ReplayExportFrameCapturer(@NonNull RenderInfo renderInfo, @NonNull ReplayExportSettings settings) {
        this.renderInfo = renderInfo;
        this.settings = settings;
    }

    public void setup() {
        if (replayCapture != null) {
            throw new IllegalStateException("Capture has already been setup.");
        }

        replayCapture = new ReplayCapture(Minecraft.getInstance().level, settings);
        replayCapture.beginCapture();
    }

    public boolean isSetup() {
        return replayCapture != null;
    }

    @Override
    public boolean isDone() {
        return framesDone >= renderInfo.getTotalFrames();
    }

    @Override
    public Map<Channel, BitmapFrame> process() {
        float tickDelta = renderInfo.updateForNextFrame();
        if (!isSetup()) {
            setup();
        }

        // Bogus frame to satisfy encoder.
        BitmapFrame frame = new BitmapFrame(framesDone++, new Dimension(0, 0), 0, ByteBufferPool.allocate(0));
        return Collections.singletonMap(Channel.BRGA, frame);
    }

    /**
     * Close the resources associated with this capturer.
     */
    @Override
    public void close() throws IOException {
        if (replayCapture != null)
            replayCapture.finish();

        LOGGER.info("Saving replay to {}", settings.getExportPath());
        CompletableFuture<?> save = save();
        // Some parts of the compile pipeline (eg. TextureExtractor's GPU texture readback) complete via
        // fenced tasks that only run when RenderSystem.executePendingTasks() is pumped. Since we're on the
        // render thread, blocking on save.get() would starve that pump and deadlock. Poll instead.
        long deadline = Util.getMillis() + TimeUnit.SECONDS.toMillis(50);
        try {
            // TODO: can we make close run asynchronously in its entirety?
            while (!save.isDone()) {
                RenderSystem.executePendingTasks();
                if (Util.getMillis() > deadline) {
                    throw new IOException("Replay export timed out.");
                }
            }
            save.get();
        } catch (InterruptedException e) {
            LOGGER.error("Replay export interrupted.");
        } catch (ExecutionException e) {
            throw new IOException("Error compiling replay:", e);
        }
    }

    public CompletableFuture<?> save() {
        ReplayCompiler compiler = new ReplayCompiler(replayCapture);

        return compiler.compile().thenCompose(replay -> {
            CompletableFuture<?> result;
            if (settings.isExportZip()) {
                Path exportPath = settings.getExportPath();
                if (!exportPath.toString().endsWith(".zip"))
                    exportPath = exportPath.resolveSibling(exportPath.getFileName() + ".zip");

                result = ReplayIO.saveReplayZip(exportPath, replay, Util.backgroundExecutor());
            } else {
                result = ReplayIO.saveReplayAsync(settings.getExportPath(), replay, Util.backgroundExecutor());
            }
            return result;
        });
    }
}
