package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.scaffoldeditor.worldexport.mat.sprite.SpriteAnimMetaProvider;
import org.scaffoldeditor.worldexport.mixins.SpriteAccessor;

import com.google.common.collect.ImmutableMap;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;

/**
 * A replay texture containing a sprite animation.
 */
public class AnimatedReplayTexture implements ReplayTexture {

    private final Sprite sprite;
    private final NativeImage spriteSheet;

    private final ReplayTexture spriteSheetTexture;
    private final AnimationResourceMetadata animData;


    private CompletableFuture<?> prepareFuture;

    /**
     * Create an animated replay texture.
     * @param sprite The sprite to use.
     */
    public AnimatedReplayTexture(Sprite sprite) {
        this.sprite = sprite;
        this.spriteSheet = ((SpriteAccessor) sprite.getContents()).getImages()[0];
        spriteSheetTexture = new NativeImageReplayTexture(spriteSheet);
        animData = ((SpriteAnimMetaProvider) sprite.getContents()).getAnimData();
    }

    @Override
    public void save(OutputStream out) throws IOException {
        assertPrepared();
        getMetadata().toJson(out);
    }

    @Override
    public String getFileExtension() {
        return ".json";
    }

    @Override
    public Map<String, Supplier<ReplayTexture>> getTextureDependencies() {
        return ImmutableMap.of(MaterialUtils.getTexName(sprite.getContents().getId()) + "_spritesheet", () -> spriteSheetTexture);
    }

    private void assertPrepared() throws IOException {
        if (prepareFuture == null || !prepareFuture.isDone()) {
            try {
                prepare().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("Error extracting sprites.", e);
            }
        }
    }

    @Override
    public CompletableFuture<?> prepare() {
        if (prepareFuture != null) return prepareFuture;
        prepareFuture = spriteSheetTexture.prepare();
        return prepareFuture;
    }

    private int countFrames() {
        // TODO: Properly read animation meta
        return spriteSheet.getHeight() / spriteSheet.getWidth();
    }

    public AnimatedTextureMeta getMetadata() {
        AnimatedTextureMeta meta = new AnimatedTextureMeta();
        meta.setFrameCount(countFrames());
        meta.setFramerate(20f / animData.getDefaultFrameTime());
        return meta;
    }
}