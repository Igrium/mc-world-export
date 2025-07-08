package com.igrium.worldexport.command;

import com.igrium.worldexport.v1.replay.ReplayRecorder;
import com.igrium.worldexport.v1.replay.ReplayRecordingSettings;
import com.igrium.worldexport.v1.replay.ReplaySerializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WorldCaptureCommand
{
    private static final SimpleCommandExceptionType NO_RECORDING = new SimpleCommandExceptionType(Text.translatable("command.worldcapture.save.norecording"));
    private static final DynamicCommandExceptionType SAVE_FAILED = new DynamicCommandExceptionType(
            arg -> Text.translatable("command.worldcapture.save.failed", arg));

    public static void register(CommandDispatcher<FabricClientCommandSource> commandDispatcher, CommandRegistryAccess commandRegistryAccess) {
        commandDispatcher.register(literal("worldcapture").then(
                literal("start").then(
                        argument("radius", IntegerArgumentType.integer()).executes(WorldCaptureCommand::start)
                )
        ).then(
                literal("save").executes(WorldCaptureCommand::save)
        ));
    }

    public static int start(CommandContext<FabricClientCommandSource> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");

        ChunkSectionPos playerChunk = ChunkSectionPos.from(context.getSource().getPlayer());
        ChunkSectionPos minChunk = playerChunk.add(-radius, -radius, -radius);
        ChunkSectionPos maxChunk = playerChunk.add(radius, radius, radius);

        int numChunks = (maxChunk.getX() - minChunk.getX())
                * (maxChunk.getY() - minChunk.getY())
                * (maxChunk.getZ() - minChunk.getZ());

        context.getSource().sendFeedback(Text.translatable("command.worldcapture.start", numChunks, minChunk.toShortString(), maxChunk.toShortString()));

        ReplayRecordingSettings settings = new ReplayRecordingSettings();
        settings.setBounds(minChunk, maxChunk);

        IgriumsReplayExporter.getInstance().startRecording(settings);

        return numChunks;
    }

    public static int save(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ReplayRecorder recorder = IgriumsReplayExporter.getInstance().getActiveRecorder();
        if (recorder == null) {
            throw NO_RECORDING.create();
        }

        Path savePath = FabricLoader.getInstance().getGameDir().resolve("replaytest");

        long startTime = Util.getMeasuringTimeMs();

        try {
            Files.createDirectory(savePath);
        } catch (IOException e) {
            IgriumsReplayExporter.LOGGER.error("Error allocating folder for replay: ", e);
            throw SAVE_FAILED.create(e.getMessage());
        }

        IgriumsReplayExporter.getInstance().stopRecording();

        recorder.compile(Util.getMainWorkerExecutor(), 0).thenCompose(cap -> {
            ReplaySerializer serializer = new ReplaySerializer();
            return serializer.saveReplay(cap, savePath);
        }).exceptionally(e -> {
            IgriumsReplayExporter.LOGGER.error("Error saving replay: ", e);
            context.getSource().sendError(Text.translatable("command.worldcapture.save.failed", e.getMessage()));
            return null;
        }).thenRun(() -> {
            context.getSource().sendFeedback(
                    Text.translatable("command.worldcapture.save.confirmed", savePath, Util.getMeasuringTimeMs() - startTime));
        });

        return 1;
    }
}
