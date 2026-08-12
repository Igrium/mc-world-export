package com.igrium.worldexport.compat.replaymod.gui;

import com.mojang.blaze3d.systems.RenderSystem;

import lombok.Getter;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The map data for the bounds editor.
 */
@Getter
public class OverviewData implements AutoCloseable {
    private final int width;
    private final int height;
    private final ChunkPos origin;

    private final DynamicTexture texture;

    private int lastHeight = 0;

    private static final int EMPTY_COLOR = ARGB.color(0, 128, 128, 128);

    /**
     * Construct an overview data object.
     *
     * @param width  The width of the map in chunks.
     * @param height The height of the map in chunks.
     * @param origin The top-left corner of the map.
     */
    public OverviewData(int width, int height, ChunkPos origin) {
        this.width = width;
        this.height = height;
        this.origin = origin;
        texture = new DynamicTexture("WorldExport/Overview", width * 16, height * 16, true);
    }

    /**
     * Update the data in the texture and block until it's complete.
     *
     * @param world      World to use.
     * @param lowerDepth Minimum height in blocks.
     * @param maxHeight  Maximum height in blocks.
     */
    public void updateTexture(Level world, int lowerDepth, int maxHeight) {
        RenderSystem.assertOnRenderThread();

        lastHeight = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                ChunkPos pos = new ChunkPos(x + origin.x(), z + origin.z());
                updateChunk(world, pos, lowerDepth, maxHeight);
            }
        }

        texture.upload();
    }

    private void updateChunk(Level world, ChunkPos chunkPos, int lowerDepth, int maxHeight) {
        int offsetX = chunkPos.x() - origin.x();
        int offsetZ = chunkPos.z() - origin.z();

        // SHOULD prevent anything from being written outside the image.
        if (offsetX < 0 || offsetZ < 0) return;
        if (offsetX >= width || offsetZ >= height) return;

        LevelChunk chunk = world.getChunk(chunkPos.x(), chunkPos.z());
        if (chunk.isEmpty()) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int imageX = offsetX * 16 + x;
                    int imageY = offsetZ * 16 + y;
                    texture.getPixels().setPixel(imageX, imageY, EMPTY_COLOR);
                }
            }
            return;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int imageX = offsetX * 16 + x;
                int imageY = offsetZ * 16 + z;
                int color = EMPTY_COLOR;

                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                height = Math.min(height, maxHeight);

                mutable.set(x, height, z);
                if (height >= lowerDepth) {
                    BlockState blockState;

                    // Get highest non-clear blockstate
                    while ((blockState = chunk.getBlockState(mutable)).getMapColor(world, mutable) == MapColor.NONE && height > lowerDepth) {
                        mutable.setY(--height);
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

                    color = mapColor.calculateARGBColor(brightness);
                }

                texture.getPixels().setPixel(imageX, imageY, color);
                lastHeight = height;
            }
        }
    }

    @Override
    public void close() {
        texture.close();
    }
}