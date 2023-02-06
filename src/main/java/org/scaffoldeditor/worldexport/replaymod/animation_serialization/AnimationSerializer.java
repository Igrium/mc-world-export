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
import java.util.Random;
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
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel.RotationProvidingChannel;
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel.ScalarProvidingChannel;
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel.VectorProvidingChannel;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationImpl;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule.CameraPathFrame;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;
import org.scaffoldeditor.worldexport.util.RenderUtils;
import org.scaffoldeditor.worldexport.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;

import net.minecraft.util.math.Vec3d;

public class AnimationSerializer {

    private Logger logger = LogManager.getLogger();

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    

    private DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();

    private final Random random = new Random();

    /**
     * Deserialize a single animation XML file.
     * @param in The file data.
     * @return The parsed animation.
     * @throws IOException If the animation is improperly formatted or there's another IO exception.
     */
    public AbstractCameraAnimation loadAnimation(InputStream in) throws IOException {
        Document doc = quickLoadDocument(in);
        try {
            return loadAnimation(doc.getDocumentElement());
        } catch (XMLParseException e) {
            throw new IOException("The animation was unable to load due to an XML error.", e);
        }
    }

    /**
     * Deserialize an <code>animations.xml</code> file.
     * @param in The file data.
     * @return The parsed animations and their IDs.
     * @throws IOException If an unrecoverable exception is thrown while parsing the file.
     */
    public BiMap<Integer, AbstractCameraAnimation> loadAnimations(InputStream in) throws IOException {
        Document doc = quickLoadDocument(in);

        BiMap<Integer, AbstractCameraAnimation> anims = HashBiMap.create();
        for (Element element : XMLUtils.getChildrenByTagName(doc.getDocumentElement(), "animation")) {
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

    private Document quickLoadDocument(InputStream in) throws IOException {
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
        return doc;
    }

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
        if (!element.getTagName().equals("animation")) {
            throw new XMLParseException("Cannot load a "+element.getTagName()+" tag as an animation.");
        }

        float fps = Float.parseFloat(element.getAttribute("fps"));

        VectorProvidingChannel<?> posChannel = null;
        RotationProvidingChannel<?> rotChannel = null;
        ScalarProvidingChannel<?> fovChannel = null;

        List<AnimationChannel<?>> channels = new ArrayList<>();

        for (Element channelElement : XMLUtils.getChildrenByTagName(element, "channel")) {
            String name = channelElement.getAttribute("name");
            AnimationChannel<?> channel = channelTypeRegistry.get(name);
            if (channel == null) {
                throw new XMLParseException("Unknown channel name: "+name);
            }

            if (positionChannels.contains(channel)) {
                if (posChannel != null) {
                    throw new XMLParseException(String.format("Two position channels were specified: '%s' and '$s'.",
                            channelTypeRegistry.inverse().get(posChannel), name));
                }
                posChannel = (VectorProvidingChannel<?>) channel;
                channels.add(channel);

            } else if (rotationChannels.contains(channel)) {
                if (rotChannel != null) {
                    throw new XMLParseException(String.format("Two rotation channels were specified: '%s' and '$s'.",
                            channelTypeRegistry.inverse().get(rotChannel), name));
                }
                rotChannel = (RotationProvidingChannel<?>) channel;
                channels.add(channel);

            } else if (fovChannels.contains(channel)) {
                if (fovChannel != null) {
                    throw new XMLParseException(String.format("Two FOV channels were specified: '%s' and '$s'.",
                            channelTypeRegistry.inverse().get(fovChannel), name));
                }
                fovChannel = (ScalarProvidingChannel<?>) channel;
                channels.add(channel);
            } else {
                throw new IllegalStateException("Channel name '" + name +"' was not registered with a channel type!");
            }
        }

        Element animData = XMLUtils.getFirstElementWithName(element, "anim_data");
        if (animData == null) {
            throw new XMLParseException("Animation is missing an anim_data node!");
        }
        

        List<String> lines = animData.getTextContent().lines().filter(s -> !s.isBlank()).toList();
        int length = lines.size();

        Vec3d[] positions = new Vec3d[length];
        Rotation[] rotations = new Rotation[length];
        double[] fovs = new double[length];

        int valuesPerLine = channels.stream().mapToInt(AnimationChannel::numValues).sum();
        // int valuesPerLine = 0;
        // for (AnimationChannel<?> channel : channels) {
        //     valuesPerLine += channel.numValues();
        // }
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
                    fovs[i] = fovChannel.read(myValues).doubleValue();
                } else {
                    throw new IllegalStateException("I honestly have no idea how this happened.");
                }
            }
        }

        CameraAnimationImpl anim = new CameraAnimationImpl(fps, positions, rotations, fovs);

        // IDs are only used internally; not in imported files.
        if (element.hasAttribute("id")) anim.setId(Integer.parseInt(element.getAttribute("id")));
        if (element.hasAttribute("name")) anim.setName(element.getAttribute("name"));
        if (element.hasAttribute("offset")) {
            try {
                anim.setOffset(XMLUtils.parseVector(element.getAttribute("offset")));
            } catch (IllegalArgumentException e) {
                throw new XMLParseException(e, "Unable to parse animation offset: " + e.getMessage());
            }
        }
        parseColor(anim, element);
        return anim;
    }

    public static RotationProvidingChannel<?> getPreferredRotChannel(Rotation value) {
        return value.prefersEuler() ? AnimationChannels.ROTATION_EULER : AnimationChannels.ROTATION_QUAT;
    }

    private void parseColor(AbstractCameraAnimation animation, Element element) throws XMLParseException {
        String colorHex = element.getAttribute("preview_color");
        if (!colorHex.isEmpty()) {
            try {
                int colorInt = RenderUtils.stripAlpha((int) Long.parseLong(colorHex, 16));
                animation.setColor(RenderUtils.argbToColor(colorInt, new Color()));
            } catch (NumberFormatException e) {
                LogManager.getLogger().error("Illegal color hex: "+colorHex, e);
                animation.setColor(randomColor());
            }
        } else {
            animation.setColor(randomColor());
        }
    }

    private Color randomColor() {
        return RenderUtils.hsvToColor(random.nextFloat(), .6f, 1f, new Color());
    }

    /**
     * Serialize a camera animation into XML.
     * @param animation The animation to serialize.
     * @param dom The dom to use to create the element.
     * @return The animation as XML.
     */
    public Element writeAnimation(AbstractCameraAnimation animation, Document dom) {
        Element element = dom.createElement("animation");
        element.setAttribute("fps", String.valueOf(animation.getFps()));
        element.setAttribute("id", String.valueOf(animation.getId()));
        element.setAttribute("name", animation.getName());
        element.setAttribute("offset", XMLUtils.writeVector(animation.getOffset()));
        element.setAttribute("preview_color", Integer.toHexString(RenderUtils.colorToARGB(animation.getColor())));

        if (animation.isEmpty()) {
            element.appendChild(dom.createElement("anim_data"));
            return element;
        }

        // AnimationChannel<Vec3d> positionChannel = AnimationChannels.LOCATION;
        // String rotationChannelName = getPreferredRotChannel(animation.getRotation(0));
        // AnimationChannel<Rotation> rotationChannel = AnimationChannel.castChannel(channelTypeRegistry.get(rotationChannelName), Rotation.class);
        // AnimationChannel<Float> fovChannel = AnimationChannels.FOV;

        VectorProvidingChannel<?> positionChannel = AnimationChannels.LOCATION;
        RotationProvidingChannel<?> rotationChannel = getPreferredRotChannel(animation.getRotation(0));
        ScalarProvidingChannel<?> fovChannel = AnimationChannels.FOV;

        Element positionTag = dom.createElement("channel");
        positionTag.setAttribute("name", AnimationChannels.getId(positionChannel));
        positionTag.setAttribute("size", String.valueOf(positionChannel.numValues()));
        element.appendChild(positionTag);

        Element rotationTag = dom.createElement("channel");
        rotationTag.setAttribute("name", AnimationChannels.getId(rotationChannel));
        rotationTag.setAttribute("size", String.valueOf(rotationChannel.numValues()));
        element.appendChild(rotationTag);

        Element fovTag = dom.createElement("channel");
        fovTag.setAttribute("name", AnimationChannels.getId(fovChannel));
        fovTag.setAttribute("size", String.valueOf(fovChannel.numValues()));
        element.appendChild(fovTag);

        Element animData = dom.createElement("anim_data");
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
                textContent.append('\n');
            }

        }

        animData.setTextContent(textContent.toString());
        element.appendChild(animData);

        return element;
    }
}
