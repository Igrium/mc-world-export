package com.igrium.worldexport;

import com.igrium.worldexport.event.ClientBlockUpdated;
import com.igrium.worldexport.event.ClientWorldEvents;
import com.igrium.worldexport.replay.ReplayRecorder;
import com.igrium.worldexport.replay.ReplayRecordingSettings;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgriumsReplayExporter implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Getter
    private static IgriumsReplayExporter instance;

    @Getter @Nullable
    private ReplayRecorder activeRecorder;

    @Override
    public void onInitialize() {
        instance = this;

    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            if (activeRecorder != null) {
                activeRecorder.onClientTick();
            }
        });

        ClientBlockUpdated.EVENT.register((pos, oldState, newState, world) -> {
            if (activeRecorder != null) {
                activeRecorder.onUpdateBlock(pos, oldState, newState);
            }
        });

        ClientWorldEvents.SET_WORLD.register(world -> {
            if (activeRecorder != null && world != activeRecorder.getWorld()) {
                LOGGER.info("Stopped capture due to world change.");
                activeRecorder = null;
            }
        });
    }

    /**
     * Start recording with a specified recorder.
     * @param recorder Recorder to record with.
     * @return <code>recorder</code>
     * @implNote If there's already a recording, old recording silently stops.
     */
    public ReplayRecorder startRecording(ReplayRecorder recorder) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            throw new IllegalStateException("Can only start recording from the main thread.");
        }
        activeRecorder = recorder;
        recorder.startRecording();
        LOGGER.info("Started capturing '{}'", recorder.getSettings().getName());
        return recorder;
    }

    /**
     * Start recording the current client world.
     * @param settings Recording settings.
     * @return The newly-created recorder.
     * @implNote If there's already a recording, old recording silently stops.
     */
    public ReplayRecorder startRecording(ReplayRecordingSettings settings) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            throw new IllegalStateException("Client must be connected to a world to record.");
        }
        return startRecording(new ReplayRecorder(settings, world));
    }
}