package org.scaffoldeditor.worldexport.mat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class AnimatedTextureMeta {

    private transient static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private float framerate = 20;

    /**
     * Get the framerate of this animation.
     * @return Frames per second.
     */
    public float getFramerate() {
        return framerate;
    }

    /**
     * Set the framerate of this animation.
     * @param framerate Frames per second.
     */
    public void setFramerate(float framerate) {
        this.framerate = framerate;
    }

    @SerializedName("frame_count")
    private int frameCount;

    public int getFrameCount() {
        return frameCount;
    }

    public void setFrameCount(int frameCount) {
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Frame count must be greater than zero.");
        }
        this.frameCount = frameCount;
    }
    
    public void toJson(OutputStream out) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
        GSON.toJson(this, writer);
        writer.flush();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static AnimatedTextureMeta fromJson(String json) {
        return GSON.fromJson(json, AnimatedTextureMeta.class);
    }

    public static AnimatedTextureMeta fromJson(InputStream in) {
        return GSON.fromJson(new InputStreamReader(in), AnimatedTextureMeta.class);
    }
}
