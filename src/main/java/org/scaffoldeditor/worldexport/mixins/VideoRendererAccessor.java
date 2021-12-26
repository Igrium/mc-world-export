package org.scaffoldeditor.worldexport.mixins;

import com.replaymod.render.rendering.Pipeline;
import com.replaymod.render.rendering.VideoRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VideoRenderer.class, remap = false)
public interface VideoRendererAccessor {
    @Accessor("renderingPipeline")
    @SuppressWarnings("rawtypes")
    Pipeline getRenderingPipeline();

    @Accessor("renderingPipeline")
    @SuppressWarnings("rawtypes")
    void setRenderingPipeline(Pipeline renderingPipeline);
}
