package com.igrium.worldexport.mesh.VertexConsumers;

import com.igrium.worldexport.mesh.ColoredVertex;
import de.javagl.obj.Obj;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2fc;
import org.joml.Vector3fc;

public class ObjVertexConsumer extends FaceVertexConsumer {

    @Getter
    private final Obj obj;

    private boolean initialized;

    @Getter @Setter
    private boolean enableColors = true;

    @Getter
    private boolean enableNormals = false;

    public void setEnableNormals(boolean enableNormals) {
        if (initialized) {
            throw new IllegalStateException("Can't toggle normals after faces have been written.");
        }
        this.enableNormals = enableNormals;
    }

    public ObjVertexConsumer(Obj obj) {
        this.obj = obj;
    }

    @Override
    protected void handleFace(Vector3fc[] vertices, Vector3fc[] colors, Vector3fc[] normals, Vector2fc[] texCoords) {
        initialized = true;
        int objHead = obj.getNumVertices();
        int[] indices = new int[4];

        for (int i = 0; i < 4; i++) {
            indices[i] = objHead + i;

            // Per Blender standard, colors get stored with the vertex.
            if (enableColors) {
                obj.addVertex(new ColoredVertex(vertices[i], colors[i]));
            } else {
                obj.addVertex(vertices[i].x(), vertices[i].y(), vertices[i].z());
            }

            if (enableNormals) {
                obj.addNormal(normals[i].x(), normals[i].y(), normals[i].z());
            }

            obj.addTexCoord(texCoords[i].x(), 1 - texCoords[i].y());
        }

        if (enableNormals) {
            obj.addFace(indices, indices, indices);
        } else {
            obj.addFace(indices, indices, null);
        }
    }
}
