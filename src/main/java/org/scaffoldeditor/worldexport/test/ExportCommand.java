package org.scaffoldeditor.worldexport.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.scaffoldeditor.worldexport.mat.TextureExtractor;
import org.scaffoldeditor.worldexport.vcap.VcapExporter;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class ExportCommand {
    private ExportCommand() {
    };

    static MinecraftClient client = MinecraftClient.getInstance();

    protected static class ExportContext {
        public final VcapExporter exporter;
        public final World world;
        public Date startTime;
        public boolean autoCapture = false;
        private boolean captureQueued = false;

        private Set<BlockPos> updates = new HashSet<>();

        public ExportContext(VcapExporter exporter, World world) {
            this.exporter = exporter;
            this.world = world;
        }

        public void onBlockUpdate(BlockPos pos, BlockState state) {
            updates.add(pos);
            if (autoCapture && !captureQueued) {
                RenderSystem.recordRenderCall(() -> {
                    captureFrame(); // Seperate render call merges simultaneous updates into one frame.
                    captureQueued = false;
                });
                captureQueued = true;
            }
        }

        public void captureFrame() {
            exporter.capturePFrame(
                    (new Date().getTime() - startTime.getTime()) / 1000d, updates);
            updates.clear();
        }

    }

    protected static ExportContext currentExport;
    private static Set<BiConsumer<BlockPos, BlockState>> worldListeners = new HashSet<>();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        LiteralCommandNode<FabricClientCommandSource> root = ClientCommandManager.literal("vcap").build();

        ClientBlockPlaceCallback.EVENT.register((pos, old, state, world) -> {
            worldListeners.forEach(listener -> listener.accept(pos, state));
        });

        LiteralCommandNode<FabricClientCommandSource> start = ClientCommandManager.literal("start")
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            if (currentExport != null) {
                                throw new CommandException(Text.of(
                                        "A Vcap capture is already in process. Use 'vcap save [name]' to stop it."));
                            }
                            ChunkPos playerPos = context.getSource().getPlayer().getChunkPos();
                            int radius = context.getArgument("radius", Integer.class);
                            ClientWorld world = context.getSource().getWorld();

                            VcapExporter exporter = new VcapExporter(context.getSource().getWorld(),
                                    new ChunkPos(playerPos.x - radius, playerPos.z - radius),
                                    new ChunkPos(playerPos.x + radius, playerPos.z + radius));
                            currentExport = new ExportContext(exporter, world);

                            exporter.captureIFrame(0);
                            currentExport.startTime = new Date();
                            worldListeners.add(currentExport::onBlockUpdate);

                            context.getSource().sendFeedback(Text.of("Started Vcap capture..."));
                            return 0;
                        }))
                .build();
        root.addChild(start);

        LiteralCommandNode<FabricClientCommandSource> frame = ClientCommandManager.literal("frame")
                .executes(context -> {
                    if (currentExport == null) {
                        throw new CommandException(
                                Text.of("No Vcap recording active! Start one with 'vcap start'"));
                    }
                    currentExport.captureFrame();
                    context.getSource().sendFeedback(Text.of("Captured predicted frame."));
                    return 0;
                }).build();

        root.addChild(frame);

        LiteralCommandNode<FabricClientCommandSource> listen = ClientCommandManager.literal("listen")
                .then(ClientCommandManager.argument("shouldListen", BoolArgumentType.bool())
                        .executes(context -> {
                            if (currentExport == null) {
                                throw new CommandException(
                                        Text.of("No Vcap recording active! Start one with 'vcap start'"));
                            }

                            boolean mode = context.getArgument("shouldListen", Boolean.class);
                            currentExport.autoCapture = mode;
                            if (mode) {
                                context.getSource().sendFeedback(Text.of("Listening for block updates..."));
                            } else {
                                context.getSource()
                                        .sendFeedback(Text.of("Stopped listening for block updates."));
                            }
                            return 0;
                        }))
                .build();
        root.addChild(listen);

        LiteralCommandNode<FabricClientCommandSource> save = ClientCommandManager.literal("save")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            if (currentExport == null) {
                                throw new CommandException(
                                        Text.of("No Vcap recording active! Start one with 'vcap start'"));
                            }

                            Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                            if (!exportFolder.toFile().isDirectory()) {
                                exportFolder.toFile().mkdir();
                            }

                            context.getSource().sendFeedback(Text.of("Please wait..."));
                            File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".vcap")
                                    .toFile();

                            try {
                                FileOutputStream os = new FileOutputStream(targetFile);
                                currentExport.exporter.save(os);
                                
                            } catch (IOException e) {
                                throw new CommandException(
                                    Text.of("Unable to save file. " + e.getLocalizedMessage()));
                            } catch (Exception e) {
                                throw new CommandException(
                                    Text.of("Error generating Vcap." + e.getLocalizedMessage()));
                            }
                            return 0;
                        }))
                .build();
        root.addChild(save);

        LiteralCommandNode<FabricClientCommandSource> abandon = ClientCommandManager.literal("abandon")
                .executes(context -> {
                    if (currentExport == null) {
                        throw new CommandException(Text.of("No Vcap recording active!"));
                    }
                    currentExport = null;
                    return 0;
                }).build();
        root.addChild(abandon);

        LiteralCommandNode<FabricClientCommandSource> atlas = ClientCommandManager.literal("atlas")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
                            if (!exportFolder.toFile().isDirectory()) {
                                exportFolder.toFile().mkdir();
                            }
                            File targetFile = exportFolder.resolve(context.getArgument("name", String.class) + ".png")
                                    .toFile();

                            new Thread(() -> {
                                NativeImage image;
                                LogManager.getLogger().info("Obtaining atlas texture...");
                                image = TextureExtractor.getAtlas();

                                try {
                                    image.writeTo(targetFile);
                                } catch (IOException e) {
                                    LogManager.getLogger().error(e);
                                    context.getSource()
                                            .sendError(Text.of("Unable to save image. " + e.getMessage()));
                                    return;
                                }
                                context.getSource().sendFeedback(Text.of("Wrote to " + targetFile));
                            }, "World Export").start();

                            return 0;
                        }))
                .build();
        root.addChild(atlas);

        LiteralCommandNode<FabricClientCommandSource> full = ClientCommandManager.literal("full")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(0, 16))
                                .executes(context -> {
                                    exportFull(context);
                                    return 0;
                                })))
                .build();
        root.addChild(full);
        dispatcher.getRoot().addChild(root);
    }

    private static void exportFull(CommandContext<FabricClientCommandSource> context) {
        Path exportFolder = client.runDirectory.toPath().resolve("export").normalize();
        if (!exportFolder.toFile().isDirectory()) {
            exportFolder.toFile().mkdir();
        }
        context.getSource().sendFeedback(Text.of("Please wait..."));
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
                    context.getSource().sendError(Text.of("Failed to save vcap. " + e.getMessage()));
                    LogManager.getLogger().error("Failed to save vcap.", e);
                } else {
                    context.getSource().sendFeedback(Text.of("Wrote to " + targetFile));
                }
            });
        } catch (FileNotFoundException e) {
            throw new CommandException(Text.of("Unable to load file: " + e.getLocalizedMessage()));
        }
    }
}
