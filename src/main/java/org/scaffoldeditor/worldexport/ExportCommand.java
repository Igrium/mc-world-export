package org.scaffoldeditor.worldexport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.export.BlockExporter;
import org.scaffoldeditor.worldexport.export.ExportContext;
import org.scaffoldeditor.worldexport.export.ExportContext.ModelEntry;
import org.scaffoldeditor.worldexport.export.MeshWriter;
import org.scaffoldeditor.worldexport.export.TextureExtractor;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;

@Environment(EnvType.CLIENT)
public final class ExportCommand {
    private ExportCommand() {
    };

    public static void register() {
        MinecraftClient client = MinecraftClient.getInstance();
        LiteralCommandNode<FabricClientCommandSource> root = ClientCommandManager.literal("export").build();

        LiteralCommandNode<FabricClientCommandSource> world = ClientCommandManager.literal("world")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .executes(context -> {
                try {
                    Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                    if (!exportFolder.toFile().isDirectory()) {
                        exportFolder.toFile().mkdir();
                    }

                    File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".dat").toFile();
                    FileOutputStream os = new FileOutputStream(targetFile);
                    BlockExporter.writeStill(client.world, new ChunkPos(-2, -2), new ChunkPos(2, 2), new ExportContext(), os);
                    os.close();
                    context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                } catch (IOException e) {
                    LogManager.getLogger().error(e);
                    throw new SimpleCommandExceptionType(new LiteralText("Unable to export world. See console for details.")).create();
                }

                return 0;
            })).build();
        root.addChild(world);
        
        LiteralCommandNode<FabricClientCommandSource> mesh = ClientCommandManager.literal("mesh")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .executes(context -> {
                Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                if (!exportFolder.toFile().isDirectory()) {
                    exportFolder.toFile().mkdir();
                }
                File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".obj").toFile();

                BlockState block = context.getSource().getWorld().getBlockState(context.getSource().getPlayer().getBlockPos().subtract(new Vec3i(0, 1, 0)));
                BlockRenderManager dispatcher = MinecraftClient.getInstance().getBlockRenderManager();
                ModelEntry entry = new ModelEntry(dispatcher.getModel(block), new boolean[] { true, true, true, true, true, true }, !block.isOpaque());
                
                Obj model = MeshWriter.writeBlockMesh(entry, new Random());

                try {
                    FileOutputStream os = new FileOutputStream(targetFile);
                    ObjWriter.write(model, os);
                    os.close();
                } catch (IOException e) {
                    throw new SimpleCommandExceptionType(new LiteralText("Unable to export mesh. See console for details.")).create();
                }

                context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                return 0;
            })).build();
        root.addChild(mesh);

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
                }).start();
                
                return 0;
            })).build();
        root.addChild(atlas);

        LiteralCommandNode<FabricClientCommandSource> full = ClientCommandManager.literal("full")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .executes(context -> {
                new Thread(() -> {
                    Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                    if (!exportFolder.toFile().isDirectory()) {
                        exportFolder.toFile().mkdir();
                    }
                    File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".vcap").toFile();
                    try {
                        FileOutputStream os = new FileOutputStream(targetFile);
                        Exporter.Export(context.getSource().getWorld(), new ChunkPos(-2, -2), new ChunkPos(2, 2), os);
                        os.close();
                    } catch (IOException | ExecutionException | TimeoutException e) {
                        LogManager.getLogger().error(e);
                        context.getSource().sendError(new LiteralText("Unable to export world. "+e.getLocalizedMessage()));
                    }
    
                    context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                }).start();     
                return 0;
            })).build();
        root.addChild(full);

        ClientCommandManager.DISPATCHER.getRoot().addChild(root);
    }
}
