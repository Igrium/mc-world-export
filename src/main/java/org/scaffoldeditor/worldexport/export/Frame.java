package org.scaffoldeditor.worldexport.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.WorldAccess;

/**
 * Represents a single Vcap frame.
 */
public interface Frame {
    public static final byte INTRACODED_TYPE = 0;
    public static final byte PREDICTED_TYPE = 1;
    /**
     * Get the type of frame this is.
     * @return <code>0</code> for Intracoded and <code>1</code> for Predicted.
     */
    public byte getFrameType();

    /**
     * Get the timestamp this frame represents.
     * @return Time, in seconds since the start of the vcap.
     */
    public double getTimestamp();

    /**
     * Get the NBT data representing the frame.
     * @return Frame NBT data. Format varies based on type.
     */
    public NbtCompound getFrameData();

    /**
     * Get the Vcap model of a block at this frame.
     * May be dependant on prior frames.
     * @param pos Block to query.
     * @return Vcap model ID.
     * @throws IndexOutOfBoundsException If the queried block is not within the bounds of the Vcap.
     */
    public String modelAt(BlockPos pos) throws IndexOutOfBoundsException;

    public static class PFrame implements Frame {

        private Map<BlockPos, String> data = new HashMap<>();
        private Map<BlockPos, BlockState> states = new HashMap<>();
        private static MinecraftClient client = MinecraftClient.getInstance();

        public final Frame previous;
        public final double timestamp;
        public final WorldAccess world;

        /**
         * Capture a predicted frame.
         * 
         * @param world     World to capture.
         * @param blocks    A set of blocks to include data for in the frame.
         *                  All ajacent blocks will be queried, and if they are found to
         *                  have changed, they are also included in the frame.
         * @param timestamp Time stamp of the frame, in seconds since the beginning
         *                  of the animation.
         * @param previous  The previous frame in the file.
         * @param context   The export context.
         * @return The captured frame.
         */
        public static PFrame capture(WorldAccess world,
                Set<BlockPos> blocks,
                double timestamp,
                Frame previous,
                ExportContext context) {

            Map<BlockPos, String> updates = new HashMap<>();
            Map<BlockPos, BlockState> states = new HashMap<>();
            for (BlockPos pos : blocks) {
                updates.put(pos, BlockExporter.exportBlock(world, pos, context));
                states.put(pos, world.getBlockState(pos));
                // Check adjacent blocks.
                for (Direction dir : Direction.values()) {
                    BlockPos adjacent = pos.offset(dir);
                    if (updates.containsKey(adjacent)) continue;

                    String old;
                    try {
                        old = previous.modelAt(adjacent);
                    } catch (IndexOutOfBoundsException e) {
                        continue;
                    }
                    String updated = BlockExporter.exportBlock(world, adjacent, context);
                    if (!old.equals(updated)) {
                        updates.put(adjacent, updated);
                        states.put(adjacent, world.getBlockState(adjacent));
                    }
                }
            }
            return new PFrame(updates, states, world, previous, timestamp);
        }

        public PFrame(Map<BlockPos, String> updated, Map<BlockPos, BlockState> states, WorldAccess world, Frame previous, double timestamp) {
            this.data = updated;
            this.states = states;
            this.timestamp = timestamp;
            this.previous = previous;
            this.world = world;
        }
        

        @Override
        public byte getFrameType() {
            return PREDICTED_TYPE;
        }

        @Override
        public double getTimestamp() {
            return timestamp;
        }

