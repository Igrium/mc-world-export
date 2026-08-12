package com.igrium.worldexport.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Called on the client when a block has been updated.
 */
public interface ClientBlockUpdatedEvent {

    Event<ClientBlockUpdatedEvent> EVENT = EventFactory.createArrayBacked(ClientBlockUpdatedEvent.class,
            listeners -> (pos, oldState, newState, world) -> {
                for (var listener : listeners) {
                    listener.onBlockUpdated(pos, oldState, newState, world);
                }
            });

    void onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, Level world);
}
