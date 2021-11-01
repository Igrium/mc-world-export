package org.scaffoldeditor.worldexport.export;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11C;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

public final class TextureExtractor {
    private TextureExtractor() {};

    /**
     * <p>Obtain an atlas texture in a native image.</p>
     * <b>Note:</b> Will not complete untill at least the next frame. Don't hold the game thread while waiting.
     * @param atlasID Atlas texture identifier.
     * @return A future that completes once the texture has been retrieved from the GPU.
     */
    public static Future<NativeImage> getAtlas(Identifier atlasID) {
        CompletableFuture<NativeImage> future = new CompletableFuture<>();
        RenderSystem.recordRenderCall(() -> {
            SpriteAtlasTexture atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(atlasID);

            atlas.bindTexture();
            // SpriteTextureAtlas doesn't save the texture's width/height post-init, so we need to retrieve it from the GPU.
            int width = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
            int height = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
            NativeImage image = new NativeImage(width, height, false);
            image.loadFromTextureImage(0, false);
            future.complete(image);
        });

        return future;
    }
    
    /**
     * <p>Obtain an atlas texture in a native image.</p>
     * <b>Note:</b> Will not complete untill at least the next frame. Don't hold the game thread while waiting.
     * @return A future that completes once the texture has been retrieved from the GPU.
     */
    public static Future<NativeImage> getAtlas() {
        return getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    }
}
