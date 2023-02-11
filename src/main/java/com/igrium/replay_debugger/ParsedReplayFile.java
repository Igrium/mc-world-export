package com.igrium.replay_debugger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.management.modelmbean.XMLParseException;

import com.google.gson.JsonParseException;
import com.igrium.replay_debugger.ReplayParseException.ParseStage;
import com.igrium.replay_debugger.ui.ParsingUpdateListener;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.replay.BaseReplayFile;
import org.scaffoldeditor.worldexport.replay.ReplayMeta;

public class ParsedReplayFile extends BaseReplayFile<ParsedReplayEntity> {
    private Set<ParsedReplayEntity> entities = new HashSet<>();
    private Map<String, ImageReplayTexture> textures = new HashMap<>();
    private Map<String, Material> materials = new HashMap<>();
    private ReplayMeta meta;

    private byte[] vcapData = new byte[0];

    public Set<ParsedReplayEntity> getEntities() {
        return entities;
    }

    public Map<String, ImageReplayTexture> getTextures() {
        return textures;
    }

    public Map<String, Material> getMaterials() {
        return materials;
    }

    public ReplayMeta getMeta() {
        return meta;
    }

    public void setMeta(ReplayMeta meta) {
        this.meta = meta;
    }

    @Override
    protected void preserializeEntity(ParsedReplayEntity entity) {        
    }

    @Override
    public void saveWorld(OutputStream out, Consumer<String> phaseConsumer) throws IOException {
        out.write(vcapData);
    }

    public void loadWorld(InputStream in) throws IOException {
        vcapData = in.readAllBytes();
    }

    public static ParsedReplayFile load(File file, ParsingUpdateListener listener) throws IOException, ReplayParseException {
        InputStream in = new FileInputStream(file);
        ParsedReplayFile replay = load(in, listener);
        in.close();
        return replay;
    }
    
    public static ParsedReplayFile load(InputStream is, ParsingUpdateListener listener)
            throws IOException, ReplayParseException {
        ZipInputStream archive = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry entry;
        ParsedReplayFile replay = new ParsedReplayFile();
        
        while ((entry = archive.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            LogManager.getLogger().info("Parsing file: "+entry.getName());
            String filename = entry.getName();
            listener.setInfoText("Parsing file: "+filename);

            if (filename.equals("meta.json")) {
                try {
                    String meta = IOUtils.toString(archive, "UTF-8");
                    replay.meta = ReplayMeta.fromJson(meta);
                } catch (JsonParseException e) {
                    throw new ReplayParseException(ParseStage.GENERAL, "meta.json", e);
                }
            } else if (filename.equals("world.vcap")) {
                replay.loadWorld(archive);
            } else if (filename.startsWith("entities/") && filename.endsWith(".xml")) {
                try {
                    replay.entities.add(ParsedReplayEntity.load(archive));
                } catch (XMLParseException e) {
                    handle(new ReplayParseException(ParseStage.ENTITY, filename, e), listener::handle);
                }
            } else if (filename.startsWith("mat/") && filename.endsWith(".json")) {
                String basename = FilenameUtils.removeExtension(filename);
                basename = StringUtils.removeStart(basename, "mat/");
                try {
                    replay.materials.put(basename, Material.load(archive));
                } catch (JsonParseException e) {
                    handle(new ReplayParseException(ParseStage.MATERIAL, basename, e), listener::handle);
                }
            } else if (filename.startsWith("tex/") && filename.endsWith(".json")) {
                String basename = FilenameUtils.removeExtension(filename);
                basename = StringUtils.removeStart(basename, "tex/");
                try {
                    replay.textures.put(basename, new ImageReplayTexture(archive));
                } catch (IOException e) {
                    handle(new ReplayParseException(ParseStage.TEXTURE, basename, e), listener::handle);
                }
            }
        }

        return replay;
    }

    private static void handle(ReplayParseException e, Consumer<ReplayParseException> exceptionHandler)
            throws ReplayParseException {
        if (e.getCause() != null) {
            e.setStackTrace(e.getCause().getStackTrace());
        }
        if (exceptionHandler != null) {
            exceptionHandler.accept(e);
        } else {
            throw e;
        }
    }
}
