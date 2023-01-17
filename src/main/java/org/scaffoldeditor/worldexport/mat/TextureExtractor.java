package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11C;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

public final class TextureExtractor {
    private TextureExtractor() {};

    /**
     * <p>Retrieve a texture from the GPU</p>
     * <b>Note:</b> Must be called on the render thread.
     * @param texture Reference to the texture to retrieve.
     * @return CPU-bound texture.
     */
    public static NativeImage getTexture(AbstractTexture texture) {
        if (texture instanceof NativeImageBackedTexture) {
            return ((NativeImageBackedTexture) texture).getImage();
        }

        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Texture can only be retrieved on the render thread!");
        }

        texture.bindTexture();
        // AbstractTexture doesn't save the texture's width/height post-init, so we need to retrieve it from the GPU.
        int width = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);

        NativeImage image = new NativeImage(width, height, false);
        image.loadFromTextureImage(0, false);

        return image;
    }

    /**
     * <p>Obtain a texture in a native image.</p>
     * <b>Note:</b> Must be called on the render thread.
     * @param textureID Texture identifier. Use {@link #getAtlas} for atlas textures.
     * @return The texture.
     */
    public static NativeImage getTexture(Identifier textureID) {
        LogManager.getLogger().info("Fetching texture from GPU: "+textureID);
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureID);

        return getTexture(texture);
    }
    
    public static SpriteAtlasTexture getAtlasTexture(Identifier id) {
        return MinecraftClient.getInstance().getBakedModelManager().getAtlas(id);
    }
    
    public static SpriteAtlasTexture getAtlasTexture() {
        return getAtlasTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
    }

    /**
     * <p>Obtain an atlas texture in a native image.</p>
     * <b>Note:</b> Must be called on the render thread.
     * @param atlasID Atlas texture identifier.
     * @return The atlas texture.
     */
    public static NativeImage getAtlas(Identifier atlasID) {
        SpriteAtlasTexture atlas = getAtlasTexture(atlasID);
        
        return getTexture(atlas);
    }
    
    
    /**
     * <p>Obtain the block atlas texture in a native image.</p>
     * <b>Note:</b> Must be called on the render thread.
     * @return The atlas texture.
     */
    public static NativeImage getAtlas() {
        return getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
    }

    /**
     * Save a <code>NativeImage</code> into a PNG file.
     * @param texture Image to save.
     * @param output Output stream to write to.
     * @throws IOException If an I/O exception occurs.
     */
    public static void writeTextureToFile(NativeImage texture, OutputStream output) throws IOException {
        // For some reason, NativeImage can only write to a file or a byte array; not an output stream.
        byte[] data = texture.getBytes();
        output.write(data);
    }
}
