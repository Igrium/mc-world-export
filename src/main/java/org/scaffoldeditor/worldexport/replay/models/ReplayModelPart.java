package org.scaffoldeditor.worldexport.replay.models;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.scaffoldeditor.worldexport.util.TreeNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;

public class ReplayModelPart implements TreeNode<ReplayModelPart> {
    public final List<ReplayModelPart> children = new ArrayList<>();
    private Obj mesh = Objs.create();
    private String name;

    public ReplayModelPart(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Obj getMesh() {
        return mesh;
    }

    public void setMesh(Obj mesh) {
        this.mesh = mesh;
    }

    @Override
    public Iterator<ReplayModelPart> getChildren() {
        return children.iterator();
    }

    @Override
    public boolean hasChildren() {
        return children.size() > 0;
    }

    public Element serialize(Document dom) {
        Element element = dom.createElement("part");

        // Write mesh
        Writer writer = new StringWriter();
        try {
            ObjWriter.write(mesh, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Element meshNode = dom.createElement("mesh");
        meshNode.appendChild(dom.createTextNode(writer.toString()));
        element.appendChild(meshNode);

        for (ReplayModelPart child : children) {
            element.appendChild(child.serialize(dom));
        }

        element.setAttribute("name", getName());

        return element;
    }
}
