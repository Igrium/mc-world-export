package org.scaffoldeditor.worldexport.vcap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;
import org.scaffoldeditor.worldexport.world_snapshot.ChunkView;

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

public class PFrame implements Frame {

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
    public static PFrame capture(ChunkView world,
            Set<BlockPos> blocks,
            double timestamp,
            Optional<Frame> previous,
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

    protected Optional<Frame> previous;
    public final double timestamp;
    public final ChunkView world;

    public PFrame(Map<BlockPos, String> updated, Map<BlockPos, BlockState> states, ChunkView world,
            Optional<Frame> previous, double timestamp) {
        this.updated = updated;
        this.states = states;
        this.timestamp = timestamp;
        this.previous = previous;
        this.world = world;
    }

    protected PFrame(ChunkView world, Optional<Frame> previous, double timestamp) {
        this.timestamp = timestamp;
        this.previous = previous;
        this.world = world;
    }

    protected void capture(Set<BlockPos> blocks, ExportContext context) {
        Set<BlockPos> fluidPositions = new HashSet<>();

        for (BlockPos pos : blocks) {
            if (!context.getSettings().isInExport(pos)) continue;
            if (context.getSettings().exportDynamicFluids() && !world.getBlockState(pos).getFluidState().isEmpty()) {
                fluidPositions.add(pos);
            } else {
                putBlock(pos, BlockExporter.exportBlock(world, pos, context), world.getBlockState(pos));
            }

            states.put(pos, world.getBlockState(pos));
            // Check adjacent blocks.
            for (Direction dir : Direction.values()) {
                BlockPos adjacent = pos.offset(dir);
                if (!context.getSettings().isInExport(pos)) continue;
                if (updated.containsKey(adjacent)) continue;
                
                if (getPrevious().fluidAt(adjacent).isPresent()) {
                    fluidPositions.add(adjacent);
                }

                String old;
                try {
                    old = getPrevious().modelAt(adjacent);
                } catch (IndexOutOfBoundsException e) {
                    continue;
                }
                String newId = BlockExporter.exportBlock(world, adjacent, context);
                if (!old.equals(newId)) {
                    putBlock(adjacent, newId, world.getBlockState(adjacent));
                }
            }
        }
        
        for (BlockPos pos : fluidPositions) {
            genFluid(pos, world, context);
        }
    }

    @Deprecated
    private void genFluid(BlockPos pos, ChunkView world, ExportContext context) {
        if (!context.getSettings().exportDynamicFluids()) return;
        // We've already exported this fluid.
        if (handledFluids.contains(pos)) return;
        FluidState fluidState = world.getBlockState(pos).getFluidState();
        Optional<FluidDomain> lastFrame = getPrevious().fluidAt(pos);
        
        if (fluidState.isEmpty()) {
            // Clean up last frame
            if (lastFrame.isPresent()) {
                BlockPos rootPos = lastFrame.get().getRootPos();
                putBlock(rootPos, BlockExporter.exportBlock(world, rootPos, context), world.getBlockState(rootPos));
            }
            return;
        }
        Fluid fluidType = fluidState.getFluid();

        FluidDomain fluid = new FluidDomain(pos, fluidType, context);
        fluid.capture(world);

        if (lastFrame.isPresent()) {
            FluidDomain last = lastFrame.get();
            // Fluid is identical to last frame.
            if (fluid.matches(last, context.getMeshComparator())) {
                handledFluids.addAll(last.getPositions());
                return;
            }

            // Clean up last frame if we need to.
            if (!fluid.getRootPos().equals(last.getRootPos()) && !updated.containsKey(last.getRootPos())) {
                BlockPos rootPos = last.getRootPos();
                putBlock(rootPos, BlockExporter.exportBlock(world, rootPos, context), world.getBlockState(rootPos));
            }
        }

        for (BlockPos fluidPos : fluid.getPositions()) {
            fluids.put(fluidPos, fluid);
            handledFluids.add(fluidPos);
        }


        String meshId = context.addFluid(fluid);
        putBlock(fluid.getRootPos(), meshId, world.getBlockState(fluid.getRootPos()));
    }

    protected void putBlock(BlockPos pos, String meshName, BlockState state) {
        updated.put(pos, meshName);
        states.put(pos, state);
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
            return getPrevious().modelAt(pos);
        }
    }

    @Override
    public Optional<FluidDomain> fluidAt(BlockPos pos) {
        if (fluids.containsKey(pos)) {
            return Optional.of(fluids.get(pos));
        } else {
            return getPrevious().fluidAt(pos);
        }
    }
    
    /**
     * Get the previous frame, or an empty frame if no previous was set.
     * @return The previous frame.
     */
    public Frame getPrevious() {
        return previous.orElse(Frame.EMPTY);
    }

    public void setPrevious(Optional<Frame> previous) {
        this.previous = previous;
    }
    
}