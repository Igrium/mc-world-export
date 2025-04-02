package com.igrium.replay_debugger;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.modelmbean.XMLParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.BaseReplayEntity;
import org.scaffoldeditor.worldexport.replay.models.ArmatureReplayModel;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.OverrideChannel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.util.XMLUtils;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import net.minecraft.util.Identifier;

public class ParsedReplayEntity implements BaseReplayEntity {

    private ReplayModel<?> model;
    private String name;
    private float startTime = 0;
    private Identifier minecraftID;
    private float fps = 20;

    private String rawXML;

    private List<Pose<?>> frames = new ArrayList<>();

    @Override
    public void generateMaterials(MaterialConsumer file) {
        // Materials will already be in file.
    }

    @Override
    public ReplayModel<?> getModel() {
        return model;
    }

    public void setModel(ReplayModel<?> model) {
        this.model = model;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public float getStartTime() {
        return startTime;
    }

    public void setStartTime(float startTime) {
        this.startTime = startTime;
    }

    @Override
    public Identifier getMinecraftID() {
        return minecraftID;
    }

    public void setMinecraftID(Identifier minecraftID) {
        this.minecraftID = minecraftID;
    }

    @Override
    public float getFPS() {
        return fps;
    }

    public void setFps(float fps) {
        this.fps = fps;
    }

    @Override
    public List<Pose<?>> getFrames() {
        return frames;
    }

    public String getRawXML() {
        return rawXML;
    }

    /**
     * Load a replay entity from an XML file.
     * @param is Input stream to load from.
     * @return Parsed replay entity.
     * @throws XMLParseException If the entity XML is improperly formatted. 
     * @throws IOException If the input stream cannot be read for any reason.
     */
    public static ParsedReplayEntity load(InputStream is) throws XMLParseException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        
        String rawXML = IOUtils.toString(is, Charset.defaultCharset());

        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        Document doc;
        try {
            doc = db.parse(new InputSource(new StringReader(rawXML)));
        } catch (SAXException e) {
            throw new XMLParseException(e, "Improperly formatted entity XML!");
        }

        Element base = doc.getDocumentElement();
        base.normalize();
        if (!base.getTagName().equals("entity")) throw new XMLParseException("Root element must be an entity tag!");

        ParsedReplayEntity ent = load(base);
        ent.rawXML = rawXML;
        return ent;
    }
    
    /**
     * Load a replay entity from an XML element.
     * @param xml <code>entity</code> XML element to load.
     * @return Parsed replay entity.
     * @throws XMLParseException If the entity XML is improperly formatted.
     */
    public static ParsedReplayEntity load(Element xml) throws XMLParseException {
        ParsedReplayEntity entity = new ParsedReplayEntity();

        String name = xml.getAttribute("name");
        if (name.length() == 0) throw new XMLParseException("Entity is missing a name!");
        entity.name = name;

        String className = xml.getAttribute("class");
        if (className.length() > 0) {
            entity.minecraftID = new Identifier(className);
        } else {
            LogManager.getLogger().warn("Entity: {} is missing a class name!", name);
        }

        List<Element> models = XMLUtils.getChildrenByTagName(xml, "model");
        if (models.size() != 1) throw new XMLParseException("Entity:"+name+" has "+models.size()+" models!");
        Element model = models.get(0);

        String rigType = model.getAttribute("rig-type");
        if (rigType.equals("multipart")) {
            entity.model = MultipartReplayModel.parse(model);

        } else if (rigType.length() == 0 || rigType.equals("armature")) {
            // TODO: parse armature.
            entity.model = new ArmatureReplayModel();

        } else {
            throw new XMLParseException("Unknown rig type: "+rigType);
        }

        // Load anim
        List<Element> anims = XMLUtils.getChildrenByTagName(xml, "anim");
        if (anims.size() != 1) throw new XMLParseException("Entity: "+name+" has "+anims.size()+" anim tags!");
        Element anim = anims.get(0);

        String fps = anim.getAttribute("fps");
        if (fps.length() > 0) {
            try {
                entity.fps = Float.valueOf(fps);
            } catch (NumberFormatException e) {
                throw new XMLParseException(e, "Error parsing animation frame rate for entity: "+name);
            }
        }

        String startTime = anim.getAttribute("start-time");
        if (startTime.length() > 0) {
            try {
                entity.startTime = Float.valueOf(startTime);
            } catch (NumberFormatException e) {
                throw new XMLParseException(e, "Error parsing start time for entity: "+name);
            }
        }

        String animString = anim.getTextContent();
        Map<Object, Transform> previous = new HashMap<>();
        for (String line : animString.split("\\r?\\n|\\r")) {
            entity.frames.add(parseFrame(line, entity.model, previous));
        }

        return entity;
    }

    private static <T> Pose<T> parseFrame(String frame, ReplayModel<T> model, Map<Object, Transform> previous) throws XMLParseException {
        Pose<T> pose = new Pose<>();
        List<T> bones = new ArrayList<>();
        model.getBones().forEach(bones::add);

        List<OverrideChannel> overrideChannels = new ArrayList<>();
        model.getOverrideChannels().forEach(overrideChannels::add);

        int frameSize = bones.size() + overrideChannels.size() + 1;

        frame = frame.strip();
        String[] frameParts = frame.split(";");
        if (frameParts.length != frameSize) {
//            throw new XMLParseException("Frame has an incorrect number of bones. Expected: "+frameSize+". Actual: "+frameParts.length);
            LoggerFactory.getLogger(ParsedReplayEntity.class).warn("Frame has an incorrect number of bones. Expected: {}; actual: {}.", frameSize, frameParts.length);
        }

        for (int i = 0; i < frameParts.length; i++) {
            String transform = frameParts[i];
            transform = transform.strip();
            
            String[] parts = transform.split(" ");

            T bone = null;
            Transform prev;

            // Check for override channel
            if (i >= bones.size()) {
                continue;
            }
            if (i != 0) {
                bone = bones.get(i - 1);
                prev = previous.containsKey(bone) ? previous.get(bone) : Transform.NEUTRAL;
            } else {
                prev = Transform.NEUTRAL;
            }

            Quaterniondc rotation = prev.rotation;
            Vector3dc translation = prev.translation;
            Vector3dc scale = prev.scale;
            boolean visible = prev.visible;

            try {
                if (parts.length >= 4) {
                    rotation = new Quaterniond(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                    );
                }
    
                if (parts.length >= 7) {
                    translation = new Vector3d(
                        Double.parseDouble(parts[4]),
                        Double.parseDouble(parts[5]),
                        Double.parseDouble(parts[6])
                    );
                }
    
                if (parts.length >= 10) {
                    scale = new Vector3d(
                        Double.parseDouble(parts[7]),
                        Double.parseDouble(parts[8]),
                        Double.parseDouble(parts[9])
                    );
                }
    
                if (parts.length > 10) {
                    visible = (Integer.parseInt(parts[10]) == 1);
                }
            } catch (NumberFormatException e) {
                throw new XMLParseException(e, "Unable to parse parse animation frame.");
            }
            

            Transform t = new Transform(translation, rotation, scale, visible);
            if (i == 0) {
                pose.root = t;
            } else {
                previous.put(bone, t);
                pose.bones.put(bone, t);
            }
        }

        return pose;
    }

    @Override
    public String toString() {
        return getName();
    }
}
