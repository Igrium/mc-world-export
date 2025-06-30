package com.igrium.worldexport.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientWorldEvents {
    private ClientWorldEvents() {
    }

    /**
     * Called after the client has joined a new world.
     */
    public static final Event<SetWorld> SET_WORLD = EventFactory.createArrayBacked(SetWorld.class,
            listeners -> world -> {
                for (var l : listeners) {
                    l.onSetWorld(world);
                }
            });

    @FunctionalInterface
    public interface SetWorld {
        void onSetWorld(@Nullable ClientWorld world);
    }

}
