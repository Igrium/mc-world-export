package org.scaffoldeditor.worldexport.replay.models;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
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
    public static class Bone {
        public String name;

        public Bone(String name) {
            this.name = name;
        }

        public Bone(String name, Vector3dc start, Vector3dc end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        /**
         * Head of the bone.
         */
        public Vector3dc start = new Vector3d();
        /**
         * Tail of the bone.
         */
        public Vector3dc end = new Vector3d();
        /**
         * Rotation of the bone along its axis, in radians.
         */
        public double rot = 0;

        public Bone parent;

        /**
         * The children of this bone.
         */
        public final List<Bone> children = new ArrayList<>();
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

        public final Map<String, BoneTransform> bones = new HashMap<>();
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

    public ReplayModel() {
        this.mesh = Objs.create();
    }

    /**
     * Save an entity mesh into XML.
     * @param model Model to save.
     * @param dom Document to write into.
     * @return Serialized element.
     * @throws IOException
     */
    public static Element serialize(ReplayModel model, Document dom) throws IOException {
        Element element = dom.createElement("mesh");
        for (Bone bone : model.bones) {
            element.appendChild(serializeBone(bone, dom));
        }
        Writer writer = new StringWriter();
        ObjWriter.write(model.mesh, writer);

        Element meshNode = dom.createElement("mesh");
        meshNode.appendChild(dom.createTextNode(writer.toString()));
        element.appendChild(meshNode);
        return element;
    }

    public static Element serializeBone(Bone bone, Document dom) {
        Element element = dom.createElement("bone");
        element.setAttribute("name", bone.name);
        element.setAttribute("start", writeVectorString(bone.start));
        element.setAttribute("end", writeVectorString(bone.end));
        for (Bone child : bone.children) {
            element.appendChild(serializeBone(child, dom));
        }
        return element;
    }

    private static String writeVectorString(Vector3dc vec) {
        return vec.x()+","+vec.y()+","+vec.z();
    }
}
