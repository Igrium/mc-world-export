package org.scaffoldeditor.worldexport.util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.ReadableObj;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparators;
import net.minecraft.util.math.Vec3d;

/**
 * Contains a set of functions allowing the comparison of meshes.
 */
public class MeshComparator {
    /**
     * Compare material groups along with vertices.
     */
    public static final int COMPARE_MATERIALS = 1;

    /**
     * Compare vertex texcoord in addition to location.
     */
    public static final int COMPARE_UVS = 2;

    /**
     * Compare face groups along with vertices.
     */
    public static final int COMPARE_GROUPS = 4;

    /**
     * Don't sort the vertex order before comparison. Optimization for if vertex
     * order is known to be deterministic.
     */
    public static final int NO_SORT = 8;

    /**
     * Don't explicitly check for face congruency. Allows for matches when meshes
     * have been triangulated differently.
     */
    public static final int LENIENT_FACE_MATCHING = 16;

    private Map<ReadableObj, int[]> cache = new HashMap<>();

    private Comparator<FloatTuple> comparator = new Comparator<FloatTuple>() {

        @Override
        public int compare(FloatTuple o1, FloatTuple o2) {
            int x = Double.compare(o1.getX(), o2.getX());
            if (x != 0) return x;
            int y = Double.compare(o1.getY(), o2.getY());
            if (y != 0) return y;
            return Double.compare(o1.getZ(), o2.getZ());
        }
        
    };

    /**
     * Determine if one mesh "equals" another mesh.
     * @param mesh1 Mesh 1.
     * @param mesh2 Mesh 2.
     * @param epsilon The epsilon to use while comparing vertices.
     * @param flags Bit flags to use:
     * <p><code>COMPARE_MATERIALS: 1</code></p>
     * <p><code>COMPARE_UVS: 2</code></p>
     * <p><code>COMPARE_GROUPS: 4</code></p>
     * <p><code>NO_SORT: 8</code></p>
     * @return Whether these models are equal.
     */
    public boolean meshEquals(ReadableObj mesh1, ReadableObj mesh2, float epsilon, int flags) {
        return meshEquals(mesh1, mesh2, Vec3d.ZERO, epsilon, flags);
    }

    /**
     * Determine if one mesh "equals" another mesh.
     * @param mesh1 Mesh 1.
     * @param mesh2 Mesh 2.
     * @param offset An offset to apply to the first mesh when comparing.
     * @param epsilon The epsilon to use while comparing vertices.
     * @param flags Bit flags to use:
     * <p><code>COMPARE_MATERIALS: 1</code></p>
     * <p><code>COMPARE_UVS: 2</code></p>
     * <p><code>COMPARE_GROUPS: 4</code></p>
     * <p><code>NO_SORT: 8</code></p>
     * @return Whether these models are equal.
     */
    public boolean meshEquals(ReadableObj mesh1, ReadableObj mesh2, Vec3d offset, float epsilon, int flags) {
        if (mesh1.equals(mesh2)) return true;

        // Preliminary checks.
        if (mesh1.getNumVertices() != mesh2.getNumVertices()) return false;

        if ((flags & LENIENT_FACE_MATCHING) != LENIENT_FACE_MATCHING) {
            if (mesh1.getNumFaces() != mesh2.getNumFaces()) return false;
        }

        boolean compareMaterials = (flags & COMPARE_MATERIALS) == COMPARE_MATERIALS;
        if (compareMaterials) {
            if (mesh1.getNumMaterialGroups() != mesh2.getNumMaterialGroups()) return false;
        }

        boolean compareGroups = (flags & COMPARE_MATERIALS) == COMPARE_MATERIALS;
        if (compareGroups) {
            if (mesh1.getNumGroups() != mesh2.getNumGroups()) return false;
        }

        boolean noSort = (flags & NO_SORT) == NO_SORT;
        
        int[] indices1;
        int[] indices2;

        if (!noSort) {
            indices1 = getSortedIndices(mesh1);
            indices2 = getSortedIndices(mesh2);
        } else {
            // To satisfy the compiler.
            indices1 = indices2 = new int[0];
        }

        for (int i = 0; i < mesh1.getNumVertices(); i++) {
            int index1;
            int index2;

            if (noSort) {
                index1 = index2 = i;
            } else {
                index1 = indices1[i];
                index2 = indices2[i];
            }

            if (!floatTupleEquals(mesh1.getVertex(index1), mesh2.getVertex(index2), epsilon, offset)) return false;

            if ((flags & COMPARE_UVS) == COMPARE_UVS) {
                if (!mesh1.getTexCoord(index1).equals(mesh2.getTexCoord(index2))) return false;
            }
        }

        return true;
    }

    private boolean floatTupleEquals(FloatTuple first, FloatTuple second, float epsilon, Vec3d offset) {
        return Math.abs(first.getX() + offset.x - second.getX()) <= epsilon
                && Math.abs(first.getY() + offset.y - second.getY()) <= epsilon
                && Math.abs(first.getZ() + offset.z - second.getZ()) <= epsilon;
    }

    private int[] getSortedIndices(ReadableObj mesh) {
        if (cache.containsKey(mesh)) return cache.get(mesh);

        Comparator<Integer> indexComparator = Comparator.comparing(index -> mesh.getVertex(index), comparator);
        int[] indices = IntStream.range(0, mesh.getNumVertices()).toArray();
        IntArrays.quickSort(indices, IntComparators.asIntComparator(indexComparator));
        cache.put(mesh, indices);
        return indices;
    }
}
