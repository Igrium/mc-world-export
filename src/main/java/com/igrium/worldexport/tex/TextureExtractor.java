package com.igrium.worldexport.tex;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class TextureExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextureExtractor.class);

    /**
     * Pull a texture from the GPU. <b>Must be called on the render thread!</b>
     *
     * @param texture Texture to get.
     * @return A NativeImage containing the contents of the texture.
     * @implNote If the texture is a <code>NativeImageBackedTexture</code>, references the native image directly.
     * Therefore, modifying it could cause unexpected side effects.
     */
    public static NativeImage pullTexture(AbstractTexture texture) {
        if (texture instanceof DynamicTexture) {
            return ((DynamicTexture) texture).getPixels();
        }

        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Texture can only be retrieved on the render thread!");
        }

        texture.bind();
        // AbstractTexture doesn't save the texture's width/height post-init, so we need to retrieve it from the GPU.
        int width = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);

        // TODO: Because NativeImage isn't garbage collected, pulling textures like this can cause a memory leak.
        // We do it somewhat rarely though, so it's probably fine.
        NativeImage image = new NativeImage(width, height, false);
        image.downloadTexture(0, false);

        return image;
    }

    public static CompletableFuture<NativeImage> pullTextureAsync(AbstractTexture texture) {
        return supplyOnRenderThread(() -> pullTexture(texture));
    }

    public static AbstractTexture getTexture(ResourceLocation texID) {
        return Minecraft.getInstance().getTextureManager().getTexture(texID);
    }


    public static NativeImage pullTexture(ResourceLocation texID) {
        LOGGER.info("Fetching texture from GPU: {}", texID);
        AbstractTexture texture = getTexture(texID);
        return pullTexture(texture);
    }

    public static CompletableFuture<NativeImage> pullTextureAsync(ResourceLocation texID) {
        return supplyOnRenderThread(() -> pullTexture(texID));
    }


    public static AbstractTexture getAtlasTexture(ResourceLocation atlasID) {
        // TODO: Do we actually need a separate function for getting atlas textures?
        return Minecraft.getInstance().getModelManager().getAtlas(atlasID);
    }

    public static NativeImage pullAtlasTexture(ResourceLocation atlasID) {
        AbstractTexture atlas = getAtlasTexture(atlasID);
        return pullTexture(atlas);
    }

    public static CompletableFuture<NativeImage> pullAtlasTextureAsync(ResourceLocation atlasID) {
        return supplyOnRenderThread(() -> pullAtlasTexture(atlasID));
    }

    private static <T> CompletableFuture<T> supplyOnRenderThread(Supplier<T> supplier) {
        if (RenderSystem.isOnRenderThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            CompletableFuture<T> future = new CompletableFuture<>();
            RenderSystem.recordRenderCall(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }
}
