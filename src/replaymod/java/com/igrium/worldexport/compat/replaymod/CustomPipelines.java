package com.igrium.worldexport.compat.replaymod;

import com.igrium.worldexport.replay.ReplayExportSettings;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.capturer.WorldRenderer;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.hooks.EntityRendererHandler;
import com.replaymod.render.processor.DummyProcessor;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.rendering.FrameConsumer;
import com.replaymod.render.rendering.Pipeline;

import java.io.IOException;
import java.util.Map;

@Deprecated
public class CustomPipelines {
    private CustomPipelines() {
    }

    /**
     * A horrible, temporary, global storage of the replay export settings to allow
     * it to be passed through mixins.
     */
    public static ReplayExportSettings replayExportSettings;

    public static Pipeline<BitmapFrame, BitmapFrame> newReplayPipeline(RenderInfo info) {
        WorldRenderer renderer = new EntityRendererHandler(info.getRenderSettings(), info);
        FrameCapturer<BitmapFrame> capturer = new ReplayExportFrameCapturer(info, replayExportSettings);
        FrameConsumer<BitmapFrame> consumer = new FrameConsumer<>() {

            @Override
            public void close() throws IOException {
            }

            @Override
            public void consume(Map<Channel, BitmapFrame> arg0) {
            }

            @Override
            public boolean isParallelCapable() {
                return false;
            }

        };
        BlendState.setState(null); // Stop native Blender exporter from running.
        return new Pipeline<>(renderer, capturer, new DummyProcessor<>(), consumer);
    }
}