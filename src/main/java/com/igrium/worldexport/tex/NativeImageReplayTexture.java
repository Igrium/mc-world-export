package com.igrium.worldexport.tex;

import net.minecraft.client.texture.NativeImage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A replay texture backed by a NativeImage
 */
public class NativeImageReplayTexture implements ReplayTexture {

    private final NativeImage nativeImage;

    public NativeImageReplayTexture(NativeImage nativeImage) {
        this.nativeImage = nativeImage;
    }

    @Override
    public NativeImage getNativeImage() {
        return nativeImage;
    }

    @Override
    public void writeToFile(Path file) throws IOException {
        nativeImage.writeTo(file);
    }
}
