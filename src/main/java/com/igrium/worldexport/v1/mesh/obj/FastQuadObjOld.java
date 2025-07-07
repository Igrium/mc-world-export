package com.igrium.worldexport.v1.mesh.obj;

import de.javagl.obj.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A simplified version of Obj designed to work with Minecraft world exporting.
 * Each face can only have exactly four vertices, and each element (vertex, texcoord) must have a set stride.
 *
 * @implNote This is only designed to be used internally, so there's less safeguards than usual.
 */
public class FastQuadObjOld implements Obj {

    private static final Set<String> DEFAULT_GROUP_SET = Set.of("default");

    public static final int VERTEX_STRIDE = 6;
    public static final int TEXCOORD_STRIDE = 2;
    public static final int NORMAL_STRIDE = 3;

    @Getter
    private final FloatList packedVertices = new FloatArrayList();

    @Getter
    private final FloatList packedTexCoords = new FloatArrayList();

    @Getter
    private final FloatList packedNormals = new FloatArrayList();

    private final List<ObjFace> faces = new ArrayList<>();
    private final List<ObjGroup> groups = new ArrayList<>();
    private final List<ObjGroup> materialGroups = new ArrayList<>();
    private final Map<String, FastQuadObjGroup> groupMap = new LinkedHashMap<>();
    private final Map<String, FastQuadObjGroup> materialGroupMap = new LinkedHashMap<>();

    private List<String> mtlFileNames = Collections.emptyList();

    private final Map<ObjFace, Set<String>> startedGroupNames = new HashMap<>();
    private final Map<ObjFace, String> startedMaterialGroupNames = new HashMap<>();

    @Nullable
    private Set<String> nextActiveGroupNames = null;

    @Nullable
    private String nextActiveMaterialGroupName = null;

    @Nullable
    private List<FastQuadObjGroup> activeGroups = null;

    @Nullable
    private Set<String> activeGroupNames = null;

    @Nullable
    private FastQuadObjGroup activeMaterialGroup = null;

    @Nullable
    private String activeMaterialGroupName = null;

    private static float[] unpack(FloatList packed, int index, int stride, float[] out) {
        int startIndex = index * stride;
        for (int i = 0; i < stride; i++) {
            out[i] = packed.getFloat(startIndex + i);
        }
        return out;
    }

    @Override
    public int getNumVertices() {
        return packedVertices.size() / VERTEX_STRIDE;
    }

    public float[] getVertex(int index, float[] out) {
        return unpack(packedVertices, index, VERTEX_STRIDE, out);
    }

    @Override
    public FloatTuple getVertex(int index) {
        return new ArrayFloatTuple(getVertex(index, new float[VERTEX_STRIDE]));
    }

    @Override
    public int getNumTexCoords() {
        return packedTexCoords.size() / TEXCOORD_STRIDE;
    }

    public float[] getTexCoord(int index, float[] out) {
        return unpack(packedTexCoords, index, TEXCOORD_STRIDE, out);
    }

    @Override
    public FloatTuple getTexCoord(int index) {
        return new ArrayFloatTuple(getTexCoord(index, new float[TEXCOORD_STRIDE]));
    }

    @Override
    public int getNumNormals() {
        return packedNormals.size() / NORMAL_STRIDE;
    }

    public float[] getNormal(int index, float[] out) {
        return unpack(packedTexCoords, index, NORMAL_STRIDE, out);
    }

    @Override
    public FloatTuple getNormal(int index) {
        return new ArrayFloatTuple(getNormal(index, new float[NORMAL_STRIDE]));
    }

    @Override
    public int getNumFaces() {
        return faces.size();
    }

    @Override
    public ObjFace getFace(int index) {
        return faces.get(index);
    }

    @Override
    public Set<String> getActivatedGroupNames(ObjFace face) {
        return startedGroupNames.get(face);
    }

    @Override
    public String getActivatedMaterialGroupName(ObjFace face) {
        return startedMaterialGroupNames.get(face);
    }

    @Override
    public int getNumGroups() {
        return groups.size();
    }

    @Override
    public ObjGroup getGroup(int index) {
        return groups.get(index);
    }

    @Override
    public ObjGroup getGroup(String name) {
        return groupMap.get(name);
    }

    @Override
    public int getNumMaterialGroups() {
        return materialGroups.size();
    }

