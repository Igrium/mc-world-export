package org.scaffoldeditor.worldexport.vcap;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.Objs;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

public final class MeshWriter {
    private MeshWriter() {}

    // public static final String WORLD_MAT = "world";
    // public static final String TRANSPARENT_MAT = "world_transparent";
    // public static final String TINTED_MAT = "world_tinted";
    // public static final String TRANSPARENT_TINTED_MAT = "world_trans_tinted";
    public static final String EMPTY_MESH = "empty";

    public static class MeshInfo {
        public final Obj mesh;
        public final int numLayers;

        public MeshInfo(Obj mesh, int numLayers) {
            this.mesh = mesh;
            this.numLayers = numLayers;
        }
    }

    public static MeshInfo empty() {
        return new MeshInfo(Objs.create(), 1);
    }
    

    public static MeshInfo writeBlockMesh(ModelEntry entry, Random random, Consumer<VcapWorldMaterial> materialConsumer) {
        Obj obj = Objs.create();
        BakedModel model = entry.model();
        BlockState blockState = entry.blockState();
        boolean transparent = entry.transparent();
        boolean emissive = entry.emissive();

        List<Set<float[]>> fLayers = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (!entry.isFaceVisible(direction)) continue;
            List<BakedQuad> quads = model.getQuads(blockState, direction, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, transparent, emissive, fLayers, materialConsumer);
            }
        }
        {
            // Quads that aren't assigned to a direction.
            List<BakedQuad> quads = model.getQuads(blockState, null, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, transparent, emissive, fLayers, materialConsumer);
            }
        }
        return new MeshInfo(obj, fLayers.size());
    }

    /**
     * Add a baked quad to a 3d mesh.
     * 
     * @param quad             Quad to add.
     * @param obj              Mesh to add to.
     * @param transparent      Assign transparent material.
     * @param fLayers          A list of sets of 12-float arrays indicating what
     *                         quads already exist. Used for material stacking.
     * @param materialConsumer For all the generated vcap world materials.
     * @return The face layer index this face was added to.
     */
    public static int addFace(BakedQuad quad, Obj obj, boolean transparent, boolean emissive,
            @Nullable List<Set<float[]>> fLayers, Consumer<VcapWorldMaterial> materialConsumer) {

        VcapWorldMaterial mat = new VcapWorldMaterial(transparent, quad.hasColor(), emissive);
        materialConsumer.accept(mat);
        obj.setActiveMaterialGroupName(mat.getName());

        int[] vertData = quad.getVertexData();

        int len = vertData.length / 8;
        ByteBuffer buffer = ByteBuffer.allocate(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte());
        IntBuffer intBuffer = buffer.asIntBuffer();

        int[] indices = new int[len];
        float[] vertices = new float[len * 4];

        for (int i = 0; i < len; i++) {
            indices[i] = obj.getNumVertices();

            intBuffer.clear();
            intBuffer.put(vertData, i * 8, 8);

            float x = buffer.getFloat(0);
            float y = buffer.getFloat(4);
            float z = buffer.getFloat(8);

            float u = buffer.getFloat(16);
            float v = buffer.getFloat(20);

            obj.addTexCoord(u, 1 - v);
            obj.addVertex(x, y, z);

            vertices[i * 3] = x;
            vertices[i * 3 + 1] = y;
            vertices[i * 3 + 1] = z;
        }

        // Identify the first layer without this face.
        int layerIndex = 0;

        if (fLayers != null) {
            if (fLayers.isEmpty()) {
                fLayers.add(new HashSet<>());
            }

            int i = 0;
            Set<float[]> layer = fLayers.get(i);
            while (contains(layer, vertices)) {
                i++;
                if (fLayers.size() >= i) {
                    fLayers.add(new HashSet<>());
                }
                layer = fLayers.get(i);
            }
            layerIndex = i;
            layer.add(vertices);
        }

        obj.setActiveGroupNames(Arrays.asList(genGroupName(layerIndex)));
        obj.addFace(indices, indices, null);

        return layerIndex;
    }

    private static boolean contains(Collection<float[]> collection, float[] array) {
        for (float[] val : collection) {
            if (Arrays.equals(val, array)) return true;
        }
        return false;
    }
    
    public static String genGroupName(int index) {
        return "fLayer"+index;
    }

    /**
     * Determine whether two meshes are equivilent.
     * 
     * Note: Only checks if verts are the same for now.
     * @param first The first mesh.
     * @param second The second mesh.
     * @return Whether they are equivilent.
     */
    @Deprecated
    public static boolean objEquals(Obj first, Obj second) {
        if (first.getNumVertices() != second.getNumVertices()) return false;
        if (first.getNumFaces() != second.getNumFaces()) return false;
        if (first.getNumNormals() != second.getNumNormals()) return false;

        Set<ObjFace> used = new HashSet<>();

        for (int i = 0; i < first.getNumFaces(); i++) {
            ObjFace secondFace = findFace(first, second, i, used);
            if (secondFace == null) return false;
            used.add(secondFace);
        }

        return true;
    }

    private static ObjFace findFace(Obj first, Obj second, int index, Collection<ObjFace> exclude) {
        ObjFace firstFace = first.getFace(index);
        for (int i = 0; i < second.getNumFaces(); i++) {
            ObjFace secondFace = second.getFace(i);
            if (exclude.contains(secondFace)) continue;
            if (faceEquals(firstFace, secondFace, first, second)) return secondFace;
        }
        return null;
    }

    private static boolean faceEquals(ObjFace first, ObjFace second,
        Obj firstObj, Obj secondObj) {
        if (first.getNumVertices() != second.getNumVertices()) return false;
        for (int i = 0; i < first.getNumVertices(); i++) {
            if (!tupleEquals(firstObj.getVertex(first.getVertexIndex(i)),
                secondObj.getVertex(second.getVertexIndex(i)))) {
                return false;
            }
            if (!tupleEquals(firstObj.getNormal(first.getNormalIndex(i)),
                secondObj.getNormal(second.getNormalIndex(i)))) {
                return false;
            }
            if (!tupleEquals(firstObj.getTexCoord(first.getTexCoordIndex(i)),
                secondObj.getTexCoord(second.getTexCoordIndex(i)))) {
                return false;
            }
        }

        return true;
    }
 
    private static boolean tupleEquals(FloatTuple first, FloatTuple second) {
        try {
            return (first.getDimensions() == second.getDimensions())
            && (first.getW() == second.getW())
            && (first.getX() == second.getX())
            && (first.getY() == second.getY())
            && (first.getZ() == second.getZ());
        } catch (ArrayIndexOutOfBoundsException e) {
            return true; // If we hit the end of the tuple, all prior checks worked.
        }
    }
}
