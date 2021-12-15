package org.scaffoldeditor.worldexport.test;

import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;

public final class ReplayTestCommand {
    private ReplayTestCommand() {};

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



                    return 0;
                }).build();
        
        root.addChild(entity);

        ClientCommandManager.DISPATCHER.getRoot().addChild(root);
    }

    public static void testExport(Entity ent) {
        
    }
}
