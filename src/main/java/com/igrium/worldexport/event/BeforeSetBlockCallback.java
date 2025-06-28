package com.igrium.worldexport.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Called on the client and server when a blockstate is about to be updated.
 */
public interface BeforeSetBlockCallback {
    public static final Event<BeforeSetBlockCallback> EVENT = EventFactory.createArrayBacked(BeforeSetBlockCallback.class,
            listeners -> (pos, newState, world) -> {
                for (var l : listeners) {
                    l.beforeSetBlockState(pos, newState, world);
                }
            });

    /**
     * Called before a blockstate is updated.
     * @param pos Position of the block being updated.
     * @param newState The block that's being placed.
     * @param world The world it's being placed in.
     */
    void beforeSetBlockState(BlockPos pos, BlockState newState, World world);
}
