package org.scaffoldeditor.worldexport.replay;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vector4f;

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

        public Bone(String name, Vec3d start, Vec3d end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        /**
         * Head of the bone.
         */
        public Vec3d start = new Vec3d(0, 0, 0);
        /**
         * Tail of the bone.
         */
        public Vec3d end = new Vec3d(0, 0, 0);
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
        public final Vec3d translation;
        public final Vector4f rotation;
        public final Vec3d scale;

        public BoneTransform(Vec3d translation, Vector4f rotation, Vec3d scale) {
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
        }
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

    private static String writeVectorString(Vec3d vec) {
        return vec.x+","+vec.y+","+vec.z;
    }
}
