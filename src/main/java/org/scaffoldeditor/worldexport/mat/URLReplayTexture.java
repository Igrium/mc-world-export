package org.scaffoldeditor.worldexport.mat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

/**
 * A replay texture that loads the texture at a given URL.
 */
public class URLReplayTexture implements ReplayTexture {

    private final URL url;

    /**
     * Create a replay texture that loads the texture at a given URL.
     * @param url
     */
    public URLReplayTexture(URL url) {
        this.url = url;
    }

    @Override
    public void save(OutputStream out) throws IOException {
        BufferedInputStream stream = new BufferedInputStream(url.openStream());
        stream.transferTo(out);
        stream.close();
    }
    
}
