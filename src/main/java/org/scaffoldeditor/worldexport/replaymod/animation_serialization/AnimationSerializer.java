package org.scaffoldeditor.worldexport.replaymod.animation_serialization;

import static org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannels.channelTypeRegistry;
import static org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannels.fovChannels;
import static org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannels.positionChannels;
import static org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannels.rotationChannels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.management.modelmbean.XMLParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationImpl;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule.CameraPathFrame;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;
import org.scaffoldeditor.worldexport.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import net.minecraft.util.math.Vec3d;

public class AnimationSerializer {
    
    

    public static String getPreferredRotChannel(Rotation value) {
        return "rotation_euler";
    }

    private Logger logger = LogManager.getLogger();

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    

    private DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();

    /**
     * Deserialize an <code>animations.xml</code> file.
     * @param in The file data.
     * @return The parsed animations and their IDs.
     * @throws IOException If an unrecoverable exception is thrown while parsing the file.
     */
    public BiMap<Integer, AbstractCameraAnimation> loadAnimations(InputStream in) throws IOException {
        DocumentBuilder builder;
        try {
            builder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Document builder factory was not configured properly.", e);
        }
        Document doc;
        try {
            doc = builder.parse(in);
        } catch (SAXException e) {
            throw new IOException("The XML document was formatted improperly.", e);
        }

        BiMap<Integer, AbstractCameraAnimation> anims = HashBiMap.create();
        for (Element element : XMLUtils.getChildrenByTagName(doc.getDocumentElement(), "anim")) {
            AbstractCameraAnimation anim;
            try {
                anim = loadAnimation(element);
            } catch (Exception e) {
                logger.error("A camera animation failed to load.", e);
                continue;
            }

            int id = anim.getId();
            if (anims.containsKey(id)) {
                logger.error("Two animations were found with the id " + id + "! One will be discarded.");
                continue;
            }
            anims.put(id, anim);
        }

        return anims;
    };

    /**
     * Serialize a set of camera animations into an <code>animations.xml</code> file.
     * @param anims The animations to serialize.
     * @param out Output stream to write to.
     * @throws IOException If an unrecoverable exception is thrown while writing the file.
     */
    public void writeAnimations(Map<Integer, AbstractCameraAnimation> anims, OutputStream out) throws IOException {
        DocumentBuilder builder;
        try {
            builder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Document builder factory was not configured properly.", e);
        }
        Document doc = builder.newDocument();
        Element root = doc.createElement("animations");
        doc.appendChild(root);
        
        for (AbstractCameraAnimation anim : anims.values()) {
            root.appendChild(writeAnimation(anim, doc));
        }

        Transformer transformer;
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException("Transformer factory was not configured properly.", e);
        }
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(out);

