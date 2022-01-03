package org.scaffoldeditor.worldexport.mixins;

import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Pipeline;
import com.replaymod.render.rendering.VideoRenderer;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.CustomPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VideoRenderer.class, remap = false)
public class VideoRendererMixin {

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = 
            "Lcom/replaymod/render/rendering/Pipelines;newBlendPipeline(Lcom/replaymod/render/capturer/RenderInfo;)Lcom/replaymod/render/rendering/Pipeline;"))
    public Pipeline<BitmapFrame, BitmapFrame> redirectNewBlendPipeline(RenderInfo renderInfo) {
        LogManager.getLogger().info("Constructing replay export pipeline.");
        return CustomPipelines.newReplayPipeline(renderInfo);
    }
    
}
