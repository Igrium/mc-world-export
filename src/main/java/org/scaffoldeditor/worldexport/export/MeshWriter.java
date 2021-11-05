package org.scaffoldeditor.worldexport.export;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Random;

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

    public static Obj writeBlockMesh(ModelEntry entry, Random random) {
        Obj obj = Objs.create();
        BakedModel model = entry.model;
        obj.setActiveMaterialGroupName(entry.transparent ? TRANSPARENT_MAT : WORLD_MAT);
        for (int d = 0; d < BlockExporter.DIRECTIONS.length; d++) {
            if (!entry.faces[d]) continue;
            Direction direction = BlockExporter.DIRECTIONS[d];
            List<BakedQuad> quads = model.getQuads(null, direction, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, entry.transparent);
            }
        }
        {   // Quads that aren't assigned to a direction.   
            List<BakedQuad> quads = model.getQuads(null, null, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, entry.transparent);
            }
        }

        return obj;
    }

    /**
     * Add a baked quad to a 3d mesh.
     * @param quad Quad to add.
     * @param obj Mesh to add to.
     */
    public static void addFace(BakedQuad quad, Obj obj, boolean transparent) {

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
        }

        obj.addFace(indices, indices, null);
    }
}
