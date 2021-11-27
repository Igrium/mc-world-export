package org.scaffoldeditor.worldexport;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public interface ClientBlockPlaceCallback {
    Event<ClientBlockPlaceCallback> EVENT = EventFactory.createArrayBacked(ClientBlockPlaceCallback.class,
        (listeners) -> (pos, state) -> {
            for (ClientBlockPlaceCallback listener : listeners) {
                ActionResult result = listener.place(pos, state);
 
                if(result != ActionResult.PASS) {
                    return result;
                }
            }
 
        return ActionResult.PASS;
    });
 
    ActionResult place(BlockPos pos, BlockState state);
}
