package org.scaffoldeditor.worldexport.replay.models;

import java.util.ArrayList;
import java.util.List;

import org.scaffoldeditor.worldexport.util.TreeIterator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Represents a replay model that uses the <code>multipart</code> rig type.
 * @see ReplayModel
 * @see ArmatureReplayModel
 */
public class MultipartReplayModel implements ReplayModel<ReplayModelPart> {

    public final List<ReplayModelPart> bones = new ArrayList<>();

    @Override
    public Iterable<ReplayModelPart> getBones() {
        return () -> new TreeIterator<>(bones.iterator());
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
        
        return element;
    }
    
}
