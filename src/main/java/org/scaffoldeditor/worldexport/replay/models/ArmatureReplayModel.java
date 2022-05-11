package org.scaffoldeditor.worldexport.replay.models;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scaffoldeditor.worldexport.util.TreeIterator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;

/**
 * Represents a replay model that uses the <code>armature</code> rig type.
 * @see ReplayModel
 * @see MultipartReplayModel
 */
public class ArmatureReplayModel implements ReplayModel<Bone> {


    /**
     * Base mesh data. Face groups to be interpreted as bone weights.
     */
    public final Obj mesh;

    /**
     * All the bones in this model. Write to this list to build the armature.
     */
    public final List<Bone> bones = new ArrayList<>();
    protected final List<OverrideChannel> overrideChannels = new ArrayList<>();

    public ArmatureReplayModel(Obj mesh) {
        this.mesh = mesh;
    }

    public Iterable<Bone> getBones() {
        return () -> new TreeIterator<>(bones.iterator());
    }

    public List<OverrideChannel> getOverrideChannels() {
        return overrideChannels;
    }

    @Override
    public void addOverrideChannel(OverrideChannel channel) {
        overrideChannels.add(channel);
    }

    /**
     * Create a replay model with an empty mesh.
     */
    public ArmatureReplayModel() {
        this.mesh = Objs.create();
    }

    public Element serialize(Document dom) {
        Element element = dom.createElement("model");
        element.setAttribute("rig-type", "armature");
        for (Bone bone : this.bones) {
            element.appendChild(serializeBone(bone, dom));
        }
        Writer writer = new StringWriter();
        try {
            ObjWriter.write(this.mesh, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Element meshNode = dom.createElement("mesh");
        meshNode.appendChild(dom.createTextNode(writer.toString()));
        element.appendChild(meshNode);
        for (OverrideChannel channel : overrideChannels) {
            element.appendChild(channel.serialize(dom));
        }

        return element;
    }

    public static Element serializeBone(Bone bone, Document dom) {
        Element element = dom.createElement("bone");
        element.setAttribute("name", bone.name);
        element.setAttribute("pos", writeVectorString(bone.pos));
        element.setAttribute("rot", writeQuatToString(bone.rot));
        element.setAttribute("len", String.valueOf(bone.length));
        for (Bone child : bone.children) {
            element.appendChild(serializeBone(child, dom));
        }
        return element;
    }

    private static String writeVectorString(Vector3dc vec) {
        return vec.x()+","+vec.y()+","+vec.z();
    }

    private static String writeQuatToString(Quaterniondc quat) {
        return quat.w()+","+quat.x()+","+quat.y()+","+quat.z();
    }

    @Override
    public Transform processCoordinateSpace(Bone bone, Transform in) {
        Quaterniond rotation = bone.rot.difference(in.rotation, new Quaterniond());
        Vector3d position = in.translation.sub(bone.pos, new Vector3d());
        return new Transform(position, rotation, in.scale);
    }

    @Deprecated
    public static Element serialize(ArmatureReplayModel model, Document dom) {
        return model.serialize(dom);
    }
}
