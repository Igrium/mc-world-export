package org.scaffoldeditor.worldexport.replaymod;

import java.io.IOException;
import java.util.Map;

import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.capturer.WorldRenderer;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.hooks.EntityRendererHandler;
import com.replaymod.render.processor.DummyProcessor;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.rendering.FrameConsumer;
import com.replaymod.render.rendering.Pipeline;

import net.minecraft.client.MinecraftClient;

public final class CustomPipelines {
    private CustomPipelines() {};
    private static MinecraftClient client = MinecraftClient.getInstance();

    public static Pipeline<BitmapFrame, BitmapFrame> newReplayPipeline(RenderInfo info) {
        WorldRenderer renderer = new EntityRendererHandler(info.getRenderSettings(), info);
        FrameCapturer<BitmapFrame> capturer = new ReplayFrameCapturer(info, client.world);
        FrameConsumer<BitmapFrame> consumer = new FrameConsumer<>() {

            @Override
            public void close() throws IOException {                
            }

            @Override
            public void consume(Map<Channel, BitmapFrame> arg0) {
            }
            
        };
        return new Pipeline<>(renderer, capturer, new DummyProcessor<>(), consumer);
    }
}
