package org.scaffoldeditor.worldexport.replaymod;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

public class AnimatedCameraEntityRenderer extends EntityRenderer<AnimatedCameraEntity> {

    public AnimatedCameraEntityRenderer(Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(AnimatedCameraEntity var1) {
        return null;
    }
    
}
