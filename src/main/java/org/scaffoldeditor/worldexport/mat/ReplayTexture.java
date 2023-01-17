package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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

    /**
     * Prepare any asynchronous resources this replay texture needs to save. If this
     * is not called, <code>save</code> will block until the resources are prepared.
     * 
     * @return A future that completes once all resources have been prepared.
     */
    default CompletableFuture<?> prepare() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Get all the texture "dependencies" that this texture has. Most notably,
     * individual frames for animated textures.
     * 
     * @return A map of texture IDs and a supplier to get the textures should one be
     *         missing.
     */
    default Map<String, Supplier<ReplayTexture>> getTextureDependencies() {
        return Collections.emptyMap();
    }

    /**
     * Get the file extension this texture should save with.
     * @return The file extension, including the '.'
     * @default <code>.png</code>
     */
    default String getFileExtension() {
        return ".png";
    }

    /**
     * Prepare all of the replay textures in a collection.
     * @param textures Textures to prepare.
     * @return A future that completes once all the textures have been prepared.
     * @see ReplayTexture#prepare()
     */
    public static CompletableFuture<Void> prepareAll(Collection<? extends ReplayTexture> textures) {
        CompletableFuture<?>[] futures = textures.stream().map(ReplayTexture::prepare).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

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
