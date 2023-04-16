package org.scaffoldeditor.worldexport.vcap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.vcap.BlockModelEntry.Builder;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidBlockEntry;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidConsumer;
import org.scaffoldeditor.worldexport.world_snapshot.ChunkView;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshot;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshotManager;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

public final class BlockExporter {
    private BlockExporter() {
    };

    /**
     * Gets notified after each section is captured. There is no guarentee
     * thread this callback is called on, and it should return as fast as possible.
     */
    public static interface CaptureCallback {

        /**
         * Called after a chunk is captured.
         * @param chunkX The X position of the chunk.
         * @param chunkY The Y position of the chunk.
         * @param index The number of chunks that were captured before this chunk.
         * @param numSections The total number of chunks.
         */
        void onChunkCaptured(int chunkX, int chunkZ, int index, int numSections);
    }
    
    static final MinecraftClient client = MinecraftClient.getInstance();
    /**
     * The minimum light value at which blocks will be considered emissive.
     */
    public static final int EMISSIVE_THRESHOLD = 4;
    
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static void writeStill(ChunkView world, BlockBox bounds, ExportContext context,
            OutputStream os, @Nullable FluidConsumer fluidConsumer, @Nullable CaptureCallback callback) throws IOException {
        NbtCompound tag = new NbtCompound();
        tag.put("sections", exportStill(world, bounds, context, fluidConsumer, callback));
        NbtIo.writeCompressed(tag, os);
    }

    /**
     * Prepare elements of a model entry that aren't dependend on position in the world.
     * @param state Block state to use.
     * @return The generated builder.
     */
    public static BlockModelEntry.Builder prepareEntry(BlockState state) {
        BlockRenderManager dispatcher = client.getBlockRenderManager();
        BlockModelEntry.Builder builder = new Builder(dispatcher.getModel(state), state);
        builder.transparent(!state.isOpaque());
        builder.emissive(state.getLuminance() >= EMISSIVE_THRESHOLD);
        return builder;
    }

    /**
     * Export a capture of the entire block world.
     * 
     * @return A list with all the exported sections.
     * @deprecated Use <code>exportStillAsync</code> with <code>Runnable::run</code>
     *             as the executor instead.
     */
    @Deprecated
    public static NbtList exportStill(ChunkView world, BlockBox bounds, ExportContext context,
            @Nullable FluidConsumer fluidConsumer, @Nullable CaptureCallback callback) {
        return exportStillAsync(world, bounds, context, fluidConsumer, callback, Runnable::run).join();
    }
    
    /**
     * Capture the entire block world.
     * 
     * @param world         World to capture.
     * @param bounds        The region to export, in chunk section coordinates.
     * @param context       The export context.
     * @param fluidConsumer The fluid consumer to use. Must be thread-safe!
     * @param callback      A capture callback to use. Must be thread-safe!.
     * @param executor      The executor to export the chunks on.
     * @return A list with all the exported sections.
     */
    public static CompletableFuture<NbtList> exportStillAsync(ChunkView world, BlockBox bounds,
            ExportContext context,
            @Nullable FluidConsumer fluidConsumer, @Nullable CaptureCallback callback, Executor executor) {
        if (!(world instanceof WorldSnapshot)) {
            world = WorldSnapshotManager.getInstance().snapshot(world);
        }
        
        return new StillExporterAsync(world, bounds, context, fluidConsumer, callback)
                .exportStill(executor);
    }
    
    /**
     * Some values change during async world export. This class handles those values across threads.
     */
    private static class StillExporterAsync {
        final ChunkView world;
        final BlockBox bounds;
        final ExportContext context;
        @Nullable
        final FluidConsumer fluidConsumer;
        // @Nullable 
        final CaptureCallback callback;

        private int totalChunks;
        private final AtomicInteger chunksExported = new AtomicInteger();

        public StillExporterAsync(ChunkView world, BlockBox bounds, ExportContext context, @Nullable FluidConsumer fluidConsumer, @Nullable CaptureCallback callback) {
            this.world = world;
            this.bounds = bounds;
            this.context = context;
            this.fluidConsumer = fluidConsumer;
            this.callback = callback;
        }

