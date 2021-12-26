package org.scaffoldeditor.worldexport.export;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11C;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

public final class TextureExtractor {
    private TextureExtractor() {};

    /**
     * <p>Obtain an atlas texture in a native image.</p>
     * <b>Note:</b> Must be called on the render thread.
     * @param atlasID Atlas texture identifier.
     * @return The atlas texture.
     */
    public static NativeImage getAtlas(Identifier atlasID) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Texture atlas can only be retrieved on the render thread!");
        }

        LogManager.getLogger().info("Fetching atlas texture...");
        SpriteAtlasTexture atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(atlasID);
        
        atlas.bindTexture();
        // SpriteTextureAtlas doesn't save the texture's width/height post-init, so we need to retrieve it from the GPU.
        int width = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
        NativeImage image = new NativeImage(width, height, false);
        image.loadFromTextureImage(0, false);

        return image;
    }
    
    
    /**
     * <p>Obtain an atlas texture in a native image.</p>
     * <b>Note:</b> Must be called on the render thread.
     * @return The atlas texture.
     */
    public static NativeImage getAtlas() {
        return getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    }
}
