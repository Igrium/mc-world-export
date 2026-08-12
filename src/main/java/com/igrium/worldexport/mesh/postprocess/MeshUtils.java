package com.igrium.worldexport.mesh.postprocess;

import com.igrium.worldexport.mesh.vertex.ArrayBackedFloatTuple;
import de.javagl.obj.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3fc;

import java.util.Collections;
import java.util.Set;

public class MeshUtils {

    /**
     * A Minecraft matrix stack entry representing an identity matrix.
     */
    public static final PoseStack.Pose IDENTITY_ENTRY = new PoseStack().last();

    public static FloatTuple transformFloatTuple(FloatTuple floatTuple, Vector3fc offset) {
        float[] vals = new float[floatTuple.getDimensions()];
        for (int i = 0; i < floatTuple.getDimensions(); i++) {
            vals[i] = floatTuple.get(i);
        }
        for (int i = 0; i < Math.min(vals.length, 3); i++) {
            vals[i] += offset.get(i);
        }
        return new ArrayBackedFloatTuple(vals);
    }

    /**
     * Add all the data of the given OBJ into an output OBJ, assigning all its faces to a group.
     *
     * @param input     OBJ to add.
     * @param groupName Name to give the added group.
     * @param output    OBJ to add to.
     */
    public static void addAsGroup(ReadableObj input, String groupName, Obj output) {
        int vertexOffset = output.getNumVertices();
        for (int i = 0; i < input.getNumVertices(); i++) {
            output.addVertex(input.getVertex(i));
        }

        int texCoordOffset = output.getNumTexCoords();
        for (int i = 0; i < input.getNumTexCoords(); i++) {
            output.addTexCoord(input.getTexCoord(i));
        }

        int normalOffset = output.getNumNormals();
        for (int i = 0; i < input.getNumNormals(); i++) {
            output.addNormal(input.getNormal(i));
        }

        for (int i = 0; i < input.getNumFaces(); i++) {
            ObjFace inputFace = input.getFace(i);
            if (i == 0)
                output.setActiveGroupNames(Collections.singleton(groupName));

            activateGroups(input, inputFace, groupName, output);

            ObjFace outputFace = duplicateFaceWithOffsets(inputFace, vertexOffset, texCoordOffset, normalOffset);
            output.addFace(outputFace);
        }
    }

    private static void activateGroups(ReadableObj input, ObjFace face, String groupName, WritableObj output) {
        // Ends up discarding the input groups, but that's fine.
        output.setActiveGroupNames(Collections.singleton(groupName));

        String activatedMaterialGroupName = input.getActivatedMaterialGroupName(face);
        if (activatedMaterialGroupName != null) {
            output.setActiveMaterialGroupName(activatedMaterialGroupName);
        }
    }

    private static void activateGroups(ReadableObj input, ObjFace face, WritableObj output) {
        Set<String> activatedGroupNames = input.getActivatedGroupNames(face);
        if (activatedGroupNames != null) {
            output.setActiveGroupNames(activatedGroupNames);
        }
        String activatedMaterialGroupName = input.getActivatedMaterialGroupName(face);
        if (activatedMaterialGroupName != null) {
            output.setActiveMaterialGroupName(activatedMaterialGroupName);
        }
    }

    /**
     * Create a copy of the given face, adding the given offsets to the
     * respective indices. If the given face does not contain texture
     * coordinate or normal indices, then the respective offsets will
     * be ignored.<br>
     *
     * @param face           The input face
     * @param vertexOffset   The offset for the vertex indices
     * @param texCoordOffset The offset for the texture coordinate indices
     * @param normalOffset   The offset for the normal indices
     * @return The copy
     */
    public static ObjFace duplicateFaceWithOffsets(ObjFace face, int vertexOffset, int texCoordOffset, int normalOffset) {
        int[] v = new int[face.getNumVertices()];
        int[] vt = null;
        int[] vn = null;
        for (int i = 0; i < face.getNumVertices(); i++) {
            v[i] = face.getVertexIndex(i) + vertexOffset;
        }

        if (face.containsTexCoordIndices()) {
            vt = new int[face.getNumVertices()];
            for (int i = 0; i < face.getNumVertices(); i++) {
                vt[i] = face.getTexCoordIndex(i) + texCoordOffset;
            }
        }

        if (face.containsNormalIndices()) {
            vn = new int[face.getNumVertices()];
            for (int i = 0; i < face.getNumVertices(); i++) {
                vn[i] = face.getNormalIndex(i) + normalOffset;
            }
        }

        return ObjFaces.create(v, vt, vn);
    }

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
            if (!texCoordIndices.containsKey(texCoord)) {
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

            output.setActiveMaterialGroupName(source.getActivatedMaterialGroupName(face));
            output.addFace(vertices, texCoords, normals);
        }

        return output;
    }

    public static void merge(ReadableObj input, Obj output, Vector3fc offset) {
        int verticesOffset = output.getNumVertices();
        for (int i = 0; i < input.getNumVertices(); i++) {
            output.addVertex(transformFloatTuple(input.getVertex(i), offset));
        }

        int texCoordsOffset = output.getNumTexCoords();
        for (int i = 0; i < input.getNumTexCoords(); i++) {
            output.addTexCoord(input.getTexCoord(i));
        }

        int normalsOffset = output.getNumNormals();
        for (int i = 0; i < input.getNumNormals(); i++) {
            output.addNormal(input.getNormal(i));
        }

        for (int i = 0; i < input.getNumFaces(); i++) {
            ObjFace inputFace = input.getFace(i);

            activateGroups(input, inputFace, output);

            ObjFace outputFace = createWithOffsets(inputFace, verticesOffset, texCoordsOffset,
                    normalsOffset);
            output.addFace(outputFace);
        }
    }

    /**
     * Create a copy of the given face, adding the given offsets to the
     * respective indices. If the given face does not contain texture
     * coordinate or normal indices, then the respective offsets will
     * be ignored.<br>
     *
     * @param face            The input face
     * @param verticesOffset  The offset for the vertex indices
     * @param texCoordsOffset The offset for the texture coordinate indices
     * @param normalsOffset   The offset for the normal indices
     * @return The copy
     */
    static ObjFace createWithOffsets(ObjFace face, int verticesOffset, int texCoordsOffset, int normalsOffset) {
        int[] v = new int[face.getNumVertices()];
        int[] vt = null;
        int[] vn = null;
        for (int i = 0; i < face.getNumVertices(); i++) {
            v[i] = face.getVertexIndex(i) + verticesOffset;
        }

        if (face.containsTexCoordIndices()) {
            vt = new int[face.getNumVertices()];
            for (int i = 0; i < face.getNumVertices(); i++) {
                vt[i] = face.getTexCoordIndex(i) + texCoordsOffset;
            }
        }

        if (face.containsNormalIndices()) {
            vn = new int[face.getNumVertices()];
            for (int i = 0; i < face.getNumVertices(); i++) {
                vn[i] = face.getNormalIndex(i) + normalsOffset;
            }
        }

        return ObjFaces.create(v, vt, vn);
    }

}
