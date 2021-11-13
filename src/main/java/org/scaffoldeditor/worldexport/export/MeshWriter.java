package org.scaffoldeditor.worldexport.export;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.scaffoldeditor.worldexport.export.ExportContext.ModelEntry;

import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;

public final class MeshWriter {
    private MeshWriter() {}

    public static final String WORLD_MAT = "world";
    public static final String TRANSPARENT_MAT = "world_transparent";
    public static final String TINTED_MAT = "world_tinted";
    public static final String TRANSPARENT_TINTED_MAT = "world_trans_tinted";

    public static class MeshInfo {
        public final Obj mesh;
        public final int numLayers;

        public MeshInfo(Obj mesh, int numLayers) {
            this.mesh = mesh;
            this.numLayers = numLayers;
        }
    }

    public static MeshInfo writeBlockMesh(ModelEntry entry, Random random) {
        Obj obj = Objs.create();
        BakedModel model = entry.model;

        List<Set<float[]>> fLayers = new ArrayList<>();
        for (int d = 0; d < BlockExporter.DIRECTIONS.length; d++) {
            if (!entry.faces[d]) continue;
            Direction direction = BlockExporter.DIRECTIONS[d];
            List<BakedQuad> quads = model.getQuads(entry.blockState, direction, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, entry.transparent, fLayers);
            }
        }
        {   // Quads that aren't assigned to a direction.   
            List<BakedQuad> quads = model.getQuads(entry.blockState, null, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, entry.transparent, fLayers);
            }
        }
        return new MeshInfo(obj, fLayers.size());
    }

    /**
     * Add a baked quad to a 3d mesh.
     * @param quad Quad to add.
     * @param obj Mesh to add to.
     * @param transparent Assign transparent material.
     * @param fLayers A list of lists of 12-float arrays indicating what quads already exist. Used for material stacking.
     * @return The face layer index this face was added to.
     */
    public static int addFace(BakedQuad quad, Obj obj, boolean transparent, @Nullable List<Set<float[]>> fLayers) {

        if (transparent) {
            if (quad.hasColor()) {
                obj.setActiveMaterialGroupName(TRANSPARENT_TINTED_MAT);
            } else {
                obj.setActiveMaterialGroupName(TRANSPARENT_MAT);
            }
        } else {
            if (quad.hasColor()) {
                obj.setActiveMaterialGroupName(TINTED_MAT);
            } else {
                obj.setActiveMaterialGroupName(WORLD_MAT);
            }
        }

        int[] vertData = quad.getVertexData();

        int len = vertData.length / 8;
        ByteBuffer buffer = ByteBuffer.allocate(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSize());
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

        obj.setActiveGroupNames(Arrays.asList(new String[]{genGroupName(layerIndex)}));
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
}
