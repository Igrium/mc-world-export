package com.igrium.worldexport.compat.replaymod.util;

import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplaySettings;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public class ReplayFrameCapturer implements FrameCapturer<BitmapFrame> {

    @Getter
    private final RenderInfo renderInfo;
    private final ReplaySettings settings;

    @Getter
    @Nullable
    private ReplayCapture exporter;

    public ReplayFrameCapturer(RenderInfo renderInfo, ReplaySettings settings) {
        this.renderInfo = renderInfo;
        this.settings = settings;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public Map<Channel, BitmapFrame> process() {
        return Map.of();
    }

    @Override
    public void close() throws IOException {

    }
}
