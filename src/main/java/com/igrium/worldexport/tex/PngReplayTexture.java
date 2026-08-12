package com.igrium.worldexport.tex;

import com.mojang.blaze3d.platform.NativeImage;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A replay texture backed by pre-serialized PNG data.
 */
public class PngReplayTexture implements ReplayTexture {

    private final byte[] pngData;

    public PngReplayTexture(byte[] pngData) {
        this.pngData = pngData;
    }

    @Override
    public NativeImage getNativeImage() {
        try {
            return NativeImage.read(pngData);
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).error("Error parsing replay texture PNG data: ", e);
            return new NativeImage(16, 16, false);
        }
    }

    @Override
    public void writeToFile(Path file) throws IOException {
        // No need to buffer because pngData acts as a buffer.
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(pngData);
        }
    }
}
