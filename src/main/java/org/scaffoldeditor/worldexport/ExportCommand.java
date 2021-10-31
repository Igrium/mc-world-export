package org.scaffoldeditor.worldexport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.export.ExportContext;
import org.scaffoldeditor.worldexport.export.MeshWriter;
import org.scaffoldeditor.worldexport.export.ExportContext.ModelEntry;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;

@Environment(EnvType.CLIENT)
public final class ExportCommand {
    private ExportCommand() {
    };

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
        
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("exporttest")
            .then(ClientCommandManager.argument("name", StringArgumentType.word())
            .executes(context -> {
                Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                if (!exportFolder.toFile().isDirectory()) {
                    exportFolder.toFile().mkdir();
                }
                File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".obj").toFile();

                BlockState block = context.getSource().getWorld().getBlockState(context.getSource().getPlayer().getBlockPos().subtract(new Vec3i(0, 1, 0)));
                BlockRenderManager dispatcher = MinecraftClient.getInstance().getBlockRenderManager();
                ModelEntry entry = new ModelEntry(dispatcher.getModel(block), new boolean[] { true, true, true, true, true, true });

                Obj mesh = MeshWriter.writeBlockMesh(entry, new Random());

                try {
                    FileOutputStream os = new FileOutputStream(targetFile);
                    ObjWriter.write(mesh, os);
                    os.close();
                } catch (IOException e) {
                    throw new SimpleCommandExceptionType(new LiteralText("Unable to export mesh. See console for details.")).create();
                }

                context.getSource().sendFeedback(new LiteralText("Wrote to "+targetFile));
                return 0;
            })));
    }
}
