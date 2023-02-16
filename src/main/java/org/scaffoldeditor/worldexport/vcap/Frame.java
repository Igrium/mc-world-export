package org.scaffoldeditor.worldexport.vcap;

import java.util.Optional;

import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a single Vcap frame.
 */
public interface Frame {
    public static final byte INTRACODED_TYPE = 0;
    public static final byte PREDICTED_TYPE = 1;

    /**
     * A frame with no blocks in it.
     */
    public static final Frame EMPTY = new EmptyFrame();

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

    /**
     * Get the fluid domain at a particular position.
     * @param pos The position to test.
     * @return The optional fluid frame.
     */
    public default Optional<FluidDomain> fluidAt(BlockPos pos) {
        return Optional.empty();
    }
}

class EmptyFrame implements Frame {

    private NbtCompound data = new NbtCompound();

    public EmptyFrame() {
        data.putByte("type", getFrameType());
        data.putDouble("time", getTimestamp());
        data.put("sections", new NbtList());
    }

    @Override
    public byte getFrameType() {
        return INTRACODED_TYPE;
    }

    @Override
    public double getTimestamp() {
        return 0;
    }

    @Override
    public NbtCompound getFrameData() {
        return data;
    }

    @Override
    public String modelAt(BlockPos pos) throws IndexOutOfBoundsException {
        return MeshWriter.EMPTY_MESH;
    }
    
}