package org.scaffoldeditor.worldexport.export;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Random;

import org.scaffoldeditor.worldexport.Exporter;
import org.scaffoldeditor.worldexport.export.ExportContext.ModelEntry;

import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;

public final class MeshWriter {
    private MeshWriter() {}

    public static Obj writeBlockMesh(ModelEntry entry, Random random) {
        Obj obj = Objs.create();
        BakedModel model = entry.model;

        for (int d = 0; d < Exporter.DIRECTIONS.length; d++) {
            if (!entry.faces[d]) continue;
            Direction direction = Exporter.DIRECTIONS[d];
            List<BakedQuad> quads = model.getQuads(null, direction, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj);
            }
        }
        {   // Quads that aren't assigned to a direction.   
            List<BakedQuad> quads = model.getQuads(null, null, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj);
            }
        }
        
        return obj;
    }

    /**
     * Add a baked quad to a 3d mesh.
     * @param quad Quad to add.
     * @param obj Mesh to add to.
     */
    public static void addFace(BakedQuad quad, Obj obj) {
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
