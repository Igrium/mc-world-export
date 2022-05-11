package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/**
 * A replay texture that will extract a texture from the GPU at a future time.
 */
public class PromisedReplayTexture implements ReplayTexture {

    private NativeImage image;
    private AbstractTexture texture;

    /**
     * Create a promised replay texture from an AbstractTexture.
     * @param texture Texture to use.
     */
    public PromisedReplayTexture(AbstractTexture texture) {
        this.texture = texture;
    }
    
    /**
     * Create a promised replay texture from a texture identifier.
     * @param texID Identifier to use.
     * @throws IllegalArgumentException If there is no texture loaded with this identifier.
     */
    public PromisedReplayTexture(Identifier texID) throws IllegalArgumentException {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(texID);
        if (texture == null) {
            throw new IllegalArgumentException("Invalid texture identifier: "+texID);
        }
        this.texture = texture;
    }

    /**
     * Extract the texture from the GPU. Must be called on the render thread.
     */
    public void extract() {
        if (isExtracted()) return;
        this.image = TextureExtractor.getTexture(texture);
    }
    
    /**
     * Extract the texture from the GPU on the next frame.
     */
    public CompletableFuture<Void> extractLater() {
        if (RenderSystem.isOnRenderThread()) {
            extract();
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.runAsync(this::extract, MinecraftClient.getInstance());
        }
    }

    @Override
    public void save(OutputStream out) throws IOException {
        extract();
        TextureExtractor.writeTextureToFile(image, out);
    }

    /**
     * Get the AbstractTexture that was or will be extracted from the GPU.
     * @return Texture.
     */
    public AbstractTexture getTexture() {
        return texture;
    }

    /**
     * Determine whether this texture has completed it's GPU extraction.
     * @return Has the texture been extracted?
     */
    public boolean isExtracted() {
        return image != null;
    }
    
}
