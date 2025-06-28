package com.igrium.worldexport.command.test;

import com.igrium.worldexport.IgriumsReplayExporter;
import com.igrium.worldexport.world.WorldCapture;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WorldCaptureCommand
{
    public static void register(CommandDispatcher<FabricClientCommandSource> commandDispatcher, CommandRegistryAccess commandRegistryAccess) {
        commandDispatcher.register(literal("worldcapture").then(
                literal("profile").executes(WorldCaptureCommand::worldCaptureProfile)
        ));
    }

    private static int worldCaptureProfile(CommandContext<FabricClientCommandSource> context) {
        WorldCapture worldCapture = new WorldCapture(context.getSource().getWorld());
        IgriumsReplayExporter.getInstance().setCurrentWorldCapture(worldCapture);

        long startTime = Util.getMeasuringTimeMs();
        int halfWidth = 20;


        ChunkPos playerPos = context.getSource().getPlayer().getChunkPos();
        worldCapture.captureAllChunks(new ChunkPos(playerPos.x - halfWidth, playerPos.z - halfWidth), new ChunkPos(playerPos.x + halfWidth, playerPos.z + halfWidth));

        int numSections = halfWidth * halfWidth * worldCapture.getWorld().countVerticalSections();

        context.getSource().sendFeedback(Text.literal("Captured %d chunks in %d milliseconds".formatted(numSections, Util.getMeasuringTimeMs() - startTime)));

        return 0;
    }
}
