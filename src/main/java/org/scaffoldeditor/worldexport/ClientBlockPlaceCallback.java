package org.scaffoldeditor.worldexport;

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
        (listeners) -> (pos, state, world) -> {
            for (ClientBlockPlaceCallback listener : listeners) {
                listener.place(pos, state, world);
            }
    });
    
    void place(BlockPos pos, BlockState state, World world);
}
