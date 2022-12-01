package org.scaffoldeditor.worldexport.vcap;

import java.util.Optional;

import org.scaffoldeditor.worldexport.vcap.fluid.FluidDomain;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

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

    /**
     * Get the fluid domain at a particular position.
     * @param pos The position to test.
     * @return The optional fluid frame.
     */
    public default Optional<FluidDomain> fluidAt(BlockPos pos) {
        return Optional.empty();
    }
}
