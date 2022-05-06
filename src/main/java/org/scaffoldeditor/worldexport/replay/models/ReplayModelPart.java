package org.scaffoldeditor.worldexport.replay.models;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.modelmbean.XMLParseException;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.util.TreeNode;
import org.scaffoldeditor.worldexport.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;
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

    @Override
    public String toString() {
        return name;
    }

    public static ReplayModelPart parse(Element xml) throws XMLParseException {
        String name = xml.getAttribute("name");
        if (name.length() == 0) {
            throw new XMLParseException("Replay model part is missing a name!");
        }
        ReplayModelPart part = new ReplayModelPart(name);
        List<Element> mesh = XMLUtils.getChildrenByTagName(xml, "mesh");
        if (mesh.size() == 1) {
            String objString = mesh.get(0).getTextContent();
            try {
                part.mesh = ObjReader.read(new StringReader(objString));
            } catch (IOException e) {
                throw new XMLParseException(e, "Improperly formatted OBJ in part "+name);
            }
        } else {
            LogManager.getLogger().error("Model part {} has {} meshes!", name, mesh.size());
            part.mesh = Objs.create();
        }

        List<Element> children = XMLUtils.getChildrenByTagName(xml, "part");
        for (Element child : children) {
            part.children.add(parse(child));
        }

        return part;
    }
}
