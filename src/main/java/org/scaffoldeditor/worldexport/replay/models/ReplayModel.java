package org.scaffoldeditor.worldexport.replay.models;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scaffoldeditor.worldexport.util.TreeIterator;
import org.scaffoldeditor.worldexport.util.TreeNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;

/**
 * <p>
 * Unlike block models, Minecraft does not have a universal system for entity
 * models. Instead, each entity writes it's vertex data directly into GPU
 * buffers each frame with no abstraction, meaning there is no viable way to
 * dynamically generate entity meshes.
 * </p>
 * <p>
 * This is an intermediary class which contains entity meshes that can be
 * exported. Generators must be written on a per-class basis.
 * </p>
 */
public class ReplayModel {
    
    /**
     * A single bone in the armature.
     */
    public static class Bone implements TreeNode<Bone> {
        public String name;

        public Bone(String name) {
            this.name = name;
        }

        public Bone(String name, Vector3dc start, Quaterniond rot) {
            this.name = name;
            this.pos = start;
            this.rot = rot;
        }

        /**
         * Position of the bone.
         */
        public Vector3dc pos = new Vector3d();
        
        /**
         * Rotation of the bone.
         */
        public Quaterniondc rot = new Quaterniond();

        /**
         * Length of the bone.
         */
        public float length = .16f;

        /**
         * The children of this bone.
         */
        public final List<Bone> children = new ArrayList<>();

        @Override
        public Iterator<Bone> getChildren() {
            return children.iterator();
        }

        @Override
        public boolean hasChildren() {
            return children.size() > 0;
        }
    }

    public static class BoneTransform {
        public final Vector3dc translation;
        public final Quaterniondc rotation;
        public final Vector3dc scale;

        public BoneTransform(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
        }
    }

    public static class Pose {
        public Vector3dc pos;
        public Quaterniondc rot;
        public Vector3dc scale;

        public final Map<Bone, BoneTransform> bones = new HashMap<>();
    }

    /**
     * Base mesh data. Face groups to be interpreted as bone weights.
     */
    public final Obj mesh;

    /**
     * All the bones in this model.
     */
    public final List<Bone> bones = new ArrayList<>();

    public ReplayModel(Obj mesh) {
        this.mesh = mesh;
    }

    /**
     * Get an iterable view of all the bones in this model and their children.
     * 
     * @return An iterable that iterates over the bones in "definition order" (the
     *         order in which they are defined in animation frames.)
     */
    public Iterable<Bone> getBones() {
        return () -> new TreeIterator<>(bones.iterator());
    }

    /**
     * Create a replay model with an empty mesh.
     */
    public ReplayModel() {
        this.mesh = Objs.create();
    }

    /**
     * Save an entity mesh into XML.
     * @param model Model to save.
     * @param dom Document to write into.
     * @return Serialized element.
     */
    public static Element serialize(ReplayModel model, Document dom) {
        Element element = dom.createElement("model");
        for (Bone bone : model.bones) {
            element.appendChild(serializeBone(bone, dom));
        }
        Writer writer = new StringWriter();
        try {
            ObjWriter.write(model.mesh, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Element meshNode = dom.createElement("mesh");
        meshNode.appendChild(dom.createTextNode(writer.toString()));
        element.appendChild(meshNode);
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
}
