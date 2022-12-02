package org.scaffoldeditor.worldexport.replaymod.camera_paths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

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
import org.scaffoldeditor.worldexport.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

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
        List<String> lines = element.getTextContent().lines().filter(s -> s.isBlank()).toList();
        int length = lines.size();

        Vec3d[] positions = new Vec3d[length];
        Vec3d[] rotations = new Vec3d[length];
        float[] fovs = new float[length];

        for (int i = 0; i < length; i++) {
            String[] values = lines.get(i).strip().split(" ");
            if (values.length != 7) {
                throw new XMLParseException("Animation line "+i+" has an improper number of values!");
            }

            positions[i] = new Vec3d(
                    Double.parseDouble(values[0]),
                    Double.parseDouble(values[1]),
                    Double.parseDouble(values[2]));
            
            rotations[i] = new Vec3d(
                    Double.parseDouble(values[3]),
                    Double.parseDouble(values[4]),
                    Double.parseDouble(values[5]));
            
            fovs[i] = Float.parseFloat(values[6]);
        }

        CameraAnimation anim = new CameraAnimation(fps, positions, rotations, fovs);
        anim.setId(Integer.parseInt(element.getAttribute("id")));
        return anim;
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

        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < animation.size(); i++) {
            Vec3d pos = animation.getPosition(i);
            textContent.append(pos.x + " " + pos.y + " " + pos.z + " "); 

            Vec3d rot = animation.getRotation(i);
            textContent.append(rot.x + " " + rot.y + " " + rot.z + " ");

            float fov = animation.getFov(i);
            textContent.append(fov);

            if (i + 1 < animation.size()) {
                textContent.append(System.lineSeparator());
            }
        }

        element.setTextContent(textContent.toString());
        element.setAttribute("id", String.valueOf(animation.getId()));

        return element;
    }
}
