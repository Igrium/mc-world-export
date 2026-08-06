package com.igrium.worldexport.compat.replaymod.mixin;

import com.igrium.worldexport.compat.replaymod.CustomPipelines;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.rendering.Pipeline;
import com.replaymod.render.rendering.VideoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VideoRenderer.class, remap = false)
public class MixinVideoRenderer {

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/replaymod/render/rendering/Pipelines;newBlendPipeline(Lcom/replaymod/render/capturer/RenderInfo;)Lcom/replaymod/render/rendering/Pipeline;"))
    public Pipeline<BitmapFrame, BitmapFrame> redirectNewBlendPipeline(RenderInfo renderInfo) {
        return CustomPipelines.newReplayPipeline(renderInfo);
    }
}
