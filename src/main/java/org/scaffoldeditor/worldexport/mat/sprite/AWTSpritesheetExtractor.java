package org.scaffoldeditor.worldexport.mat.sprite;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import org.scaffoldeditor.worldexport.mat.AWTReplayTexture;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;

import net.minecraft.client.texture.NativeImage;

/**
 * A spritesheet extractor backed by the AWT graphics library (barf)
 */
public class AWTSpritesheetExtractor implements SpritesheetExtractor {

    @Override
    public CompletableFuture<List<? extends ReplayTexture>> extract(NativeImage spritesheet, int spriteHeight)
            throws IllegalArgumentException {
        if (spriteHeight <= 0) {
            throw new IllegalArgumentException("Sprite height must be greater than 0.");
        }
        if (spritesheet.getHeight() % spriteHeight != 0) {
            throw new IllegalArgumentException("The spright height must be a factor of the height of the spritesheet.");
        }
        return CompletableFuture.completedFuture(extractSync(spritesheet, spriteHeight));
    }

    public List<? extends ReplayTexture> extractSync(NativeImage spritesheet, int spriteHeight) {
        if (spriteHeight <= 0) {
            throw new IllegalArgumentException("Sprite height must be greater than 0.");
        }
        if (spritesheet.getHeight() % spriteHeight != 0) {
            throw new IllegalArgumentException("The spright height must be a factor of the height of the spritesheet.");
        }
        
        int width = spritesheet.getWidth();
        int numSprites = spritesheet.getHeight() / spriteHeight;

        List<BufferedImage> images = new ArrayList<>(numSprites);
        BufferedImage awtSpritesheet = nativeToBuffered(spritesheet);

        for (int i = 0; i < numSprites; i++) {
            BufferedImage image = new BufferedImage(width, spriteHeight, awtSpritesheet.getType());
            Graphics2D img_creator = image.createGraphics();

            int src_y1 = spriteHeight * i;

            img_creator.drawImage(awtSpritesheet, null, 0, -src_y1);
            images.add(image);
        }

        return images.stream().map(AWTReplayTexture::new).toList();
    }

    private BufferedImage nativeToBuffered(NativeImage image) {
        try {
            return ImageIO.read(new ByteArrayInputStream(image.getBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
}