        @Override
        public NbtCompound getFrameData() {
            NbtCompound frame = new NbtCompound();
            frame.putByte("type", PREDICTED_TYPE);
            frame.putDouble("time", timestamp);
            
            NbtList updates = new NbtList();
            List<String> palette = new ArrayList<>();
            for (BlockPos pos : data.keySet()) {
                String id = data.get(pos);

                NbtCompound entry = new NbtCompound();
                int index = palette.indexOf(id);
                if (index < 0) {
                    index = palette.size();
                    palette.add(id);
                }

                entry.putInt("state", index);
                NbtList posTag = new NbtList();
                List.of(pos.getX(), pos.getY(), pos.getZ()).forEach(val -> posTag.add(NbtInt.of(val)));
                entry.put("pos", posTag);

                BlockState state = states.get(pos);
                if (state == null) {
                    throw new IllegalStateException("Vcap: Block at "+pos+" is missing a blockstate entry!");
                }

                int color = client.getBlockColors().getColor(state, world, pos, 0);

                byte r = (byte)(color >> 16 & 255);
                byte g = (byte)(color >> 8 & 255);
                byte b = (byte)(color & 255);

                NbtList colorTag = new NbtList();
                List.of(r, g, b).forEach(val -> colorTag.add(NbtByte.of(val)));
                entry.put("color", colorTag);

                updates.add(entry);
            }

            frame.put("blocks", updates);
            NbtList paletteTag = new NbtList();
            palette.forEach(val -> paletteTag.add(NbtString.of(val)));
            frame.put("palette", paletteTag);

            return frame;
        }

        @Override
        public String modelAt(BlockPos pos) throws IndexOutOfBoundsException {
            if (data.containsKey(pos)) {
                return data.get(pos);
            } else {
                return previous.modelAt(pos);
            }
        }
        
    }

    public static class IFrame implements Frame {

        private NbtCompound data;
        private Map<Vec3i, NbtCompound> sectionCache = new HashMap<>();

        /**
         * <p>
         * Capture an intracoded frame.
         * </p>
         * <p>
         * Warning: depending on the size of the capture, this may
         * take multiple seconds.
         * </p>
         * 
         * @param world    World to capture.
         * @param minChunk Bounding box min.
         * @param maxChunk Bounding box max.
         * @param context  The export context.
         * @param time     Time stamp of the frame, in seconds since the beginning
         *                 of the animation.
         * @return Captured frame.
         */
        public static IFrame capture(WorldAccess world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context, double time) {
            NbtCompound frame = new NbtCompound();
            frame.put("sections", BlockExporter.exportStill(world, minChunk, maxChunk, context));
            frame.putByte("type", INTRACODED_TYPE);
            frame.putDouble("time", time);

            return new IFrame(frame);
        }

        /**
         * Create a wrapper around an existing IFrame.
         * @param data Properly formatted IFrame data.
         */
        public IFrame(NbtCompound data) {
            this.data = data;
        }

        @Override
        public byte getFrameType() {
            return INTRACODED_TYPE;
        }

        @Override
        public double getTimestamp() {
            return data.getDouble("time");
        }

        @Override
        public NbtCompound getFrameData() {
            return data;
        }

        @Override
        public String modelAt(BlockPos pos) throws IndexOutOfBoundsException {
            Vec3i sectionCoord = new Vec3i(Math.floor(pos.getX() / 16), Math.floor(pos.getY() / 16),
                    Math.floor(pos.getZ() / 16));
            
            NbtCompound section = sectionCache.get(sectionCoord);

            if (section == null) {
                for (NbtElement n : data.getList("sections", 10)) {
                    NbtCompound current = (NbtCompound) n;
                    if (current.getInt("x") == sectionCoord.getX()
                            && current.getInt("y") == sectionCoord.getY()
                            && current.getInt("z") == sectionCoord.getZ()) {
                        section = current;
                        sectionCache.put(sectionCoord, section);
                        break;
                    }
                }
            }

            if (section == null) {
                throw new IndexOutOfBoundsException("Block pos: "+pos+" is not within the Vcap bounds.");
            }

            Vec3i relativePos = new Vec3i(Math.floorMod(pos.getX(), 16), Math.floorMod(pos.getY(), 16),
                    Math.floorMod(pos.getZ(), 16));

            int index = section.getIntArray("blocks")[(relativePos.getY() * 16 + relativePos.getZ()) * 16
                    + relativePos.getZ()];

            return section.getList("palette", 8).getString(index);
        }
    }
}
