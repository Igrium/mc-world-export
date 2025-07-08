package com.igrium.worldexport.command;

import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplaySettings;
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
        ChunkSectionPos center = ChunkSectionPos.from(context.getSource().getPosition());
        ChunkSectionBox bounds = ChunkSectionBox.fromRadius(center, radius);
        ReplaySettings settings = ReplaySettings.builder()
                .bounds(bounds)
                .build();

        IgriumsReplayExporter.getInstance().startRecording(context.getSource().getWorld(), settings);

        context.getSource().sendFeedback(Text.literal("Capturing " + bounds.count() + " sections..."));
        return 1;
    }

    public static int save(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        if (IgriumsReplayExporter.getInstance().getActiveRecording() == null) {
            throw NO_RECORDING.create();
        }

        context.getSource().sendFeedback(Text.literal("Saving recording..."));
        IgriumsReplayExporter.getInstance().saveRecording().thenRun(() -> {
            context.getSource().sendFeedback(Text.literal("Saved recording. Check console for details."));
        });

        return 1;
    }
}
