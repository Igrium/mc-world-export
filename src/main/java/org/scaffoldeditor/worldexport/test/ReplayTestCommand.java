package org.scaffoldeditor.worldexport.test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.mojang.brigadier.tree.LiteralCommandNode;

import org.scaffoldeditor.worldexport.replay.ReplayEntity;
import org.scaffoldeditor.worldexport.replay.ReplayFile;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelAdapter.ModelNotFoundException;
import org.w3c.dom.Document;

import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;

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

        ClientCommandManager.DISPATCHER.getRoot().addChild(root);
    }

    public static void testExport(Entity ent, FabricClientCommandSource source) {
        ReplayFile file = new ReplayFile(source.getWorld());
        ReplayEntity<Entity> rEntity = new ReplayEntity<>(ent, file);
        try {
            rEntity.genAdapter();
        } catch (ModelNotFoundException e1) {
            throw new CommandException(new LiteralText(e1.getMessage()));
        }
        rEntity.capture(0);
        rEntity.capture(.3f);

        DocumentBuilderFactory dFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new CommandException(new LiteralText("Error building XML document: "+e.getMessage()));
        }

        Document doc = dBuilder.newDocument();
        doc.appendChild(ReplayEntity.writeToXML(rEntity, doc));

        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            DOMSource dSource = new DOMSource(doc);

            File target = client.runDirectory.toPath().resolve("export/ent_test.xml").normalize().toFile();

            OutputStream out = new BufferedOutputStream(new FileOutputStream(target));
            StreamResult result = new StreamResult(out);

            transformer.transform(dSource, result);

            source.sendFeedback(new LiteralText("Wrote to "+target));
        } catch (TransformerException | FileNotFoundException e) {
            throw new CommandException(new LiteralText("Error saving XML: "+e.getMessage()));
        }
        
    }
}
