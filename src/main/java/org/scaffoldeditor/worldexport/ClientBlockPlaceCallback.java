package org.scaffoldeditor.worldexport;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Called on the client when a block has been updated.
 */
public interface ClientBlockPlaceCallback {
    Event<ClientBlockPlaceCallback> EVENT = EventFactory.createArrayBacked(ClientBlockPlaceCallback.class,
        (listeners) -> (pos, oldState, state, world) -> {
            for (ClientBlockPlaceCallback listener : listeners) {
                listener.place(pos, oldState, state, world);
            }
    });
    
    void place(BlockPos pos, @Nullable BlockState oldState, BlockState state, World world);
}
