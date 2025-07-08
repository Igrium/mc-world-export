package com.igrium.worldexport;

import com.igrium.worldexport.command.ProfileDiffsCommand;
import com.igrium.worldexport.command.WorldCaptureCommand;
import com.igrium.worldexport.replay.CapturedReplay;
import com.igrium.worldexport.replay.ReplayCapture;
import com.igrium.worldexport.replay.ReplayIO;
import com.igrium.worldexport.replay.ReplaySettings;
import de.javagl.obj.Obj;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class IgriumsReplayExporter implements ClientModInitializer {
    public static final String MOD_ID = "worldexport";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Getter
    private static IgriumsReplayExporter instance;

    @Getter
    private volatile ReplayCapture activeRecording;

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientCommandRegistrationCallback.EVENT.register(WorldCaptureCommand::register);
        ClientCommandRegistrationCallback.EVENT.register(ProfileDiffsCommand::register);
    }

    public ReplayCapture startRecording(World world, ReplaySettings settings) {
        activeRecording = new ReplayCapture(world, settings);
        activeRecording.beginCapture();
        return activeRecording;
    }

    public CompletableFuture<?> saveRecording() {
        if (activeRecording == null) {
            throw new IllegalStateException("Not recording");
        }

        return activeRecording.compile().thenAccept(r -> {
            try {
                ReplayIO.saveReplay(FabricLoader.getInstance().getGameDir().resolve("ReplayTest"), r);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            activeRecording = null;
        }).exceptionally(e -> {
            LOGGER.error("Error saving replay: ", e);
            return null;
        });
    }
}
