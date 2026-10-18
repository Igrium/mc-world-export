package com.igrium.worldexport.debugger.raw;

import com.igrium.craftui.api.screen.CraftAppScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import org.jspecify.annotations.NonNull;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

@UtilityClass
public class WorldExportCommand  {

    public static void register(@NonNull CommandDispatcher<FabricClientCommandSource> dispatcher,
                         @NonNull CommandBuildContext buildContext) {

        dispatcher.register(literal("worldexport").then(
                literal("start").executes(WorldExportCommand::startExport)
        ));
    }

    private static int startExport(CommandContext<FabricClientCommandSource> context) {
        // Delay so that closing the chat window doesn't close the screen
        Minecraft.getInstance().execute(() -> {
            var screen = new CraftAppScreen<>(new RawExportCraftApp());
            Minecraft.getInstance().setScreenAndShow(screen);
        });
        return 1;
    }
}
