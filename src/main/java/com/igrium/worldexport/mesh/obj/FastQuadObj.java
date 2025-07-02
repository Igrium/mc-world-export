package com.igrium.worldexport.mesh.obj;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjGroup;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FastQuadObj implements Obj {
    private static final Set<String> DEFAULT_GROUP_SET = Set.of("default");

    public static final int VERTEX_STRIDE = 6;
    public static final int TEXCOORD_STRIDE = 2;
    public static final int NORMAL_STRIDE = 3;
    public static final int FACE_STRIDE = 12; // 4 + 4 + 4

    @Getter
    private final FloatList packedVertices = new FloatArrayList();

    @Getter
    private final FloatList packedTexCoords = new FloatArrayList();

    @Getter
    private final FloatList packedNormals = new FloatArrayList();

    private final IntList packedFaces = new IntArrayList();

    @Nullable
    private String activeGroupName; // Only need to support one group (for block separation)

    @Nullable
    private String activeMaterialGroupName;

    private final List<String> groupNames = new ArrayList<>();
    private final Map<String, IntList> groups = new HashMap<>();

    private final List<String> materialGroupNames = new ArrayList<>();
    private final Map<String, IntList> materialGroups = new HashMap<>();

    private float[] unpack(FloatList packed, int index, int stride, float[] out) {
        int min = Math.min(stride, out.length);
        int start = index * stride;

        for (int i = 0; i < min; i++) {
            out[i] = packed.getFloat(start + i);
        }
        return out;
    }

    private FloatTuple unpack(FloatList packed, int index, int stride) {
        return new ArrayFloatTuple(unpack(packed, index, stride, new float[stride]));
    }

    @Override
    public int getNumVertices() {
        return packedVertices.size() / VERTEX_STRIDE;
    }

    @Override
    public FloatTuple getVertex(int index) {
        return unpack(packedVertices, index, VERTEX_STRIDE);
    }

    public float[] getVertex(int index, float[] out) {
        return unpack(packedVertices, index, VERTEX_STRIDE, out);
    }

    @Override
    public int getNumTexCoords() {
        return packedTexCoords.size() / TEXCOORD_STRIDE;
    }

    @Override
    public FloatTuple getTexCoord(int index) {
        return unpack(packedTexCoords, index, TEXCOORD_STRIDE);
    }

    public float[] getTexCoord(int index, float[] out) {
        return unpack(packedTexCoords, index, TEXCOORD_STRIDE, out);
    }

    @Override
    public int getNumNormals() {
        return packedNormals.size() / NORMAL_STRIDE;
    }

    @Override
    public FloatTuple getNormal(int index) {
        return unpack(packedNormals, index, NORMAL_STRIDE);
    }

    public float[] getNormal(int index, float[] out) {
        return unpack(packedNormals, index, NORMAL_STRIDE, out);
    }

    @Override
    public int getNumFaces() {
        return packedFaces.size() / FACE_STRIDE;
    }

    @Override
    public ObjFace getFace(int index) {
        int start = index * FACE_STRIDE;
        return new FastQuadFace(packedFaces.subList(start, start + FACE_STRIDE).toIntArray());
    }

    @Override
    public Set<String> getActivatedGroupNames(ObjFace face) {
        return Set.of();
    }

    @Override
    public String getActivatedMaterialGroupName(ObjFace face) {
        return "";
    }

    @Override
    public int getNumGroups() {
        return 0;
    }

    @Override
    public ObjGroup getGroup(int index) {
        return null;
    }

    @Override
    public ObjGroup getGroup(String name) {
        return null;
    }

    @Override
    public int getNumMaterialGroups() {
        return 0;
    }

    @Override
    public ObjGroup getMaterialGroup(int index) {
        return null;
    }

    @Override
    public ObjGroup getMaterialGroup(String name) {
        return null;
    }

    @Override
    public List<String> getMtlFileNames() {
        return List.of();
    }

    private void appendTuple(FloatList packed, int stride, FloatTuple tuple, float padding) {
        int dim = tuple.getDimensions();
        for (int i = 0; i < stride; i++) {
            packed.add(i < dim ? tuple.get(i) : padding);
        }
    }

    @Override
    public void addVertex(FloatTuple vertex) {
        appendTuple(packedVertices, VERTEX_STRIDE, vertex, 1);
    }

    @Override
    public void addVertex(float x, float y, float z) {
        packedVertices.add(x);
        packedVertices.add(y);
        packedVertices.add(z);
        packedVertices.add(1);
        packedVertices.add(1);
        packedVertices.add(1);
    }

    @Override
    public void addTexCoord(FloatTuple texCoord) {
        appendTuple(packedTexCoords, TEXCOORD_STRIDE, texCoord, 0);
    }

    @Override
    public void addTexCoord(float x) {
        packedTexCoords.add(x);
        packedTexCoords.add(0);
    }

    @Override
    public void addTexCoord(float x, float y) {
        packedTexCoords.add(x);
        packedTexCoords.add(y);
    }

    @Override
    public void addTexCoord(float x, float y, float z) {
        packedTexCoords.add(x);
        packedTexCoords.add(y);
    }

    @Override
    public void addNormal(FloatTuple normal) {
        appendTuple(packedNormals, NORMAL_STRIDE, normal, 0);
    }

    @Override
    public void addNormal(float x, float y, float z) {
        packedNormals.add(x);
        packedNormals.add(y);
        packedNormals.add(z);
    }

    @Override
    public void setActiveGroupNames(Collection<? extends String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            activeGroupName = "default";
        } else {
            activeGroupName = groupNames.iterator().next();
        }
    }

    public void setActiveGroupName(@Nullable String groupName) {
        this.activeGroupName = groupName;
    }

    @Override
    public void setActiveMaterialGroupName(String materialGroupName) {
        if (materialGroupName == null) {
            activeMaterialGroupName = "default";
        } else {
            activeMaterialGroupName = materialGroupName;
        }
    }

    @Override
    public void addFace(ObjFace face) {
        if (face.getNumVertices() != 4) {
            throw new IllegalArgumentException("FastQuadObj only supports quad faces.");
        }
        addFace(
                new int[]{face.getVertexIndex(0), face.getVertexIndex(1), face.getVertexIndex(2), face.getVertexIndex(3)},
                new int[]{face.getTexCoordIndex(0), face.getTexCoordIndex(1), face.getTexCoordIndex(2), face.getTexCoordIndex(3)},
                new int[]{face.getNormalIndex(0), face.getNormalIndex(1), face.getNormalIndex(2), face.getNormalIndex(3)}
        );
    }

    private static final int[] DEFAULT_INT = new int[]{0, 0, 0, 0};

    @Override
    public void addFace(int... v) {
        addFace(v, DEFAULT_INT, DEFAULT_INT);
    }

    @Override
    public void addFaceWithTexCoords(int... v) {
        addFace(v, v, DEFAULT_INT);
    }

    @Override
    public void addFaceWithNormals(int... v) {
        addFace(v, DEFAULT_INT, v);
    }

    @Override
    public void addFaceWithAll(int... v) {
        addFace(v, v, v);
    }

    @Override
    public void addFace(int[] v, int[] vt, int[] vn) {
        for (int i = 0; i < 4; i++) {
            packedFaces.add(v[i]);
            packedFaces.add(vt[i]);
            packedFaces.add(vn[i]);
        }
        int index = getNumFaces();


    }

    @Override
    public void setMtlFileNames(Collection<? extends String> mtlFileNames) {

    }

}
