import com.igrium.worldexport.mesh.MeshMergeVerts;
import de.javagl.obj.FloatTuple;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.Objs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MeshMergeVertsTest {

    /** Add a quad with its own four vertices. */
    private static void addQuad(Obj obj, float[]... positions) {
        int start = obj.getNumVertices();
        int[] indices = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            obj.addVertex(positions[i][0], positions[i][1], positions[i][2]);
            indices[i] = start + i;
        }
        obj.addFace(indices);
    }

    private static float[] v(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    @Test
    void mergesSharedEdgeOfAdjacentQuads() {
        Obj obj = Objs.create();
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));
        addQuad(obj, v(1, 0, 0), v(2, 0, 0), v(2, 1, 0), v(1, 1, 0));

        Obj merged = MeshMergeVerts.mergeByDistance(obj, 0.001f);

        // The two shared corners become one vertex each, both faces survive.
        assertEquals(6, merged.getNumVertices());
        assertEquals(2, merged.getNumFaces());
        assertEquals(4, merged.getFace(0).getNumVertices());
        assertEquals(4, merged.getFace(1).getNumVertices());
    }

    @Test
    void removesDuplicateFaces() {
        Obj obj = Objs.create();
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));

        Obj merged = MeshMergeVerts.mergeByDistance(obj, 0.001f);

        assertEquals(4, merged.getNumVertices());
        assertEquals(1, merged.getNumFaces());
    }

    @Test
    void collapsesQuadWithCoincidentCornersIntoTriangle() {
        Obj obj = Objs.create();
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(1, 1, 0));

        Obj merged = MeshMergeVerts.mergeByDistance(obj, 0.001f);

        assertEquals(3, merged.getNumVertices());
        assertEquals(1, merged.getNumFaces());
        assertEquals(3, merged.getFace(0).getNumVertices());
    }

    @Test
    void copiesMeshThroughWhenNothingIsCloseEnough() {
        Obj obj = Objs.create();
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));

        Obj merged = MeshMergeVerts.mergeByDistance(obj, 0.001f);

        assertEquals(4, merged.getNumVertices());
        assertEquals(1, merged.getNumFaces());
        for (int i = 0; i < merged.getNumVertices(); i++) {
            assertEquals(obj.getVertex(i).getX(), merged.getVertex(i).getX());
            assertEquals(obj.getVertex(i).getY(), merged.getVertex(i).getY());
            assertEquals(obj.getVertex(i).getZ(), merged.getVertex(i).getZ());
        }
    }

    @Test
    void mergingAveragesPositionsAndPreservesAttributes() {
        Obj obj = Objs.create();
        obj.setActiveMaterialGroupName("stone");
        obj.addTexCoord(0, 0);
        obj.addTexCoord(1, 0);
        obj.addTexCoord(1, 1);
        obj.addTexCoord(0, 1);
        obj.addVertex(0, 0, 0);
        obj.addVertex(1, 0, 0);
        obj.addVertex(1, 1, 0);
        obj.addVertex(0, 1, 0);
        obj.addFaceWithTexCoords(0, 1, 2, 3);
        // A second quad sharing an edge, offset by a hair so the merge has to average.
        obj.setActiveMaterialGroupName("dirt");
        obj.addVertex(1.0001f, 0, 0);
        obj.addVertex(2, 0, 0);
        obj.addVertex(2, 1, 0);
        obj.addVertex(1.0001f, 1, 0);
        obj.addFace(new int[]{4, 5, 6, 7}, new int[]{0, 1, 2, 3}, null);

        Obj merged = MeshMergeVerts.mergeByDistance(obj, 0.001f);

        assertEquals(6, merged.getNumVertices());
        assertEquals(2, merged.getNumFaces());

        // The welded vertex sits halfway between the two originals.
        boolean foundAveraged = false;
        for (int i = 0; i < merged.getNumVertices(); i++) {
            FloatTuple pos = merged.getVertex(i);
            if (Math.abs(pos.getX() - 1.00005f) < 1e-5f && pos.getY() == 0) {
                foundAveraged = true;
            }
        }
        assertTrue(foundAveraged, "Merged vertex should be the average of its sources");

        assertEquals("stone", merged.getActivatedMaterialGroupName(merged.getFace(0)));
        assertEquals("dirt", merged.getActivatedMaterialGroupName(merged.getFace(1)));
        for (int i = 0; i < merged.getNumFaces(); i++) {
            ObjFace face = merged.getFace(i);
            assertTrue(face.containsTexCoordIndices());
        }
    }

    @Test
    void keepsUnrelatedGeometryIntact() {
        Obj obj = Objs.create();
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));
        addQuad(obj, v(0, 0, 0), v(1, 0, 0), v(1, 1, 0), v(0, 1, 0));
        // Far away, untouched by the merge.
        addQuad(obj, v(10, 0, 0), v(11, 0, 0), v(11, 1, 0), v(10, 1, 0));

        Obj merged = MeshMergeVerts.mergeByDistance(obj, 0.001f);

        assertEquals(8, merged.getNumVertices());
        assertEquals(2, merged.getNumFaces());
    }
}
