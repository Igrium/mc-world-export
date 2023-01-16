package org.scaffoldeditor.worldexport.mat.sprite;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.scaffoldeditor.worldexport.mat.ReplayTexture;

import net.minecraft.client.texture.NativeImage;

/**
 * Extracts an animated texture's spritesheet into a set of individual textures.
 */
public interface SpritesheetExtractor {

    /**
     * Extract a spritesheet into a list of replay textures.
     * 
     * @param spritesheet  A native image containing the spritesheet to extract.
     * @param spriteHeight The height of each sprite in the sheet.
     * @return A future that completes once the textures have been extracted.
     * @throws IllegalArgumentException If <code>spriteHeight</code> is not a factor
     *                                  of the height of the spritesheet.
     */
    CompletableFuture<List<? extends ReplayTexture>> extract(NativeImage spritesheet, int spriteHeight) throws IllegalArgumentException;

    /**
     * Create a spritesheet extractor using the current default implementation.
     * @return Spritesheet extractor.
     */
    public static SpritesheetExtractor create() {
        return new AWTSpritesheetExtractor();
    }
}
