package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.client.texture.NativeImage;

/**
 * A texture contained within a replay file.
 */
public interface ReplayTexture {

    /**
     * Save this texture out to a file, in PNG format. Note: some implementations
     * may require this to be run on the render thread.
     * 
     * @param out Stream to save out to.
     * @throws IOException If something goes wrong while saving the texture.
     */
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
