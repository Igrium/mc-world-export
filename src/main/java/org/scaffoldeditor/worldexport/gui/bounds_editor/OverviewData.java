package org.scaffoldeditor.worldexport.gui.bounds_editor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * The map data for the bounds editor.
 */
public class OverviewData implements AutoCloseable {
    private final int width;
    private final int height;
    private final ChunkPos origin;

    private final NativeImageBackedTexture texture;

    private static final int EMPTY_COLOR = ColorHelper.Argb.getArgb(0, 128, 128, 128);

    /**
     * Construct an overview data object.
     * @param width The width of the map in chunks.
     * @param height The height of the map in chunks.
     * @param origin The top-left corner of the map.
     */
    public OverviewData(int width, int height, ChunkPos origin) {
        this.width = width;
        this.height = height;
        this.origin = origin;
        texture = new NativeImageBackedTexture(width * 16, height * 16, true);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ChunkPos getOrigin() {
        return origin;
    }

    public NativeImageBackedTexture getTexture() {
        return texture;
    }

    /**
     * Update the data in the texture and block until it's complete.
     * @param world World to use.
     * @param lowerDepth Minimum height in blocks.
     * @param maxHeight Maximum height in blocks.
     * @param executor Executor to dispatch CPU image rendering to.
     */
    public void updateTexture(World world, int lowerDepth, int maxHeight, Executor executor) {
        RenderSystem.assertOnRenderThread();
        List<CompletableFuture<?>> futures = new LinkedList<>();
        
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                ChunkPos pos = new ChunkPos(x + origin.x, z + origin.z);
                futures.add(CompletableFuture.runAsync(() -> updateChunk(world, pos, lowerDepth, maxHeight), executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        texture.upload();
    }

    private void updateChunk(World world, ChunkPos chunkPos, int lowerDepth, int maxHeight) {
        int offsetX = chunkPos.x - origin.x;
        int offsetZ = chunkPos.z - origin.z;

        // These checks SHOULD prevent anything from being written outside of the image.
        if (offsetX < 0 || offsetZ < 0) return;
        if (offsetX >= width || offsetZ >= height) return;

        WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk.isEmpty()) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int imageX = offsetX * 16 + x;
                    int imageY = offsetZ * 16 + y;
                    texture.getImage().setColor(imageX, imageY, EMPTY_COLOR);
                }
            }
            return;
        };

        int lastHeight = 0;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++){
            for (int z = 0; z < 16; z++) {
                int imageX = offsetX * 16 + x;
                int imageY = offsetZ * 16 + z;
                int color = EMPTY_COLOR;

                int height = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                height = Math.min(height, maxHeight);

                mutable.set(x, height, z);
                if (height >= lowerDepth) {
                    BlockState blockState;

                    // Get highest non-clear blockstate
                    while ((blockState = chunk.getBlockState(mutable)).getMapColor(world, mutable) == MapColor.CLEAR && height > lowerDepth) {
                        mutable.setY(height--);
                    }

                    int deltaHeight = height - lastHeight;

                    MapColor mapColor = blockState.getMapColor(world, mutable);
                    MapColor.Brightness brightness;
                    if (deltaHeight > 0) {
                        brightness = MapColor.Brightness.HIGH;
                    } else if (deltaHeight < 0) {
                        brightness = MapColor.Brightness.LOW;
                    } else {
                        brightness = MapColor.Brightness.NORMAL;
                    }

                    byte colorByte = mapColor.getRenderColorByte(brightness);
                    color = MapColor.getRenderColor(colorByte);
                }

                texture.getImage().setColor(imageX, imageY, color);
                lastHeight = height;
            }
        }
    }

    @Override
    public void close() {
        texture.close();
    }
}
