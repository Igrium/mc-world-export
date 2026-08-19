package com.igrium.worldexport.tex;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class TextureExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/TextureExtractor");

    public static CompletableFuture<ManagedNativeImage> pullTextureAsync(AbstractTexture texture) {
        if (!RenderSystem.isOnRenderThread()) {
            return CompletableFuture.supplyAsync(() -> pullTextureAsync(texture), Minecraft.getInstance()).thenCompose(f -> f);
        }
        if (texture instanceof DynamicTexture dynamicTexture) {
            return CompletableFuture.completedFuture(ManagedNativeImage.copyOf(dynamicTexture.getPixels()));
        }

        return CompletableFuture.supplyAsync(() -> downloadTexture(texture.getTexture()),
                        TextureExtractor::onRenderThread)
                .thenCompose(f -> f);

    }

    private static AbstractTexture getTexture(Identifier texID) {
        return Minecraft.getInstance().getTextureManager().getTexture(texID);
    }


    public static ManagedNativeImage pullTexture(Identifier texID) {
        LOGGER.info("Fetching texture: {}", texID);
        AbstractTexture texture = getTexture(texID);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return ManagedNativeImage.copyOf(dynamicTexture.getPixels());
        } else {
            try {
                return ManagedNativeImage.of(loadTextureFromResources(texID));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static CompletableFuture<ManagedNativeImage> pullTextureAsync(Identifier texID) {
        LOGGER.info("Fetching texture: {}", texID);
        if (!RenderSystem.isOnRenderThread()) {
            return CompletableFuture.supplyAsync(() -> pullTextureAsync(texID), Minecraft.getInstance())
                    .thenCompose(f -> f);
        }
        AbstractTexture texture = getTexture(texID);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return CompletableFuture.completedFuture(ManagedNativeImage.copyOf(dynamicTexture.getPixels()));
        }
        return loadTextureFromResourcesAsync(texID).thenApply(ManagedNativeImage::of);
    }

    @SuppressWarnings("resource") // Not closed: ownership of the image transfers to the caller.
    private static NativeImage loadTextureFromResources(Identifier texID) throws IOException {
        return TextureContents.load(Minecraft.getInstance().getResourceManager(), texID).image();
    }

    private static CompletableFuture<NativeImage> loadTextureFromResourcesAsync(Identifier texID) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadTextureFromResources(texID);
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        }, Util.ioPool());
    }

    public static AbstractTexture getAtlasTexture(Identifier atlasID) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(atlasID);
    }


    public static CompletableFuture<ManagedNativeImage> pullAtlasTextureAsync(Identifier atlasID) {
        AbstractTexture atlas = getAtlasTexture(atlasID);
        return pullTextureAsync(atlas);
    }

    /**
     * Copy a GPU texture into a freshly-allocated {@link NativeImage} via a staging buffer.
     * <b>Must be called on the render thread.</b> The source texture must have been created with
     * <code>GpuTexture.USAGE_COPY_SRC</code>.
     */
    private static CompletableFuture<ManagedNativeImage> downloadTexture(GpuTexture texture) {
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        int blockSize = texture.getFormat().blockSize();

        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(() -> "Texture readback buffer",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * blockSize);

        CompletableFuture<ManagedNativeImage> future = new CompletableFuture<>();
        device.createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                NativeImage image = new NativeImage(width, height, false);
                ByteBuffer data = view.data();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixelABGR(x, y, data.getInt((x + y * width) * blockSize));
                    }
                }
                future.complete(ManagedNativeImage.of(image));
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                buffer.close();
            }
        }, 0);

        return future;
    }

    private static void onRenderThread(Runnable r) {
        if (RenderSystem.isOnRenderThread()) {
            r.run();
        } else {
            Minecraft.getInstance().execute(r);
        }
    }

    @Deprecated
    private static NativeImage copyNativeImage(NativeImage from) {
        NativeImage to = new NativeImage(from.format(), from.getWidth(), from.getHeight(), false);
        to.copyFrom(from);
        return to;
    }
}