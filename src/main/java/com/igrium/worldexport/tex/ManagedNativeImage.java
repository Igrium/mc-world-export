package com.igrium.worldexport.tex;

import com.google.common.collect.MapMaker;
import lombok.Getter;
import lombok.Setter;
import com.mojang.blaze3d.platform.NativeImage;
import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.Cleaner;

import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentMap;

/**
 * A wrapper around {@link NativeImage} that supports garbage collection
 */
public final class ManagedNativeImage {

    private static final Cleaner CLEANER = Cleaner.create();

    private static final ConcurrentMap<NativeImage, ManagedNativeImage> INSTANCES = new MapMaker().weakKeys().weakValues().makeMap();


    /**
     * <p>The base image.</p>
     * <p><strong>WARNING:</strong> <em>should be gated with {@link #reachabilityFence()},
     * otherwise the underlying image could become invalid without warning!</em></p>
     */
    @Getter
    private final NativeImage image;

    @Getter @Setter
    private boolean externallySynchronized;

    private ManagedNativeImage(NativeImage image) {
        this.image = image;
    }

    /**
     * Wraps a {@link NativeImage}, asserting exclusive ownership.
     * Use {@link #get} if you don't need exclusive ownership.
     *
     * @param image the image to wrap
     * @return the managed wrapper
     * @throws IllegalStateException if this image is already owned by another instance
     */
    public static ManagedNativeImage of(NativeImage image) throws IllegalStateException {
        boolean[] created = {false};

        var instance = INSTANCES.computeIfAbsent(image, i -> {
            var inst = new ManagedNativeImage(i);
            CLEANER.register(inst, i::close);
            created[0] = true;

            return inst;
        });

        if (!created[0]) {
            throw new IllegalStateException("This NativeImage is already owned!");
        }
        return instance;
    }

    public static ManagedNativeImage copyOf(NativeImage image) {
        NativeImage copy = new NativeImage(image.format(), image.getWidth(), image.getHeight(), false);
        copy.copyFrom(image);
        return of(copy);
    }

    /**
     * Returns the managed wrapper for a {@link NativeImage}, creating one if absent.
     *
     * @param image the image to wrap
     * @return the managed wrapper
     */
    public static ManagedNativeImage get(NativeImage image) {
        return INSTANCES.computeIfAbsent(image, i -> {
            var instance = new ManagedNativeImage(i);
            CLEANER.register(instance, i::close);
            return instance;
        });
    }

    /**
     * Returns the existing managed wrapper for a {@link NativeImage}, or <code>null</code>
     * if none has been created.
     *
     * @param image the image to look up
     * @return the managed wrapper, or <code>null</code>
     */
    public static @Nullable ManagedNativeImage tryGet(NativeImage image) {
        return INSTANCES.get(image);
    }

    public <E extends Throwable> void useRawImage(FailableConsumer<NativeImage, E> func) throws E {
        try {
            func.accept(image);
        } finally {
            reachabilityFence();
        }
    }

    public <T, E extends Throwable> void useRawImage(T val, FailableBiConsumer<NativeImage, T, E> func) throws E {
        try {
            func.accept(image, val);
        } finally {
            reachabilityFence();
        }
    }

    public <T, E extends Throwable> T useRawImage(FailableFunction<NativeImage, T, E> func) throws E {
        try {
            return func.apply(image);
        } finally {
            reachabilityFence();
        }
    }


    // Wrap nativeImage function calls so we don't lose references to this while being called.

    public void writeTo(Path path) throws IOException {
        useRawImage(path, NativeImage::writeToFile);
    }

    public void copyFrom(NativeImage other) {
        useRawImage(other, NativeImage::copyFrom);

    }

    public void copyFrom(ManagedNativeImage other) {
        try {
            image.copyFrom(other.getImage());
        } finally {
            reachabilityFence();
            other.reachabilityFence();
        }
    }

    public void copyTo(NativeImage target) {
        useRawImage(target, (img, t) -> t.copyFrom(img));

    }

    public int[] copyPixelsAbgr() {
        return useRawImage(NativeImage::getPixelsABGR);
    }

    public int[] copyPixelsArgb() {
        return useRawImage(NativeImage::getPixels);
    }

    public int getWidth() {
        try {
            return image.getWidth();
        } finally {
            reachabilityFence();
        }
    }

    public int getHeight() {
        try {
            return image.getHeight();
        } finally {
            reachabilityFence();
        }
    }

    public NativeImage.Format getFormat() {
        return useRawImage(NativeImage::format);
    }

    public void writeToFile(Path path) throws IOException {
        try {
            image.writeToFile(path);
        } finally {
            reachabilityFence();
        }
    }

    public void reachabilityFence() {
        Reference.reachabilityFence(this);
    }

}
