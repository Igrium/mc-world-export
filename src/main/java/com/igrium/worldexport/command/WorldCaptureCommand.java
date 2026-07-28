package com.igrium.worldexport.command;

import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.core.SectionPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class WorldCaptureCommand
{
    private static final SimpleCommandExceptionType NO_RECORDING = new SimpleCommandExceptionType(Component.translatable("command.worldcapture.save.norecording"));
    private static final DynamicCommandExceptionType SAVE_FAILED = new DynamicCommandExceptionType(
            arg -> Component.translatable("command.worldcapture.save.failed", arg));

    public static void register(CommandDispatcher<FabricClientCommandSource> commandDispatcher, CommandBuildContext commandRegistryAccess) {
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
        SectionPos center = SectionPos.of(context.getSource().getPosition());
        ChunkSectionBox bounds = ChunkSectionBox.fromRadius(center, radius);
        ReplayExportSettings settings = ReplayExportSettings.builder()
                .bounds(bounds)
                .build();

        IgriumsReplayExporter.getInstance().startRecording(context.getSource().getLevel(), settings);

        context.getSource().sendFeedback(Component.literal("Capturing " + bounds.count() + " sections..."));
        return 1;
    }

    public static int save(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        if (IgriumsReplayExporter.getInstance().getActiveRecording() == null) {
            throw NO_RECORDING.create();
        }

        context.getSource().sendFeedback(Component.literal("Saving recording..."));
        IgriumsReplayExporter.getInstance().saveRecording().thenRun(() -> {
            context.getSource().sendFeedback(Component.literal("Saved recording. Check console for details."));
        });

        return 1;
    }
}
