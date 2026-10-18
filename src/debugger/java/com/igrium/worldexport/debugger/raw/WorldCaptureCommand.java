package com.igrium.worldexport.debugger.raw;

import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.math.ChunkSectionBox;
import com.igrium.worldexport.replay.ReplayExportSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

/**
 * Debug utility that begins capturing the world and saves it directly to a
 * replay file, without going through the export settings GUI or requiring
 * any external playback mod.
 */
@UtilityClass
public class WorldCaptureCommand {

    private static final SimpleCommandExceptionType NO_RECORDING = new SimpleCommandExceptionType(
            Component.translatable("command.worldcapture.save.norecording"));
    private static final DynamicCommandExceptionType SAVE_FAILED = new DynamicCommandExceptionType(
            arg -> Component.translatable("command.worldcapture.save.failed", arg));

    public static void register(@NonNull CommandDispatcher<FabricClientCommandSource> dispatcher,
                                 @NonNull CommandBuildContext buildContext) {

        dispatcher.register(literal("worldcapture").then(
                literal("start").then(
                        argument("radius", IntegerArgumentType.integer()).executes(WorldCaptureCommand::start)
                )
        ).then(
                literal("save").executes(WorldCaptureCommand::save)
        ));
    }

    private static int start(CommandContext<FabricClientCommandSource> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        SectionPos center = SectionPos.of(context.getSource().getPosition());
        ChunkSectionBox bounds = ChunkSectionBox.fromRadius(center, radius);
        ReplayExportSettings settings = ReplayExportSettings.builder()
                .worldBounds(bounds)
                .build();

        IgriumsReplayExporter.getInstance().startRecording(context.getSource().getLevel(), settings);

        context.getSource().sendFeedback(Component.literal("Capturing " + bounds.count() + " sections..."));
        return 1;
    }

    private static int save(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        if (IgriumsReplayExporter.getInstance().getActiveRecording() == null) {
            throw NO_RECORDING.create();
        }

        context.getSource().sendFeedback(Component.literal("Saving recording..."));
        IgriumsReplayExporter.getInstance().saveRecording().thenRun(() -> {
            context.getSource().sendFeedback(Component.literal("Saved recording. Check console for details."));
        }).exceptionally(e -> {
            context.getSource().sendError(Component.translatable("command.worldcapture.save.failed", e.getMessage()));
            return null;
        });

        return 1;
    }
}