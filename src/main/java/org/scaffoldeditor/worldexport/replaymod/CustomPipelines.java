package org.scaffoldeditor.worldexport.replaymod;

import java.io.IOException;
import java.util.Map;

import org.scaffoldeditor.worldexport.replaymod.export.ReplayExportSettings;

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

@Deprecated
public final class CustomPipelines {
    private CustomPipelines() {};

    /**
     * A horrible, temporary, global storage if the replay export settings to allow
     * it to be passed through mixins.
     */
    public static ReplayExportSettings replaySettings = new ReplayExportSettings();

    public static Pipeline<BitmapFrame, BitmapFrame> newReplayPipeline(RenderInfo info) {
        WorldRenderer renderer = new EntityRendererHandler(info.getRenderSettings(), info);
        FrameCapturer<BitmapFrame> capturer = new ReplayFrameCapturer(info, info.getRenderSettings().getFramesPerSecond(), replaySettings);
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
