package com.igrium.replay_debugger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import org.scaffoldeditor.worldexport.mat.ReplayTexture;

public class ImageReplayTexture implements ReplayTexture {

    private final BufferedImage image;

    public ImageReplayTexture(BufferedImage image) {
        this.image = image;
    }

    public ImageReplayTexture(InputStream is) throws IOException {
        this.image = ImageIO.read(is);
    }

    public BufferedImage getImage() {
        return image;
    }

    @Override
    public void save(OutputStream out) throws IOException {
        ImageIO.write(image, "png", out);
    }
    
}
