package org.scaffoldeditor.worldexport.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.export.ExportContext.ModelEntry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
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
    
    public static void writeStill(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context, OutputStream os) throws IOException {
        NbtIo.writeCompressed(exportStill(world, minChunk, maxChunk, context), os);
    }

    public static NbtCompound exportStill(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context) {
        NbtList sectionTag = new NbtList();

        for (int x = minChunk.x; x < maxChunk.x; x++) {
            for (int z = minChunk.z; z < maxChunk.z; z++) {
                if (!world.isChunkLoaded(x, z))
                    continue;
                Chunk chunk = world.getChunk(x, z);
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i] == null) continue;
                    sectionTag.add(writeSection(sections[i], world, x, chunk.sectionIndexToCoord(i), z, context));
                }
            }
        }

        NbtCompound tag = new NbtCompound();
        tag.put("sections", sectionTag);
        return tag;
    }

    private static NbtCompound writeSection(ChunkSection section, WorldAccess world,
            int sectionX, int sectionY, int sectionZ, ExportContext context) {

        BlockRenderManager dispatcher = client.getBlockRenderManager();
        LogManager.getLogger().info("Exporting section [" + sectionX +", " + sectionY + ", " + sectionZ + "]");

        NbtCompound tag = new NbtCompound();
        tag.putInt("x", sectionX);
        tag.putInt("y", sectionY);
        tag.putInt("z", sectionZ);
        List<String> palette = new ArrayList<>();
        byte[] blocks = new byte[16 * 16 * 16];

        IntStream.range(0, 16).parallel().forEach(y -> {
            IntStream.range(0, 16).parallel().forEach(z -> {
                IntStream.range(0, 16).parallel().forEach(x -> {
                    BlockState state = section.getContainer().get(x, y, z);
                    BlockPos worldPos = new BlockPos(sectionX * 16 + x, sectionY * 16 + y, sectionZ * 16 + z);
                    BlockPos.Mutable mutable = worldPos.mutableCopy();
                    boolean[] faces = new boolean[6];

                    for (int i = 0; i < DIRECTIONS.length; i++) {
                        Direction direction = DIRECTIONS[i];
                        mutable.set(worldPos, direction);
                        faces[i] = Block.shouldDrawSide(state, world, worldPos, direction, mutable);
                    }

                    ModelEntry entry = new ModelEntry(dispatcher.getModel(state), faces);
                    String id = context.getID(entry, BlockModels.getModelId(state).toString());

                    // We don't want threads overwriting each other in the palette
                    int index;
                    synchronized(palette) {
                        index = palette.indexOf(id);
                        if (index < 0) {
                            index = palette.size();
                            palette.add(id);
                        }
                    }
                    
                    blocks[(y * 16 + z) * 16 + x] = (byte) index;
                });
            });
        });
        NbtList paletteTag = new NbtList();
        for (String entry : palette) paletteTag.add(NbtString.of(entry));
        tag.put("palette", paletteTag);

        NbtByteArray blockTag = new NbtByteArray(blocks);
        tag.put("blocks", blockTag);

        return tag;
    }
}
