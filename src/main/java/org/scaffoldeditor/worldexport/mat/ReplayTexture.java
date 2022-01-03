package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.client.texture.NativeImage;

public interface ReplayTexture {
    void save(OutputStream out) throws IOException;

    public static class NativeImageReplayTexture implements ReplayTexture {

        private final NativeImage image;

        public NativeImageReplayTexture(NativeImage image) {
            this.image = image;
        }

        public NativeImage getImage() {
            return image;
        }

        @Override
        public void save(OutputStream out) throws IOException {
            TextureExtractor.writeTextureToFile(getImage(), out);
        }
        
    }
}