        /**
         * Export a block world asynchronously. This method itself is synchronized
         * because we don't want two instances of the export conflicting with each
         * other.
         * 
         * @param executor Executor to use.
         * @return
         */
        public synchronized CompletableFuture<NbtList> exportStill(Executor executor) {

            // Convert to chunk coordinates
            ChunkPos minChunk = new ChunkPos(bounds.getMinX(), bounds.getMinZ());

            ChunkPos maxChunk = new ChunkPos(bounds.getMaxX(), bounds.getMaxZ());

            totalChunks = (maxChunk.x - minChunk.x + 1) * (maxChunk.z - minChunk.z + 1);
            chunksExported.set(0);

            List<CompletableFuture<? extends Collection<NbtCompound>>> futures = new ArrayList<>();
            for (int x = minChunk.x; x <= maxChunk.x; x++) {
                for (int z = minChunk.z; z <= maxChunk.z; z++) {
                    if (!world.isChunkLoaded(x, z)) continue;
                    futures.add(exportChunkAsync(x, z, executor));
                }
            }

            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
                NbtList list = new NbtList();
                futures.forEach(future -> {
                    future.join().forEach(list::add);
                });

                return list;
            });
        }

        private CompletableFuture<List<NbtCompound>> exportChunkAsync(int x, int z, Executor executor) {
            return CompletableFuture.supplyAsync(() -> exportChunk(x, z), executor);
        }

        private List<NbtCompound> exportChunk(int x, int z) {
            LOGGER.debug("Exporting chunk [{}, {}]", x, z);
            if (!world.isChunkLoaded(x, z)) return Collections.emptyList();
            List<NbtCompound> chunks = new ArrayList<>();

            // Convert to section coordinates
            int minHeight = bounds.getMinY();
            int maxHeight = bounds.getMaxY();

            for (int y = world.getBottomSectionCoord(); y < world.getTopSectionCoord(); y++) {
                if (!world.isSectionLoaded(x, y, z)) continue;
                if (y < minHeight || y > maxHeight) continue;

                chunks.add(writeSection(world, x, y, z, context, fluidConsumer));
            }

            int count = chunksExported.incrementAndGet();

            if (callback != null) {
                callback.onChunkCaptured(x, z, count, totalChunks);
            }

            return chunks;
        }
    }
    
    /**
     * Generate a mesh ID from a block in the world.
     * @param world World to use.
     * @param pos Position of the block.
     * @param context Vcap export context.
     * @return The mesh ID.
     */
    public static String exportBlock(BlockRenderView world, BlockPos pos, ExportContext context) {
        BlockState state = world.getBlockState(pos);
        String id;

        BlockModelEntry.Builder builder = prepareEntry(state);

        BlockPos.Mutable mutable = pos.mutableCopy();
        for (Direction direction : Direction.values()) {
            mutable.set(pos, direction);
            builder.face(direction, Block.shouldDrawSide(state, world, pos, direction, mutable));
        }

        id = context.addBlock(builder.build());

        return id;
    }

    private static NbtCompound writeSection(ChunkView world,
            int sectionX, int sectionY, int sectionZ, ExportContext context, @Nullable FluidConsumer fluidConsumer) {

        LogManager.getLogger().debug("Exporting section [" + sectionX + ", " + sectionY + ", " + sectionZ + "]");

        NbtCompound tag = new NbtCompound();
        tag.putInt("x", sectionX);
        tag.putInt("y", sectionY);
        tag.putInt("z", sectionZ);

        List<String> palette = new ArrayList<>();
        int[] blocks = new int[16 * 16 * 16];

        List<Byte> colorPalette = new ArrayList<>();
        byte[] colors = new byte[16 * 16 * 16];

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos worldPos = new BlockPos(sectionX * 16 + x, sectionY * 16 + y, sectionZ * 16 + z);
                    BlockState state = world.getBlockState(worldPos);
                    String id;

                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty() && context.getSettings().exportStaticFluids() && fluidConsumer != null) {
                        // genFluid(worldPos, world, context, fluidConsumer);

                        // Optional<FluidDomain> thisDomain = fluidConsumer.fluidAt(worldPos);
                        // if (!thisDomain.isPresent()) {
                        //     throw new IllegalStateException("Block at "+worldPos+" is a fluid, but no fluid domain was generated!");
                        // }

                        // if (thisDomain.get().getRootPos().equals(worldPos)) {
                        //     id = context.addFluid(thisDomain.get());
                        // } else {
                        //     id = MeshWriter.EMPTY_MESH;
                        // }
                        FluidBlockEntry fluidMesh = MeshWriter.writeFluidMesh(worldPos, world, state);
                        id = context.addFluid(fluidMesh);

                    } else {
                        BlockPos.Mutable mutable = worldPos.mutableCopy();
                        BlockModelEntry.Builder builder = prepareEntry(state);

                        for (Direction direction : Direction.values()) {
                            mutable.set(worldPos, direction);
                            builder.face(direction, Block.shouldDrawSide(state, world, worldPos, direction, mutable));
                        }

                        id = context.addBlock(builder.build());
                    }
                    
                    int color = client.getBlockColors().getColor(state, world, worldPos, 0);

                    byte r = (byte)(color >> 16 & 255);
                    byte g = (byte)(color >> 8 & 255);
                    byte b = (byte)(color & 255);

                    // We don't want threads overwriting each other in the palette
                    int index;
                    int colorIndex = -1;
                    synchronized(palette) {
                        index = palette.indexOf(id);
                        if (index < 0) {
                            index = palette.size();
                            palette.add(id);
                        }

                        // Look for color in color palette
                        for (int i = 0; i < colorPalette.size(); i += 3) {
                            if (colorPalette.get(i).equals(r) && colorPalette.get(i + 1).equals(g) && colorPalette.get(i + 2).equals(b)) {
                                colorIndex = i;
                                break;
                            }
                        }
                        if (colorIndex < 0) {
                            colorIndex = colorPalette.size();
                            colorPalette.add(r);
                            colorPalette.add(g);
                            colorPalette.add(b);
                        }
                    }

                    blocks[(y * 16 + z) * 16 + x] = index;
                    colors[(y * 16 + z) * 16 + x] = (byte) colorIndex;
                };
            };
        };
        NbtList paletteTag = new NbtList();
        for (String entry : palette) paletteTag.add(NbtString.of(entry));
        tag.put("palette", paletteTag);

        NbtIntArray blockTag = new NbtIntArray(blocks);
        tag.put("blocks", blockTag);

        NbtByteArray colorPaletteTag = new NbtByteArray(colorPalette);
        tag.put("colorPalette", colorPaletteTag);

        NbtByteArray colorsTag = new NbtByteArray(colors);
        tag.put("colors", colorsTag);

        return tag;
    }
}
