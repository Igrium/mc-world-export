package com.igrium.worldexport.command;

import com.igrium.worldexport.util.ChunkDiffs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.world.chunk.PalettedContainer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ProfileDiffsCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> commandDispatcher, CommandRegistryAccess commandRegistryAccess) {
        commandDispatcher.register(literal("profilediffs").executes(ProfileDiffsCommand::profileDiffs));
    }

    private static int profileDiffs(CommandContext<FabricClientCommandSource> context) {
        int maxAmount = 10000;
        PalettedContainer<BlockState> first = new PalettedContainer<>(Block.STATE_IDS, Blocks.AIR.getDefaultState(), PalettedContainer.PaletteProvider.BLOCK_STATE);
        PalettedContainer<BlockState> second = new PalettedContainer<>(Block.STATE_IDS, Blocks.AIR.getDefaultState(), PalettedContainer.PaletteProvider.BLOCK_STATE);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    first.set(x, y, z, Blocks.STONE.getDefaultState());
                    second.set(x, y, z, Blocks.OAK_WOOD.getDefaultState());
                }
            }
        }

        long startTime = Util.getMeasuringTimeNano();

        for (int i = 0; i < maxAmount; i++) {
            ChunkDiffs.diff(first, second);
        }

        long time = Util.getMeasuringTimeNano() - startTime;

        context.getSource().sendFeedback(Text.literal(
                String.format("Profiled %d diffs in %d ms (%d) nanos", maxAmount, time / 1000000L, time)));

        startTime = Util.getMeasuringTimeNano();
        for (int i = 0; i < maxAmount; i++) {
            ChunkDiffs.diff(first, second);
        }

        time = Util.getMeasuringTimeNano() - startTime;

        context.getSource().sendFeedback(Text.literal(
                String.format("Profiled %d identical diffs in %d ms (%d) nanos", maxAmount, time / 1000000L, time)));

        return 0;
    }
}
