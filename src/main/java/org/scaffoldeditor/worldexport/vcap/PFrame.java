package org.scaffoldeditor.worldexport.vcap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

public class PFrame implements Frame {

    private static boolean isInBBox(BlockPos pos, ChunkPos minChunk, ChunkPos maxChunk) {
        return (minChunk.getStartX() <= pos.getX()) && (pos.getX() < maxChunk.getStartX())
            && (minChunk.getStartZ() <= pos.getZ()) && (pos.getZ() < maxChunk.getStartZ());
    }

    /**
     * Capture a predicted frame.
     * 
     * @param world     World to capture.
     * @param blocks    A set of blocks to include data for in the frame.
     *                  All ajacent blocks will be queried, and if they are found to
     *                  have changed, they are also included in the frame. Note that
     *                  only blocks within the export context's bounding box are
     *                  captured. See
     *                  {@link VcapSettings#setBBox(ChunkPos, ChunkPos)} for more
     *                  info.
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

        PFrame frame = new PFrame(world, previous, timestamp);
        frame.capture(blocks, context);
        return frame;
    }

    private Map<BlockPos, String> updated = new HashMap<>();
    private Map<BlockPos, BlockState> states = new HashMap<>();
    private static MinecraftClient client = MinecraftClient.getInstance();

    private Map<BlockPos, FluidDomain> fluids = new HashMap<>();
    private Set<BlockPos> handledFluids = new HashSet<>();

    public final Frame previous;
    public final double timestamp;
    public final WorldAccess world;

    public PFrame(Map<BlockPos, String> updated, Map<BlockPos, BlockState> states, WorldAccess world,
            Frame previous, double timestamp) {
        this.updated = updated;
        this.states = states;
        this.timestamp = timestamp;
        this.previous = previous;
        this.world = world;
    }

    protected PFrame(WorldAccess world, Frame previous, double timestamp) {
        this.timestamp = timestamp;
        this.previous = previous;
        this.world = world;
    }

    protected void capture(Set<BlockPos> blocks, ExportContext context) {
        ChunkPos minChunk = context.getSettings().getMinChunk();
        ChunkPos maxChunk = context.getSettings().getMaxChunk();

        int lowerDepth = context.getSettings().getLowerDepth() * 16; // In blocks, not chunks.
        Set<BlockPos> fluidPositions = new HashSet<>();

        for (BlockPos pos : blocks) {
            if (pos.getY() < lowerDepth || !isInBBox(pos, minChunk, maxChunk)) continue;
            if (context.getSettings().exportDynamicFluids() && !world.getBlockState(pos).getFluidState().isEmpty()) {
                fluidPositions.add(pos);
            } else {
                updated.put(pos, BlockExporter.exportBlock(world, pos, context));
                states.put(pos, world.getBlockState(pos));
            }

            states.put(pos, world.getBlockState(pos));
            // Check adjacent blocks.
            for (Direction dir : Direction.values()) {
                BlockPos adjacent = pos.offset(dir);
                if (pos.getY() < lowerDepth || !isInBBox(pos, minChunk, maxChunk)) continue;
                if (updated.containsKey(adjacent)) continue;
                
                if (previous.fluidAt(adjacent).isPresent()) {
                    fluidPositions.add(adjacent);
                }

                String old;
                try {
                    old = previous.modelAt(adjacent);
                } catch (IndexOutOfBoundsException e) {
                    continue;
                }
                String newId = BlockExporter.exportBlock(world, adjacent, context);
                if (!old.equals(newId)) {
                    updated.put(adjacent, newId);
                    states.put(adjacent, world.getBlockState(adjacent));
                }
            }
        }

        for (BlockPos pos : fluidPositions) {
            genFluid(pos, world, context);
        }
    }

    private void genFluid(BlockPos pos, WorldAccess world, ExportContext context) {
        // We've already exported this fluid.
        if (handledFluids.contains(pos)) return;
        FluidState fluidState = world.getBlockState(pos).getFluidState();
        
        if (fluidState.isEmpty()) {
            // throw new IllegalStateException("Fluid at "+pos+" is empty!");
            return;
        }
        Fluid fluidType = fluidState.getFluid();

        FluidDomain fluid = new FluidDomain(pos, fluidType, context);
        fluid.capture(world);

        Optional<FluidDomain> lastFrame = previous.fluidAt(pos);
        if (lastFrame.isPresent()) {
            FluidDomain last = lastFrame.get();
            // Fluid is identical to last frame.
            if (fluid.matches(last, context.getMeshComparator())) {
                handledFluids.addAll(last.getPositions());
                return;
            }

            // Clean up last frame if we need to.
            if (!fluid.getRootPos().equals(last.getRootPos()) && !updated.containsKey(last.getRootPos())) {
                updated.put(last.getRootPos(), BlockExporter.exportBlock(world, pos, context));
                states.put(last.getRootPos(), world.getBlockState(pos));
            }
        }

        for (BlockPos fluidPos : fluid.getPositions()) {
            fluids.put(fluidPos, fluid);
            handledFluids.add(pos);
        }

        String meshId = context.addExtraModel("fluid.0", fluid.getMesh());
        updated.put(fluid.getRootPos(), meshId);
        states.put(fluid.getRootPos(), world.getBlockState(fluid.getRootPos()));
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
        for (BlockPos pos : updated.keySet()) {
            String id = updated.get(pos);

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
        if (updated.containsKey(pos)) {
            return updated.get(pos);
        } else {
            return previous.modelAt(pos);
        }
    }

    @Override
    public Optional<FluidDomain> fluidAt(BlockPos pos) {
        if (fluids.containsKey(pos)) {
            return Optional.of(fluids.get(pos));
        } else {
            return previous.fluidAt(pos);
        }
    }
    
}