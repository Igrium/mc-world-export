package org.scaffoldeditor.worldexport.mat;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

/**
 * A replay texture backed by an AWT image.
 */
public class AWTReplayTexture implements ReplayTexture {
    private final RenderedImage image;

    public AWTReplayTexture(RenderedImage image) {
        this.image = image;
    }

    @Override
    public void save(OutputStream out) throws IOException {
        ImageIO.write(image, "png", out);
    }

    public RenderedImage getImage() {
        return image;
    }
}
