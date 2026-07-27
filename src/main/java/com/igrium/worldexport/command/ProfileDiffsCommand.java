package com.igrium.worldexport.command;

import com.igrium.worldexport.util.ChunkDiffs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.world.level.chunk.PalettedContainer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ProfileDiffsCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> commandDispatcher, CommandBuildContext commandRegistryAccess) {
        commandDispatcher.register(literal("profilediffs").executes(ProfileDiffsCommand::profileDiffs));
    }

    private static int profileDiffs(CommandContext<FabricClientCommandSource> context) {
        int maxAmount = 10000;
        PalettedContainer<BlockState> first = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        PalettedContainer<BlockState> second = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    first.set(x, y, z, Blocks.STONE.defaultBlockState());
                    second.set(x, y, z, Blocks.OAK_WOOD.defaultBlockState());
                }
            }
        }

        long startTime = Util.getNanos();

        for (int i = 0; i < maxAmount; i++) {
            ChunkDiffs.diff(first, second);
        }

        long time = Util.getNanos() - startTime;

        context.getSource().sendFeedback(Component.literal(
                String.format("Profiled %d diffs in %d ms (%d) nanos", maxAmount, time / 1000000L, time)));

        startTime = Util.getNanos();
        for (int i = 0; i < maxAmount; i++) {
            ChunkDiffs.diff(first, second);
        }

        time = Util.getNanos() - startTime;

        context.getSource().sendFeedback(Component.literal(
                String.format("Profiled %d identical diffs in %d ms (%d) nanos", maxAmount, time / 1000000L, time)));

        return 0;
    }
}
