package org.scaffoldeditor.worldexport.vcap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.scaffoldeditor.worldexport.vcap.BlockExporter.CaptureCallback;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidConsumer;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;
import org.scaffoldeditor.worldexport.world_snapshot.ChunkView;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshot;
import org.scaffoldeditor.worldexport.world_snapshot.WorldSnapshotManager;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

public class IFrame implements Frame, FluidConsumer {

    private NbtCompound data;
    /**
     * Used when retrieving data from the frame so we don't have to keep locating
     * the section.
     */

    private Map<Vec3i, NbtCompound> sectionCache = new HashMap<>();
    private Map<BlockPos, FluidDomain> fluids = new ConcurrentHashMap<>();

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
     * @param bounds   Region to export.
     * @param context  The export context.
     * @param time     Time stamp of the frame, in seconds since the beginning
     *                 of the animation.
     * @return Captured frame.
     */
    public static IFrame capture(ChunkView world, BlockBox bounds, ExportContext context, double time, @Nullable CaptureCallback callback) {
        IFrame iFrame = new IFrame();
        iFrame.captureData(world, bounds, context, time, callback);
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
    public static CompletableFuture<IFrame> captureAsync(ChunkView world, BlockBox bounds,
            ExportContext context, double time, Executor executor, @Nullable CaptureCallback callback) {

        WorldSnapshot snapshot = WorldSnapshotManager.getInstance().snapshot(world);
        IFrame iFrame = new IFrame();
        return iFrame.captureDataAsync(snapshot, bounds, context, time, callback, executor)
                .thenApply(data -> iFrame);
    }

    protected void captureData(ChunkView world, BlockBox bounds, ExportContext context,
            double time, @Nullable CaptureCallback callback) {
        NbtCompound frame = new NbtCompound();
        frame.put("sections", BlockExporter.exportStillAsync(world, bounds, context, this, callback, Runnable::run).join());
        frame.putByte("type", INTRACODED_TYPE);
        frame.putDouble("time", time);
        this.data = frame;
    }
    
    protected CompletableFuture<NbtCompound> captureDataAsync(ChunkView world, BlockBox bounds,
            ExportContext context, double time, @Nullable CaptureCallback callback, Executor executor) {
        return BlockExporter.exportStillAsync(world, bounds, context, this, callback, executor)
        .thenApply(sections -> {
            NbtCompound frame = new NbtCompound();
            frame.put("sections", sections);
            frame.putByte("type", INTRACODED_TYPE);
            frame.putDouble("time", time);
            this.data = frame;
            return frame;
        });
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
        Vec3i sectionCoord = new Vec3i((int) Math.floor(pos.getX() / 16), (int) Math.floor(pos.getY() / 16),
                (int) Math.floor(pos.getZ() / 16));
        
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
    public synchronized void putFluid(FluidDomain fluid) {
        for (BlockPos pos : fluid.getPositions()) {
            fluids.put(pos, fluid);
        }
    }
}