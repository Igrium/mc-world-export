package org.scaffoldeditor.worldexport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import org.apache.logging.log4j.LogManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;

@Environment(EnvType.CLIENT)
public final class ExportCommand {
    private ExportCommand() {};

    public static void register() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("exportworld")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .executes(context -> {
                try {
                    Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                    if (!exportFolder.toFile().isDirectory()) {
                        exportFolder.toFile().mkdir();
                    }

                    File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".dat").toFile();
                    FileOutputStream os = new FileOutputStream(targetFile);
                    Exporter.writeStill(client.world, new ChunkPos(-2, -2), new ChunkPos(2, 2), new ExportContext(), os);
                    os.close();
                    context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                } catch (IOException e) {
                    LogManager.getLogger().error(e);
                    throw new SimpleCommandExceptionType(new LiteralText("Unable to export world. See console for details.")).create();
                }

                return 0;
            })));
    }
}
