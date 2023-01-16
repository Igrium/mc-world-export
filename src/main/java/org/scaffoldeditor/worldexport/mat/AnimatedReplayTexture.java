package org.scaffoldeditor.worldexport.mat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.scaffoldeditor.worldexport.mat.sprite.SpritesheetExtractor;
import org.scaffoldeditor.worldexport.mixins.SpriteAccessor;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

/**
 * A replay texture containing a sprite animation.
 */
public class AnimatedReplayTexture implements ReplayTexture {

    private final Sprite sprite;
    private NativeImage spritesheet;
    private SpritesheetExtractor extractor = SpritesheetExtractor.create();

    private List<? extends ReplayTexture> spriteTextures;

    private CompletableFuture<?> prepareFuture;

    /**
     * Create an animated replay texture.
     * @param sprite The sprite to use.
     */
    public AnimatedReplayTexture(Sprite sprite) {
        this.sprite = sprite;
        this.spritesheet = ((SpriteAccessor) sprite).getImages()[0];
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
        Map<String, Supplier<ReplayTexture>> map = new HashMap<>();
        int i = 0;
        for (String texId : genSpriteNames()) {
            int index = i;
            map.put(texId, () -> prepareAndGetSprite(index));
            i++;
        }

        return map;
    }

    private void assertPrepared() throws IOException {
        if (spriteTextures == null) {
            try {
                prepare().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("Error extracting sprites.", e);
            }
        }
    }

    private ReplayTexture prepareAndGetSprite(int index) {
        if (spriteTextures == null) {
            try {
                prepare().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        return spriteTextures.get(index);
    }

    @Override
    public CompletableFuture<?> prepare() {
        if (prepareFuture != null) return prepareFuture;
        prepareFuture = extractor.extract(spritesheet, spritesheet.getWidth()).thenAccept(sprites -> {
            spriteTextures = sprites;
        }).thenCompose(v -> ReplayTexture.prepareAll(spriteTextures));
        return prepareFuture;
    }

    /**
     * Until I can find a way to access the data from <code>Animation</code> this is
     * the best way to count the frames in the sprite sheet.
     */
    private int countFrames() {
        // TODO: Properly read animation meta
        return spritesheet.getHeight() / spritesheet.getWidth();
    }

    private List<String> genSpriteNames() {
        int numFrames = countFrames();
        List<String> list = new ArrayList<>(numFrames);
        for (int i = 0; i < numFrames; i++) {
            list.add(getTexName(sprite.getId()) + "_" + i);
        }
        return list;
    }

    public AnimatedTextureMeta getMetadata() {
        AnimatedTextureMeta meta = new AnimatedTextureMeta();
        meta.getFrames().addAll(genSpriteNames());
        return meta;
    }
    
    public Sprite getSprite() {
        return sprite;
    }

    /**
     * Get the filename of a texture, excluding the extension.
     * @param texture Texture identifier.
     * @return Filename, without extension. Compatible with material fields.
     */
    private String getTexName(Identifier texture) {
        String name = texture.toString().replace(':', '/');
        int index = name.lastIndexOf('.');
        if (index > 0) {
            return name.substring(0, index);
        } else {
            return name;
        }
    }
    
}
