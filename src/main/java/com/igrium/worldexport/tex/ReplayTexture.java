package com.igrium.worldexport.tex;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A texture that can be saved as part of a replay.
 */
public interface ReplayTexture {
    /**
     * Get this texture as a NativeImage.
     */
    NativeImage getNativeImage();

    /**
     * Save this texture to a PNG file.
     * @param file File to save to.
     * @throws IOException If an IO exception occurs saving the file.
     */
    void writeToFile(Path file) throws IOException;
}