        try {
            transformer.transform(source, result);
        } catch (TransformerException e) {
            throw new IOException("An error occured during document transformation.", e);
        }
    }

    /**
     * Load a camera animation from an XML element.
     * @param element The element to load.
     * @return The deserialized animation.
     * @throws XMLParseException If there's a formatting issue with the XML.
     */
    public AbstractCameraAnimation loadAnimation(Element element) throws XMLParseException {
        if (!element.getTagName().equals("anim")) {
            throw new XMLParseException("Cannot load a "+element.getTagName()+" tag as an animation.");
        }

        float fps = Float.parseFloat(element.getAttribute("fps"));

        AnimationChannel<Vec3d> posChannel = null;
        AnimationChannel<Rotation> rotChannel = null;
        AnimationChannel<Float> fovChannel = null;

        List<AnimationChannel<?>> channels = new ArrayList<>();

        for (Element channelElement : XMLUtils.getChildrenByTagName(element, "channel")) {
            String name = channelElement.getAttribute("name");
            AnimationChannel<?> channel = channelTypeRegistry.get(name);
            if (channel == null) {
                throw new XMLParseException("Unknown channel name: "+name);
            }
            if (positionChannels.contains(name)) {
                if (posChannel != null) {
                    throw new XMLParseException(String.format("Two position channels were specified: '%s' and '$s'.",
                            channelTypeRegistry.inverse().get(posChannel), name));
                }
                posChannel = AnimationChannel.castChannel(posChannel, Vec3d.class);
            } else if (rotationChannels.contains(name)) {
                if (rotChannel != null) {
                    throw new XMLParseException(String.format("Two rotation channels were specified: '%s' and '$s'.",
                            channelTypeRegistry.inverse().get(rotChannel), name));
                }
                rotChannel = AnimationChannel.castChannel(channel, Rotation.class);
            } else if (fovChannels.contains(name)) {
                if (fovChannel != null) {
                    throw new XMLParseException(String.format("Two FOV channels were specified: '%s' and '$s'.",
                            channelTypeRegistry.inverse().get(fovChannel), name));
                }
                fovChannel = AnimationChannel.castChannel(fovChannel, Float.class);
            } else {
                throw new IllegalStateException("Channel name '" + name +"' was not registered with a channel type!");
            }
        }

        Element animData = XMLUtils.getFirstElementWithName(element, "anim_data");
        if (animData == null) {
            throw new XMLParseException("Animation is missing an anim_data node!");
        }
        

        List<String> lines = animData.getTextContent().lines().filter(s -> s.isBlank()).toList();
        int length = lines.size();

        Vec3d[] positions = new Vec3d[length];
        Rotation[] rotations = new Rotation[length];
        float[] fovs = new float[length];

        int valuesPerLine = channels.stream().mapToInt(AnimationChannel::numValues).sum();
        for (int i = 0; i < length; i++) {
            String[] values = lines.get(i).strip().split(" ");
            if (values.length != valuesPerLine) {
                throw new XMLParseException(String.format("Incorrect number of values on animation line %d. (%d != %d)",
                        i, values.length, valuesPerLine));
            }

            int head = 0;
            for (AnimationChannel<?> channel : channels) {
                int numValues = channel.numValues();
                double[] myValues = new double[numValues];
                for (int v = 0; v < numValues; v++) {
                    myValues[v] = Double.valueOf(values[head]);
                    head++;
                }

                if (channel == posChannel) {
                    positions[i] = posChannel.read(myValues);
                } else if (channel == rotChannel) {
                    rotations[i] = rotChannel.read(myValues);
                } else if (channel == fovChannel) {
                    fovs[i] = fovChannel.read(myValues);
                } else {
                    throw new IllegalStateException("I honestly have no idea how this happened.");
                }
            }
        }

        CameraAnimationImpl anim = new CameraAnimationImpl(fps, positions, rotations, fovs);

        // IDs are only used internally; not in imported files.
        if (element.hasAttribute("id")) anim.setId(Integer.parseInt(element.getAttribute("id")));
        return anim;

        // for (int i = 0; i < length; i++) {
        //     String[] values = lines.get(i).strip().split(" ");
        //     if (values.length != 7) {
        //         throw new XMLParseException("Animation line "+i+" has an improper number of values!");
        //     }

        //     positions[i] = new Vec3d(
        //             Double.parseDouble(values[0]),
        //             Double.parseDouble(values[1]),
        //             Double.parseDouble(values[2]));
            
        //     rotations[i] = new Vec3d(
        //             Double.parseDouble(values[3]),
        //             Double.parseDouble(values[4]),
        //             Double.parseDouble(values[5]));
            
        //     fovs[i] = Float.parseFloat(values[6]);
        // }

        // CameraAnimationImpl anim = new CameraAnimationImpl(fps, positions, rotations, fovs);
        // anim.setId(Integer.parseInt(element.getAttribute("id")));
        // return anim;
    }

    /**
     * Serialize a camera animation into XML.
     * @param animation The animation to serialize.
     * @param dom The dom to use to create the element.
     * @return The animation as XML.
     */
    public Element writeAnimation(AbstractCameraAnimation animation, Document dom) {
        Element element = dom.createElement("anim");
        element.setAttribute("fps", String.valueOf(animation.getFps()));
        element.setAttribute("id", String.valueOf(animation.getId()));
        if (animation.isEmpty()) {
            element.appendChild(dom.createElement("anim_data"));
            return element;
        }

        AnimationChannel<Vec3d> positionChannel = AnimationChannels.LOCATION;
        String rotationChannelName = getPreferredRotChannel(animation.getRotation(0));
        AnimationChannel<Rotation> rotationChannel = AnimationChannel.castChannel(positionChannel, Rotation.class);
        AnimationChannel<Float> fovChannel = AnimationChannels.FOV;

        Element positionTag = dom.createElement("channel");
        positionTag.setAttribute("name", positionChannels.iterator().next());
        positionTag.setAttribute("size", String.valueOf(positionChannel.numValues()));
        element.appendChild(positionTag);

        Element rotationTag = dom.createElement("channel");
        rotationTag.setAttribute("name", rotationChannelName);
        rotationTag.setAttribute("size", String.valueOf(rotationChannel.numValues()));
        element.appendChild(rotationTag);

        Element fovTag = dom.createElement("channel");
        fovTag.setAttribute("name", fovChannels.iterator().next());
        fovTag.setAttribute("size", String.valueOf(fovChannel.numValues()));
        element.appendChild(fovTag);

        Element animData = dom.createElement("animData");
        StringBuilder textContent = new StringBuilder();

        Iterator<CameraPathFrame> iterator = animation.iterator();
        while (iterator.hasNext()) {
            CameraPathFrame frame = iterator.next();

            textContent.append(
                    Arrays.stream(positionChannel.write(frame.pos()))
                            .mapToObj(String::valueOf).collect(Collectors.joining(" ")));
            textContent.append(" ");

            textContent.append(
                    Arrays.stream(rotationChannel.write(frame.rot()))
                            .mapToObj(String::valueOf).collect(Collectors.joining(" ")));
            textContent.append(" ");

            textContent.append(
                    Arrays.stream(fovChannel.write(frame.fov()))
                            .mapToObj(String::valueOf).collect(Collectors.joining(" ")));
            
            if (iterator.hasNext()) {
                textContent.append(System.lineSeparator());
            }

        }

        animData.setTextContent(textContent.toString());
        element.appendChild(animData);

        return element;
    }
}