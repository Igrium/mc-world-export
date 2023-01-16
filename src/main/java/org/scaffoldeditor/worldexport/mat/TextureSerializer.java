package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableSet;

/**
 * A small utility class that saves textures into a Zip file.
 */
public class TextureSerializer {
    public static interface OutputStreamSupplier {
        /**
         * Generate an output stream.
         * @param filepath Filepath relative to the texture root, including the extension.
         * @return The output stream. This stream will be closed automatically.
         * @throws IOException If an IO exception occurs.
         */
        OutputStream get(String filepath) throws IOException;
    }


    private final OutputStreamSupplier outputStreamSupplier;

    /**
     * Create a texture serializer.
     * @param outputStreamSupplier The output stream supplier to use.
     */
    public TextureSerializer(OutputStreamSupplier outputStreamSupplier) {
        this.outputStreamSupplier = outputStreamSupplier;
    }

    public Logger logger;
    
    /**
     * Save all textures to file synchronously. May block until the next frame is
     * rendered if not called on the render thread.
     * 
     * @param textures A map of the textures to save and their IDs.
     * @throws IOException If an IO exception occurs while writing the textures.
     */
    public void save(Map<String, ? extends ReplayTexture> textures) throws IOException {
        Map<String, ReplayTexture> texMap = new HashMap<>(textures);

        Set<ReplayTexture> newDependencies = new HashSet<>(texMap.values());
        // Recursively resolve all dependencies.
        while (!newDependencies.isEmpty()) {
            for (ReplayTexture texture : ImmutableSet.copyOf(newDependencies)) {
                synchronized(texture) {
                    texture.getTextureDependencies().forEach((texId, supplier) -> {
                        if (texMap.containsKey(texId)) return;
                        ReplayTexture tex = supplier.get();
                        if (tex == null) return;
                        texMap.put(texId, tex);
                        newDependencies.add(tex);
                    });
                    newDependencies.remove(texture);
                }
            }
        }

        if (logger != null) logger.info("Extracting textures.");
        CompletableFuture<?>[] futures = texMap.values().stream().map(ReplayTexture::prepare)
                .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new IOException("Exception preparing textures: ", e);
        } catch (TimeoutException e) {
            throw new IOException("Texture preparation timed out.");
        }

        for (String texId : texMap.keySet()) {
            ReplayTexture texture = texMap.get(texId);
            OutputStream out = outputStreamSupplier.get(texId + texture.getFileExtension());
            texture.save(out);
            out.close();
        }
    }
}
