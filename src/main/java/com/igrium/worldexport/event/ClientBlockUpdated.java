package com.igrium.worldexport.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Called on the client when a block has been updated.
 */
public interface ClientBlockUpdated {

    Event<ClientBlockUpdated> EVENT = EventFactory.createArrayBacked(ClientBlockUpdated.class,
            listeners -> (pos, oldState, newState, world) -> {
                for (var listener : listeners) {
                    listener.place(pos, oldState, newState, world);
                }
            });

    void place(BlockPos pos, BlockState oldState, BlockState newState, World world);
}
