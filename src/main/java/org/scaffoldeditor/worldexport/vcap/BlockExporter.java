package org.scaffoldeditor.worldexport.vcap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.vcap.ExportContext.ModelEntry;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidConsumer;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;

import de.javagl.obj.Obj;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

public final class BlockExporter {
    private BlockExporter() {
    };

    // Directions must follow this order.
    public static Direction[] DIRECTIONS = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP,
            Direction.DOWN };
    
    static final MinecraftClient client = MinecraftClient.getInstance();
    
    public static void writeStill(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context,
            OutputStream os, @Nullable FluidConsumer fluidConsumer) throws IOException {
        NbtCompound tag = new NbtCompound();
        tag.put("sections", exportStill(world, minChunk, maxChunk, context, fluidConsumer));
        NbtIo.writeCompressed(tag, os);
    }

    public static NbtList exportStill(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context,
            @Nullable FluidConsumer fluidConsumer) {
        NbtList sectionTag = new NbtList();

        for (int x = minChunk.x; x < maxChunk.x; x++) {
            for (int z = minChunk.z; z < maxChunk.z; z++) {
                if (!world.isChunkLoaded(x, z))
                    continue;
                Chunk chunk = world.getChunk(x, z);
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i] == null) continue;

                    int y = chunk.sectionIndexToCoord(i);
                    if (y < context.getSettings().getLowerDepth()) continue;
                    
                    sectionTag.add(writeSection(sections[i], world, x, y, z, context, fluidConsumer));
                }
            }
        }

        return sectionTag;
    }
    
    /**
     * Generate a mesh ID from a block in the world.
     * @param world World to use.
     * @param pos Position of the block.
     * @param context Vcap export context.
     * @return The mesh ID.
     */
    public static String exportBlock(WorldAccess world, BlockPos pos, ExportContext context) {
        BlockState state = world.getBlockState(pos);
        String id;
        BlockRenderManager dispatcher = client.getBlockRenderManager();


        BlockPos.Mutable mutable = pos.mutableCopy();
        boolean[] faces = new boolean[6];

        for (int i = 0; i < DIRECTIONS.length; i++) {
            Direction direction = DIRECTIONS[i];
            mutable.set(pos, direction);
            faces[i] = Block.shouldDrawSide(state, world, pos, direction, mutable);
        }

        ModelEntry entry = new ModelEntry(dispatcher.getModel(state), faces, !state.isOpaque(), state);
        id = context.getID(entry, BlockModels.getModelId(state).toString());

        return id;
    }

    private static void genFluid(BlockPos worldPos, WorldAccess world, ExportContext context, FluidConsumer fluidConsumer) {
        // The fluid for this block was already generated.
        if (fluidConsumer.fluidAt(worldPos).isPresent()) return;
        
        FluidState state = world.getBlockState(worldPos).getFluidState();
        if (state.isEmpty()) {
            throw new IllegalArgumentException("This block is not a fluid!");
        }

        FluidDomain domain = new FluidDomain(worldPos, state.getFluid(), context);
        domain.capture(world);

        fluidConsumer.putFluid(domain);
    }

    private static NbtCompound writeSection(ChunkSection section, WorldAccess world,
            int sectionX, int sectionY, int sectionZ, ExportContext context, @Nullable FluidConsumer fluidConsumer) {

        BlockRenderManager dispatcher = client.getBlockRenderManager();
        LogManager.getLogger().debug("Exporting section [" + sectionX + ", " + sectionY + ", " + sectionZ + "]");

        NbtCompound tag = new NbtCompound();
        tag.putInt("x", sectionX);
        tag.putInt("y", sectionY);
        tag.putInt("z", sectionZ);

        List<String> palette = new ArrayList<>();
        int[] blocks = new int[16 * 16 * 16];

        List<Byte> colorPalette = new ArrayList<>();
        byte[] colors = new byte[16 * 16 * 16];

        IntStream.range(0, 16).forEach(y -> {
            IntStream.range(0, 16).forEach(z -> {
                IntStream.range(0, 16).forEach(x -> {
                    BlockState state = section.getBlockStateContainer().get(x, y, z);
                    BlockPos worldPos = new BlockPos(sectionX * 16 + x, sectionY * 16 + y, sectionZ * 16 + z);
                    String id;

                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty() && context.getSettings().exportStaticFluids() && fluidConsumer != null) {
                        genFluid(worldPos, world, context, fluidConsumer);

                        Optional<FluidDomain> thisDomain = fluidConsumer.fluidAt(worldPos);
                        if (!thisDomain.isPresent()) {
                            throw new IllegalStateException("Block at "+worldPos+" is a fluid, but no fluid domain was generated!");
                        }

                        if (thisDomain.get().getRootPos().equals(worldPos)) {
                            Obj mesh = thisDomain.get().getMesh();
                            id = context.addExtraModel("fluid.0", mesh);
                        } else {
                            id = MeshWriter.EMPTY_MESH;
                        }

                    } else {
                        BlockPos.Mutable mutable = worldPos.mutableCopy();
                        boolean[] faces = new boolean[6];
    
                        for (int i = 0; i < DIRECTIONS.length; i++) {
                            Direction direction = DIRECTIONS[i];
                            mutable.set(worldPos, direction);
                            faces[i] = Block.shouldDrawSide(state, world, worldPos, direction, mutable);
                        }
    
                        ModelEntry entry = new ModelEntry(dispatcher.getModel(state), faces, !state.isOpaque(), state);
                        id = context.getID(entry, BlockModels.getModelId(state).toString());
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
                });
            });
        });
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
