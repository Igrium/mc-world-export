package com.igrium.worldexport.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;

public class WorldCaptureCommand
{
    public static void register(CommandDispatcher<FabricClientCommandSource> commandDispatcher, CommandRegistryAccess commandRegistryAccess) {
//        commandDispatcher.register(literal("worldcapture").then(
//                literal("profile").executes(WorldCaptureCommand::worldCaptureProfile)
//        ));
    }

}
