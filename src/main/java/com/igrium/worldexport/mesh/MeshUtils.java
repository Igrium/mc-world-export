package com.igrium.worldexport.mesh;

import de.javagl.obj.*;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class MeshUtils {

    /**
     * Return an OBJ where all duplicate indexed values are merged.
     * @param source Source OBJ
     * @return Merged OBJ.
     */
    public static Obj removeDoubles(ReadableObj source) {

        // A mapping of values and their first index in the source obj
        Object2IntMap<FloatTuple> vertexIndices = new Object2IntOpenHashMap<>();
        Object2IntMap<FloatTuple> texCoordIndices = new Object2IntOpenHashMap<>();
        Object2IntMap<FloatTuple> normalIndices = new Object2IntOpenHashMap<>();

        Obj output = Objs.create();


        for (int i = 0; i < source.getNumVertices(); i++) {
            FloatTuple vertex = source.getVertex(i);
            if (!vertexIndices.containsKey(vertex)) {
                vertexIndices.put(vertex, output.getNumVertices());
                output.addVertex(vertex);
            }
        }

        for (int i = 0; i < source.getNumTexCoords(); i++) {
            FloatTuple texCoord = source.getTexCoord(i);
            if (!vertexIndices.containsKey(texCoord)) {
                texCoordIndices.put(texCoord, output.getNumTexCoords());
                output.addTexCoord(texCoord);
            }
        }

        for (int i = 0; i < source.getNumNormals(); i++) {
            FloatTuple normal = source.getNormal(i);
            if (!normalIndices.containsKey(normal)) {
                normalIndices.put(normal, output.getNumNormals());
                output.addNormal(normal);
            }
        }


        for (int faceIndex = 0; faceIndex < source.getNumFaces(); faceIndex++) {
            ObjFace face = source.getFace(faceIndex);

            int[] vertices = new int[face.getNumVertices()];
            for (int i = 0; i < vertices.length; i++) {
                FloatTuple vertex = source.getVertex(face.getVertexIndex(i));
                vertices[i] = vertexIndices.getInt(vertex);
            }

            int[] texCoords = null;
            if (face.containsTexCoordIndices()) {
                texCoords = new int[face.getNumVertices()];
                for (int i = 0; i < texCoords.length; i++) {
                    FloatTuple texCoord = source.getTexCoord(face.getTexCoordIndex(i));
                    texCoords[i] = texCoordIndices.getInt(texCoord);
                }
            }

            int[] normals = null;
            if (face.containsNormalIndices()) {
                normals = new int[face.getNumVertices()];
                for (int i = 0; i < normals.length; i++) {
                    FloatTuple normal = source.getNormal(face.getNormalIndex(i));
                    normals[i] = normalIndices.getInt(normal);
                }
            }

            output.addFace(vertices, texCoords, normals);
        }

        return output;
    }

}
