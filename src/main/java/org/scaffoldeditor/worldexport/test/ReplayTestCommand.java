package org.scaffoldeditor.worldexport.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replay.ReplayEntity;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.ReplayIO;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter.ModelNotFoundException;

import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;

public final class ReplayTestCommand {
    private ReplayTestCommand() {};
    private static MinecraftClient client = MinecraftClient.getInstance();

    public static void register() {
        LiteralCommandNode<FabricClientCommandSource> root = ClientCommandManager.literal("replaytest").build();

        /**
         * Saves the nearest entity (excluding the player) to a single-frame replay file.
         */
        LiteralCommandNode<FabricClientCommandSource> entity = ClientCommandManager.literal("entity")
                .executes(context -> {
                    ClientPlayerEntity player = context.getSource().getPlayer();
                    double distance = -1;
                    Entity closest = null;
                    for (Entity ent : context.getSource().getWorld().getEntities()) {
                        if (ent.getId() == player.getId()) continue;
                        double dist = ent.getPos().squaredDistanceTo(player.getPos());
                        if (distance < 0 || dist < distance) {
                            distance = dist;
                            closest = ent;
                        }
                    }
                    
                    if (closest == null) {
                        throw new CommandException(new LiteralText("No entity found."));
                    }

                    testExport(closest, context.getSource());

                    return 0;
                }).build();
        
        root.addChild(entity);

        LiteralCommandNode<FabricClientCommandSource> world = ClientCommandManager.literal("world")
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int radius = context.getArgument("radius", Integer.class);

                            ClientPlayerEntity player = context.getSource().getPlayer();
                            double distance = -1;
                            Entity closest = null;
                            for (Entity ent : context.getSource().getWorld().getEntities()) {
                                if (ent.getId() == player.getId()) continue;
                                double dist = ent.getPos().squaredDistanceTo(player.getPos());
                                if (distance < 0 || dist < distance) {
                                    distance = dist;
                                    closest = ent;
                                }
                            }
                    
                            if (closest == null) {
                                throw new CommandException(new LiteralText("No entity found."));
                            }
                            ChunkPos playerPos = player.getChunkPos();

                            testWorldExport(closest, context.getSource(),
                                    new ChunkPos(playerPos.x - radius, playerPos.z - radius),
                                    new ChunkPos(playerPos.x + radius, playerPos.z + radius));

                            return 1;
                        }))
                .build();
        
        root.addChild(world);
        ClientCommandManager.DISPATCHER.getRoot().addChild(root);
    }

    public static void testExport(Entity ent, FabricClientCommandSource source) {
        ReplayFile file = new ReplayFile(source.getWorld(), new ChunkPos(0, 0), new ChunkPos(0, 0));
        ReplayEntity<Entity> rEntity = new ReplayEntity<>(ent, file);
        try {
            rEntity.genAdapter();
        } catch (ModelNotFoundException e1) {
            throw new CommandException(new LiteralText(e1.getMessage()));
        }
        rEntity.capture(0);
        rEntity.capture(.3f);

        File target = client.runDirectory.toPath().resolve("export/ent_test.xml").normalize().toFile();
        try {
            FileWriter writer = new FileWriter(target);
            ReplayIO.serializeEntity(rEntity, writer);
            source.sendFeedback(new LiteralText("Wrote to "+target));
        } catch (IOException e) {
            LogManager.getLogger().error(e);
            throw new CommandException(new LiteralText("Error saving XML: "+e.getMessage()));
        }
    }

    public static void testWorldExport(Entity ent, FabricClientCommandSource source, ChunkPos min, ChunkPos max) {
        ReplayFile file = new ReplayFile(source.getWorld(), min, max);

        ReplayEntity<Entity> rEntity = new ReplayEntity<>(ent, file);
        try {
            rEntity.genAdapter();
        } catch (ModelNotFoundException e1) {
            throw new CommandException(new LiteralText(e1.getMessage()));
        }
        rEntity.capture(0);
        file.entities.add(rEntity);
        file.getWorldExporter().captureIFrame(0);

        File target = client.runDirectory.toPath().resolve("export/test.replay").normalize().toFile();
        try {
            FileOutputStream os = new FileOutputStream(target);
            file.saveAsync(os).whenComplete((val, e) -> {
                if (e != null) {
                    source.sendError(new LiteralText("Failed to save replay: "+e.getMessage()));
                    LogManager.getLogger().error("Failed to save replay.", e);
                } else {
                    source.sendFeedback(new LiteralText("Saved to "+target));
                }
            });
        } catch (FileNotFoundException e) {
            throw new CommandException(new LiteralText(e.getMessage()));
        }
    }
}
