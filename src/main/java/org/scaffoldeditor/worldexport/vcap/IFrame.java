package org.scaffoldeditor.worldexport.vcap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.scaffoldeditor.worldexport.vcap.fluid.FluidConsumer;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;
import org.scaffoldeditor.worldexport.world_snapshot.ChunkView;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshot;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshotManager;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;

public class IFrame implements Frame, FluidConsumer {

    private NbtCompound data;
    private Map<Vec3i, NbtCompound> sectionCache = new HashMap<>();
    private Map<BlockPos, FluidDomain> fluids = new HashMap<>();

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
    public static IFrame capture(ChunkView world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context, double time) {
        IFrame iFrame = new IFrame();
        iFrame.captureData(world, minChunk, maxChunk, context, time);
        return iFrame;
    }

    /**
     * Asynchronously capture an intracoded frame.
     * 
     * @param world    World to capture.
     * @param minChunk Bounding box min.
     * @param maxChunk Bounding box max.
     * @param context  The export context.
     * @param time     Time stamp of the frame, in seconds since the beginning of
     *                 the animation.
     * @param executor The executor to use for capture.
     * @return A future that completes with the captured frame.
     */
    public static CompletableFuture<IFrame> captureAsync(ChunkView world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context, double time, Executor executor) {
        WorldSnapshot snapshot = WorldSnapshotManager.getInstance().snapshot(world);
        return CompletableFuture.supplyAsync(() -> {
            IFrame iFrame = new IFrame();
            iFrame.captureData(snapshot, minChunk, maxChunk, context, time);
            return iFrame;
        }, executor);
    }

    protected void captureData(ChunkView world, ChunkPos minChunk, ChunkPos maxChunk, ExportContext context, double time) {
        NbtCompound frame = new NbtCompound();
        frame.put("sections", BlockExporter.exportStill(world, minChunk, maxChunk, context, this));
        frame.putByte("type", INTRACODED_TYPE);
        frame.putDouble("time", time);
        this.data = frame;
    }

    /**
     * Create a wrapper around an existing IFrame.
     * @param data Properly formatted IFrame data.
     */
    public IFrame(NbtCompound data) {
        this.data = data;
    }

    protected IFrame() {};

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

    public Map<BlockPos, FluidDomain> getFluids() {
        return fluids;
    }

    @Override
    public Optional<FluidDomain> fluidAt(BlockPos pos) {
        return Optional.ofNullable(fluids.get(pos));
    }

    @Override
    public void putFluid(FluidDomain fluid) {
        for (BlockPos pos : fluid.getPositions()) {
            fluids.put(pos, fluid);
        }
    }
}