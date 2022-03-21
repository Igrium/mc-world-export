package org.scaffoldeditor.worldexport.replay.models;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scaffoldeditor.worldexport.util.TreeNode;

/**
 * Represents a single bone in an armature.
 */
public class Bone implements TreeNode<Bone> {
    public String name;

    public Bone(String name) {
        this.name = name;
    }

    public Bone(String name, Vector3dc start, Quaterniondc rot) {
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
