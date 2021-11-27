package org.scaffoldeditor.worldexport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.export.TextureExtractor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;

@Environment(EnvType.CLIENT)
public final class ExportCommand {
    private ExportCommand() {
    };
    static MinecraftClient client = MinecraftClient.getInstance();

    public static void register() {
        LiteralCommandNode<FabricClientCommandSource> root = ClientCommandManager.literal("export").build();

        LiteralCommandNode<FabricClientCommandSource> atlas = ClientCommandManager.literal("atlas")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .executes(context -> {
                Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                if (!exportFolder.toFile().isDirectory()) {
                    exportFolder.toFile().mkdir();
                }
                File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".png").toFile();

                new Thread(() -> {
                    NativeImage image;
                    LogManager.getLogger().info("Obtaining atlas texture...");
                    try {
                        image = TextureExtractor.getAtlas().get();
                    } catch (InterruptedException | ExecutionException e) {
                        LogManager.getLogger().error(e);
                        context.getSource().sendError(new LiteralText("Unable to retrieve atlas. "+e.getMessage()));
                        return;
                    }
    
                    try {
                        image.writeTo(targetFile);
                    } catch (IOException e) {
                        LogManager.getLogger().error(e);
                        context.getSource().sendError(new LiteralText("Unable to save image. "+e.getMessage()));
                        return;
                    }
                    context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                }, "World Export").start();
                
                return 0;
            })).build();
        root.addChild(atlas);

        LiteralCommandNode<FabricClientCommandSource> full = ClientCommandManager.literal("full")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(0, 16))
            .executes(context -> {
                exportFull(context);
                return 0;
            }))).build();
        root.addChild(full);

        ClientCommandManager.DISPATCHER.getRoot().addChild(root);
    }

    private static void exportFull(CommandContext<FabricClientCommandSource> context) {
        Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
        if (!exportFolder.toFile().isDirectory()) {
            exportFolder.toFile().mkdir();
        }
        context.getSource().sendFeedback(new LiteralText("Please wait..."));
        File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".vcap").toFile();
        ChunkPos playerPos = context.getSource().getPlayer().getChunkPos();
        int radius = context.getArgument("radius", Integer.class);

        VcapExporter exporter = new VcapExporter(context.getSource().getWorld(),
                new ChunkPos(playerPos.x - radius, playerPos.z - radius),
                new ChunkPos(playerPos.x + radius, playerPos.z + radius));
        
        exporter.captureIFrame(0);

        try {
            FileOutputStream os = new FileOutputStream(targetFile);
            exporter.saveAsync(os).whenComplete((val, e) -> {
                if (e != null) {
                    context.getSource().sendError(new LiteralText("Failed to save vcap. "+e.getMessage()));
                    LogManager.getLogger().error("Failed to save vcap.", e);
                } else {
                    context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                }
            });
        } catch (FileNotFoundException e) {
            context.getSource().sendError(new LiteralText(e.getMessage()));
            LogManager.getLogger().error("Error saving vcap : "+e);
        }
    }
}
