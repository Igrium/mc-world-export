package org.scaffoldeditor.worldexport.replay.models;

import java.util.ArrayList;
import java.util.List;

import javax.management.modelmbean.XMLParseException;

import org.scaffoldeditor.worldexport.util.TreeIterator;
import org.scaffoldeditor.worldexport.util.XMLUtils;
import org.scaffoldeditor.worldexport.util.XMLUtils.JavaNodeList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Represents a replay model that uses the <code>multipart</code> rig type.
 * @see ReplayModel
 * @see ArmatureReplayModel
 */
public class MultipartReplayModel implements ReplayModel<ReplayModelPart> {

    public final List<ReplayModelPart> bones = new ArrayList<>();
    protected final List<OverrideChannel> overrideChannels = new ArrayList<>();

    @Override
    public Iterable<ReplayModelPart> getBones() {
        return () -> new TreeIterator<>(bones.iterator());
    }

    @Override
    public List<OverrideChannel> getOverrideChannels() {
        return overrideChannels;
    }

    @Override
    public void addOverrideChannel(OverrideChannel channel) {
        overrideChannels.add(channel);
    }

    @Override
    public Transform processCoordinateSpace(ReplayModelPart bone, Transform in) {
        return in;
    }

    @Override
    public Element serialize(Document dom) {
        Element element = dom.createElement("model");
        element.setAttribute("rig-type", "multipart");
        for (ReplayModelPart bone : bones) {
            element.appendChild(bone.serialize(dom));
        }
        for (OverrideChannel channel : overrideChannels) {
            element.appendChild(channel.serialize(dom));
        }

        return element;
    }

    @Override
    public boolean allowVisibility() {
        return true;
    }

    /**
     * Get the model part with a particular name from the model.
     * @param name The name to search for.
     * @return The first model part with that name, or <code>null</code> if none was found.
     */
    public ReplayModelPart getBone(String name) {
        for (ReplayModelPart bone : getBones()) {
            if (bone.getName().equals(name)) return bone;
        }
        return null;
    }

    /**
     * Parse a multipart replay model from XML.
     * @param xml XML model element.
     * @return Parsed model.
     * @throws XMLParseException
     */
    public static MultipartReplayModel parse(Element xml) throws XMLParseException {
        MultipartReplayModel model = new MultipartReplayModel();
        List<Element> parts = XMLUtils.getChildrenByTagName(xml, "part");
        
        for (Element part : parts) {
            model.bones.add(ReplayModelPart.parse(part));
        }

        JavaNodeList overrides = new JavaNodeList(xml.getElementsByTagName("override_channel"));

        for (Node node : overrides) {
            Element element = (Element) node;
            model.overrideChannels.add(OverrideChannel.parse(element));
        }


        return model;
    }
}
