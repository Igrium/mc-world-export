package com.igrium.worldexport.v1.mesh.obj;

import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjGroup;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class FastQuadObjGroup implements ObjGroup {

    private final String name;

    private final List<ObjFace> faces = new ArrayList<>();

    public FastQuadObjGroup(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getNumFaces() {
        return faces.size();
    }

    @Override
    public ObjFace getFace(int index) {
        return faces.get(index);
    }

    void addFace(ObjFace face) {
        faces.add(face);
    }
}