    @Override
    public ObjGroup getMaterialGroup(int index) {
        return materialGroups.get(index);
    }

    @Override
    public ObjGroup getMaterialGroup(String name) {
        return materialGroupMap.get(name);
    }

    @Override
    public List<String> getMtlFileNames() {
        return mtlFileNames;
    }

    private void appendTuple(FloatList packed, int stride, FloatTuple tuple, float padding) {
        int dim = tuple.getDimensions();
        for (int i = 0; i < stride; i++) {
            packed.add(i < dim ? tuple.get(i) : padding);
        }
    }

    @Override
    public void addVertex(FloatTuple vertex) {
        appendTuple(packedVertices, VERTEX_STRIDE, vertex, 0);
    }

    @Override
    public void addVertex(float x, float y, float z) {
        packedVertices.add(x);
        packedVertices.add(y);
        packedVertices.add(z);
        packedVertices.add(0);
        packedVertices.add(0);
        packedVertices.add(0);
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
        if (groupNames == null)
            return;
        if (groupNames.isEmpty()) {
            nextActiveGroupNames = DEFAULT_GROUP_SET;
        } else {
            assert !groupNames.contains(null);
            nextActiveGroupNames = Set.copyOf(groupNames);
        }

    }

    public void setActiveGroupName(String groupName) {
        if (groupName == null)
            return;
        nextActiveGroupNames = Set.of(groupName);
    }

    @Override
    public void setActiveMaterialGroupName(String materialGroupName) {
        if (materialGroupName == null)
            return;
        nextActiveMaterialGroupName = materialGroupName;
    }

    @Override
    public void addFace(ObjFace face) {
        if (face == null) {
            throw new NullPointerException("The face is null");
        }
        if (nextActiveGroupNames != null) {
            activeGroups = getGroupsInternal(nextActiveGroupNames);
            if (!nextActiveGroupNames.equals(activeGroupNames)) {
                startedGroupNames.put(face, nextActiveGroupNames);
            }
            activeGroupNames = nextActiveGroupNames;
            nextActiveGroupNames = null;
        }
        if (nextActiveMaterialGroupName != null) {
            activeMaterialGroup =
                    getMatGroupInternal(nextActiveMaterialGroupName);
            if (!nextActiveMaterialGroupName.equals(activeMaterialGroupName)) {
                startedMaterialGroupNames.put(face, nextActiveMaterialGroupName);
            }
            activeMaterialGroupName = nextActiveMaterialGroupName;
            nextActiveMaterialGroupName = null;
        }
        faces.add(face);
        if (activeMaterialGroup != null) {
            activeMaterialGroup.addFace(face);
        }
        if (activeGroups != null) {
            for (var group : activeGroups) {
                group.addFace(face);
            }
        }
    }

    @Override
    public void addFace(int... v) {
        addFace(v, null, null);
    }

    @Override
    public void addFaceWithTexCoords(int... v)
    {
        addFace(v, v, null);
    }

    @Override
    public void addFaceWithNormals(int... v)
    {
        addFace(v, null, v);
    }

    @Override
    public void addFaceWithAll(int... v)
    {
        addFace(v, v, v);
    }


    @Override
    public void addFace(int[] v, int[] vt, int[] vn) {
        Objects.requireNonNull(v, "The vertex indices are null");
//        addFace(new FastQuadFace(v, vt, vn));
    }

    @Override
    public void setMtlFileNames(Collection<? extends String> mtlFileNames) {
        this.mtlFileNames = List.copyOf(mtlFileNames);
    }

    private List<FastQuadObjGroup> getGroupsInternal(Collection<? extends  String> groupNames) {
        if (groupNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<FastQuadObjGroup> list = new ArrayList<>(groupNames.size());
        for (var name : groupNames) {
            list.add(getGroupInternal(name));
        }
        return list;
    }

    private FastQuadObjGroup getGroupInternal(String groupName) {
        return groupMap.computeIfAbsent(groupName, name -> {
            var group = new FastQuadObjGroup(name);
            materialGroups.add(group);
            return group;
        });
    }

    private FastQuadObjGroup getMatGroupInternal(String name) {
        return materialGroupMap.computeIfAbsent(name, n -> {
            var group = new FastQuadObjGroup(name);
            materialGroups.add(group);
            return group;
        });
    }
}
