package com.igrium.worldexport.mesh;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.Objs;
import de.javagl.obj.ReadableObj;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.experimental.UtilityClass;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges ("welds") vertices of an OBJ together, collapsing the edges, corners
 * and faces that become degenerate as a result and removing faces that end up
 * duplicated.
 * <p>
 * This is a port of Blender's <code>mesh_merge_verts.cc</code>
 * (<code>blender::geometry</code>), adapted to run on {@link Obj} meshes instead
 * of Blender's <code>Mesh</code>. The names of the internal structures and
 * functions are kept close to the original to keep it diffable against upstream.
 * <p>
 * Compared to {@link MeshUtils#removeDoubles(ReadableObj)}, which only
 * de-duplicates the index arrays, this actually changes the topology: vertices
 * within a distance of each other become one vertex, edges between two merged
 * vertices collapse, faces that lose enough corners disappear, faces that touch
 * themselves after merging are split apart, and coincident faces are removed.
 * @implNote Vibe-ported from Blender
 */
@UtilityClass
public final class MeshMergeVerts {

    /** Indicates when the element was not computed. */
    private static final int OUT_OF_CONTEXT = -1;
    /** Indicates if the edge or face will be collapsed. */
    private static final int ELEM_COLLAPSED = -2;
    /** The largest possible squared distance between two colors whose channels are each in <code>[0, 1]</code>. */
    private static final float MAX_COLOR_DISTANCE_SQ = 3f;

    /* -------------------------------------------------------------------- */
    /* Public API                                                           */

    /**
     * A map of source vertices to the vertex they should be merged into.
     *
     * @param vertDestMap For every source vertex, either the index of the vertex it
     *                    merges into, or <code>-1</code> if it does not take part in
     *                    the merge. Targets map to themselves.
     * @param vertKillLen The number of vertices that will disappear.
     */
    public record MergeMap(int[] vertDestMap, int vertKillLen) {
    }

    /**
     * Merge all vertices that are within a given distance of each other. The
     * attributes of a merged vertex are averaged from the vertices that went into
     * it.
     *
     * @param source        OBJ to merge. Not modified.
     * @param mergeDistance Maximum distance between two vertices for them to merge.
     * @return A new OBJ with the merged mesh. If nothing was close enough to merge,
     *         this is a copy of the source.
     */
    public static Obj mergeByDistance(ReadableObj source, float mergeDistance) {
        return mergeByDistance(source, mergeDistance, true);
    }

    /**
     * Merge all vertices that are within a given distance of each other.
     *
     * @param source        OBJ to merge. Not modified.
     * @param mergeDistance Maximum distance between two vertices for them to merge.
     * @param doMixData     If true, a merged vertex or corner gets its attributes
     *                      averaged from all the elements that went into it. If
     *                      false, it keeps those of the element it merged into.
     * @return A new OBJ with the merged mesh. If nothing was close enough to merge,
     *         this is a copy of the source.
     */
    public static Obj mergeByDistance(ReadableObj source, float mergeDistance, boolean doMixData) {
        return mergeByDistance(source, mergeDistance, doMixData, -1f);
    }

    /**
     * Merge all vertices that are within a given distance of each other.
     *
     * @param source            OBJ to merge. Not modified.
     * @param mergeDistance     Maximum distance between two vertices for them to
     *                          merge.
     * @param doMixData         If true, a merged vertex or corner gets its
     *                          attributes averaged from all the elements that went
     *                          into it. If false, it keeps those of the element it
     *                          merged into.
     * @param colorThreshold    How different two vertex colors are allowed to be
     *                          for them to still be considered equal for merging
     *                          purposes, keeping color discontinuities sharp.
     *                          Normalized: <code>0</code> requires colors to match
     *                          exactly, <code>1</code> allows any two colors to
     *                          merge. Pass a negative value to ignore vertex colors
     *                          entirely. See {@link ColoredVertex}.
     * @return A new OBJ with the merged mesh. If nothing was close enough to merge,
     *         this is a copy of the source.
     */
    public static Obj mergeByDistance(ReadableObj source, float mergeDistance, boolean doMixData,
                                      float colorThreshold) {
        SourceMesh mesh = SourceMesh.fromObj(source);
        MergeMap map = buildDistanceMergeMap(mesh.vertPositions, colorThreshold >= 0 ? mesh.vertColors : null,
                mergeDistance, colorThreshold);
        return createMergedMesh(mesh, map.vertDestMap(), map.vertKillLen(), doMixData);
    }

    /**
     * Merge vertices according to an explicit map.
     *
     * @param source      OBJ to merge. Not modified.
     * @param vertDestMap For every source vertex, either the index of the vertex it
     *                    merges into or <code>-1</code>. Modified in place to make
     *                    every merge target point at itself.
     * @param vertKillLen The number of vertices that will disappear.
     * @param doMixData   If true, a merged vertex or corner gets its attributes
     *                    averaged from all the elements that went into it.
     * @return A new OBJ with the merged mesh.
     */
    public static Obj mergeVerts(ReadableObj source, int[] vertDestMap, int vertKillLen, boolean doMixData) {
        return createMergedMesh(SourceMesh.fromObj(source), vertDestMap, vertKillLen, doMixData);
    }

    /**
     * Build a merge map that welds every vertex into the lowest-indexed vertex
     * within <code>mergeDistance</code> of it, without performing the merge. Pass
     * the result to {@link #mergeVerts}, optionally after adjusting it.
     *
     * @param source        OBJ to search.
     * @param mergeDistance Maximum distance between two vertices for them to merge.
     * @return The merge map.
     */
    public static MergeMap buildDistanceMergeMap(ReadableObj source, float mergeDistance) {
        return buildDistanceMergeMap(source, mergeDistance, -1f);
    }

    /**
     * Build a merge map that welds every vertex into the lowest-indexed vertex
     * within <code>mergeDistance</code> of it, without performing the merge. Pass
     * the result to {@link #mergeVerts}, optionally after adjusting it.
     *
     * @param source          OBJ to search.
     * @param mergeDistance   Maximum distance between two vertices for them to
     *                        merge.
     * @param colorThreshold  How different two vertex colors are allowed to be for
     *                        them to still be considered equal for merging
     *                        purposes. Normalized: <code>0</code> requires colors
     *                        to match exactly, <code>1</code> allows any two colors
     *                        to merge. Pass a negative value to ignore vertex
     *                        colors entirely. See {@link ColoredVertex}.
     * @return The merge map.
     */
    public static MergeMap buildDistanceMergeMap(ReadableObj source, float mergeDistance, float colorThreshold) {
        return buildDistanceMergeMap(readPositions(source), colorThreshold >= 0 ? readColors(source) : null,
                mergeDistance, colorThreshold);
    }

    /* -------------------------------------------------------------------- */
    /* Source mesh                                                          */

    /**
     * The connectivity the weld algorithm needs, derived from an OBJ.
     * <p>
     * OBJs only store faces, so the edge list is synthesized here: every pair of
     * adjacent face corners contributes an edge, and edges shared between faces are
     * shared here too. Texture coordinates and normals are resolved per
     * <i>corner</i> rather than per vertex, which is what lets an OBJ reference one
     * position with several UVs.
     */
    private static final class SourceMesh {
        /** Position of each vertex. */
        Vector3f[] vertPositions;
        /** Optional per-vertex color, as used by {@link ColoredVertex}. Entries may be null. */
        Vector3f[] vertColors;
        /** Two vertex indices per edge. */
        int[] edgeVerts;
        /** First corner of each face, with one extra trailing entry holding the corner count. */
        int[] faceOffsets;
        /** The vertex each corner refers to. */
        int[] cornerVerts;
        /** The edge running from each corner to the next corner of its face. */
        int[] cornerEdges;
        /** Per-corner texture coordinate. Entries may be null. */
        Vector2f[] cornerTexCoords;
        /** Per-corner normal. Entries may be null. */
        Vector3f[] cornerNormals;
        /** Per-face material group name. Entries may be null. */
        String[] faceMaterials;
        /** Per-face group names. Entries may be null. */
        List<Set<String>> faceGroups;
        /** MTL file names to carry over to the output. */
        List<String> mtlFileNames;

        int getNumVerts() {
            return vertPositions.length;
        }

        int getNumEdges() {
            return edgeVerts.length / 2;
        }

        int getNumFaces() {
            return faceOffsets.length - 1;
        }

        static SourceMesh fromObj(ReadableObj obj) {
            SourceMesh mesh = new SourceMesh();
            final int numFaces = obj.getNumFaces();

            mesh.faceOffsets = new int[numFaces + 1];
            int numCorners = 0;
            for (int i = 0; i < numFaces; i++) {
                mesh.faceOffsets[i] = numCorners;
                numCorners += obj.getFace(i).getNumVertices();
            }
            mesh.faceOffsets[numFaces] = numCorners;

            mesh.vertPositions = readPositions(obj);
            mesh.vertColors = readColors(obj);

            mesh.cornerVerts = new int[numCorners];
            mesh.cornerEdges = new int[numCorners];
            mesh.cornerTexCoords = new Vector2f[numCorners];
            mesh.cornerNormals = new Vector3f[numCorners];
            mesh.faceMaterials = new String[numFaces];
            mesh.faceGroups = new ArrayList<>(numFaces);
            mesh.mtlFileNames = List.copyOf(obj.getMtlFileNames());

            Long2IntMap edgeIndices = new Long2IntOpenHashMap();
            edgeIndices.defaultReturnValue(-1);
            IntArrayList edgeVerts = new IntArrayList(numCorners * 2);

            // Group and material activations are only reported on the face that changes them.
            String activeMaterial = null;
            Set<String> activeGroups = null;

            for (int faceIndex = 0; faceIndex < numFaces; faceIndex++) {
                ObjFace face = obj.getFace(faceIndex);

                String material = obj.getActivatedMaterialGroupName(face);
                if (material != null) {
                    activeMaterial = material;
                }
                Set<String> groups = obj.getActivatedGroupNames(face);
                if (groups != null) {
                    activeGroups = groups;
                }
                mesh.faceMaterials[faceIndex] = activeMaterial;
                mesh.faceGroups.add(activeGroups);

                final int faceStart = mesh.faceOffsets[faceIndex];
                final int faceSize = face.getNumVertices();
                for (int i = 0; i < faceSize; i++) {
                    final int corner = faceStart + i;
                    final int vert = face.getVertexIndex(i);
                    mesh.cornerVerts[corner] = vert;

                    if (face.containsTexCoordIndices()) {
                        FloatTuple texCoord = obj.getTexCoord(face.getTexCoordIndex(i));
                        mesh.cornerTexCoords[corner] = new Vector2f(texCoord.getX(), texCoord.getY());
                    }
                    if (face.containsNormalIndices()) {
                        FloatTuple normal = obj.getNormal(face.getNormalIndex(i));
                        mesh.cornerNormals[corner] = new Vector3f(normal.getX(), normal.getY(), normal.getZ());
                    }

                    final int vertNext = face.getVertexIndex((i + 1) % faceSize);
                    mesh.cornerEdges[corner] = getOrCreateEdge(edgeIndices, edgeVerts, vert, vertNext);
                }
            }

            mesh.edgeVerts = edgeVerts.toIntArray();
            return mesh;
        }

        private static int getOrCreateEdge(Long2IntMap edgeIndices, IntArrayList edgeVerts, int vert1, int vert2) {
            final long key = ((long) Math.min(vert1, vert2) << 32) | (Math.max(vert1, vert2) & 0xFFFFFFFFL);
            int edge = edgeIndices.get(key);
            if (edge < 0) {
                edge = edgeVerts.size() / 2;
                edgeVerts.add(vert1);
                edgeVerts.add(vert2);
                edgeIndices.put(key, edge);
            }
            return edge;
        }
    }

    private static Vector3f[] readPositions(ReadableObj obj) {
        Vector3f[] positions = new Vector3f[obj.getNumVertices()];
        for (int i = 0; i < positions.length; i++) {
            FloatTuple vertex = obj.getVertex(i);
            positions[i] = new Vector3f(vertex.getX(), vertex.getY(), vertex.getZ());
        }
        return positions;
    }

    /**
     * Read the per-vertex colors of an OBJ, as written by
     * <code>ObjVertexConsumer</code>. Vertices without a color get a null entry.
     */
    private static Vector3f[] readColors(ReadableObj obj) {
        Vector3f[] colors = new Vector3f[obj.getNumVertices()];
        for (int i = 0; i < colors.length; i++) {
            FloatTuple vertex = obj.getVertex(i);
            if (vertex.getDimensions() >= 6) {
                colors[i] = new Vector3f(vertex.get(3), vertex.get(4), vertex.get(5));
            }
        }
        return colors;
    }

    /* -------------------------------------------------------------------- */
    /* Structures                                                           */

    private static final class WeldEdge {
        /* Indices relative to the original mesh. */
        final int edgeOrig;
        final int vertA;
        final int vertB;

        WeldEdge(int edgeOrig, int vertA, int vertB) {
            this.edgeOrig = edgeOrig;
            this.vertA = vertA;
            this.vertB = vertB;
        }
    }

    /**
     * The original C++ overlays the <code>flag</code> field with <code>edge</code>
     * through a union, so a collapsed loop is one whose edge is
     * {@link #ELEM_COLLAPSED}.
     */
    private static final class WeldLoop {
        /* Indices relative to the original mesh. */
        int edge;
        int vert;
        int loopOrig;
        int loopNext;

        void collapse() {
            edge = ELEM_COLLAPSED;
        }
    }

    /**
     * As with {@link WeldLoop}, the original overlays <code>flag</code> with
     * <code>poly_dst</code>.
     */
    private static final class WeldPoly {
        /* Indices relative to the original mesh. */
        int polyDst;
        int polyOrig;
        int loopStart;
        int loopEnd;

        /* To find groups. */
        int loopCtxStart;
        int loopCtxLen;

        boolean isCollapsed() {
            return polyDst == ELEM_COLLAPSED;
        }

        void collapse() {
            polyDst = ELEM_COLLAPSED;
        }
    }

    private static final class WeldMesh {
        /*
         * Indicates the index of elements that will participate in the creation of
         * groups. These groups are used in attribute interpolation (`doMixData`).
         */
        IntArrayList doubleVerts = new IntArrayList();

        /** Group of edges to be merged. */
        int[] edgeDestMap;
        int[] vertDestMap;

        /** References all polygons and loops that will be affected. */
        List<WeldLoop> wloop;
        List<WeldPoly> wpoly;
        int wpolyNewLen;

        /*
         * From the actual index of the element in the mesh, it indicates what is the
         * index of the weld element above.
         */
        int[] loopMap;
        int[] faceMap;

        int vertKillLen;
        int loopKillLen;
        /** Including the new polygons. */
        int faceKillLen;

        /** Size of the affected face with more sides. */
        int maxFaceLen;
    }

    private static final class WeldLoopOfPolyIter {
        int loopIter;
        int loopEnd;

        /* Weld group. */
        int loopCtxStart;
        int loopCtxLen;
        /** May be null when the caller doesn't need corner groups. */
        int[] group;

        List<WeldLoop> wloop;
        int[] cornerVerts;
        int[] cornerEdges;
        int[] loopMap;

        /* Return */
        int groupLen;
        int v;
        int e;

        boolean begin(WeldPoly wp, List<WeldLoop> wloop, int[] cornerVerts, int[] cornerEdges, int[] loopMap,
                      int[] groupBuffer) {
            if (wp.isCollapsed()) {
                return false;
            }

            this.loopIter = wp.loopStart;
            this.loopEnd = wp.loopEnd;
            this.loopCtxStart = wp.loopCtxStart;
            this.loopCtxLen = wp.loopCtxLen;

            this.wloop = wloop;
            this.cornerVerts = cornerVerts;
            this.cornerEdges = cornerEdges;
            this.loopMap = loopMap;
            this.group = groupBuffer;
            this.groupLen = 0;

            return next();
        }

        boolean next() {
            if (loopIter > loopEnd) {
                return false;
            }

            final int l = loopIter;
            int lNext = l + 1;

            final int loopCtx = loopMap[l];
            if (loopCtx != OUT_OF_CONTEXT) {
                WeldLoop wl = wloop.get(loopCtx);
                v = wl.vert;
                e = wl.edge;
                if (wl.loopNext > l) {
                    /* Allow the loop to break. */
                    lNext = wl.loopNext;
                }

                if (group != null) {
                    groupLen = 0;
                    for (int i = 0; i < loopCtxLen; i++) {
                        WeldLoop other = wloop.get(loopCtxStart + i);
                        if (other.vert == v) {
                            group[groupLen++] = other.loopOrig;
                        }
                    }
                }
            } else {
                v = cornerVerts[l];
                e = cornerEdges[l];
                if (group != null) {
                    group[0] = l;
                    groupLen = 1;
                }
            }

            loopIter = lNext;
            return true;
        }
    }

    /* -------------------------------------------------------------------- */
    /* Merge map                                                            */

    /**
     * Build a merge map that welds every vertex into the lowest-indexed vertex
     * within <code>mergeDistance</code> of it. This replaces Blender's KD-tree
     * duplicate search with a uniform grid, which behaves the same for the small,
     * evenly distributed distances this is used with.
     *
     * @param colors         When non-null, two vertices only merge if their
     *                       entries here are within <code>colorThreshold</code> of
     *                       each other, so color discontinuities stay sharp.
     *                       Entries may themselves be null for vertices without a
     *                       color.
     * @param colorThreshold How different two vertex colors are allowed to be for
     *                       them to still be considered equal for merging
     *                       purposes. Normalized: <code>0</code> requires colors
     *                       to match exactly, <code>1</code> allows any two colors
     *                       (assumed to be in <code>[0, 1]</code> per channel) to
     *                       merge. Ignored when <code>colors</code> is null.
     */
    private static MergeMap buildDistanceMergeMap(Vector3f[] positions, Vector3f[] colors, float mergeDistance,
                                                   float colorThreshold) {
        final int numVerts = positions.length;
        int[] vertDestMap = new int[numVerts];
        Arrays.fill(vertDestMap, OUT_OF_CONTEXT);

        if (numVerts == 0 || !(mergeDistance > 0)) {
            return new MergeMap(vertDestMap, 0);
        }

        Map<Cell, IntArrayList> grid = new HashMap<>();
        for (int i = 0; i < numVerts; i++) {
            grid.computeIfAbsent(Cell.of(positions[i], mergeDistance), cell -> new IntArrayList()).add(i);
        }

        final float mergeDistSq = mergeDistance * mergeDistance;
        /* The squared distance between two normalized colors ranges from 0 to MAX_COLOR_DISTANCE_SQ. */
        final float colorThresholdSq = colorThreshold * colorThreshold * MAX_COLOR_DISTANCE_SQ;
        int vertKillLen = 0;

        for (int i = 0; i < numVerts; i++) {
            /* Vertices that already merged into something else can't become targets. */
            if (vertDestMap[i] != OUT_OF_CONTEXT) {
                continue;
            }

            final Vector3f pos = positions[i];
            final Cell base = Cell.of(pos, mergeDistance);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        IntArrayList bucket = grid.get(new Cell(base.x() + x, base.y() + y, base.z() + z));
                        if (bucket == null) {
                            continue;
                        }
                        for (int j : bucket) {
                            if (j <= i || vertDestMap[j] != OUT_OF_CONTEXT) {
                                continue;
                            }
                            if (colors != null && !colorsWithinThreshold(colors[i], colors[j], colorThresholdSq)) {
                                /* Merging these would smear one vertex color into the other. */
                                continue;
                            }
                            if (pos.distanceSquared(positions[j]) <= mergeDistSq) {
                                vertDestMap[j] = i;
                                vertDestMap[i] = i;
                                vertKillLen++;
                            }
                        }
                    }
                }
            }
        }

        return new MergeMap(vertDestMap, vertKillLen);
    }

    /**
     * @return True if two (possibly null) vertex colors have a squared distance no
     *         greater than {@code thresholdSq}. Two null colors are considered
     *         equal; a null and a non-null color are not.
     */
    private static boolean colorsWithinThreshold(Vector3f a, Vector3f b, float thresholdSq) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.distanceSquared(b) <= thresholdSq;
    }

    private record Cell(int x, int y, int z) {
        static Cell of(Vector3f pos, float size) {
            return new Cell((int) Math.floor(pos.x / size), (int) Math.floor(pos.y / size),
                    (int) Math.floor(pos.z / size));
        }
    }

    /* -------------------------------------------------------------------- */
    /* Vert API                                                             */

    /**
     * Create a weld verts context.
     *
     * @return The context weld vertices.
     */
    private static IntArrayList weldVertCtxAllocAndSetup(int[] vertDestMap, int vertKillLen) {
        IntArrayList wvert = new IntArrayList(Math.min(2 * vertKillLen, vertDestMap.length));

        for (int i = 0; i < vertDestMap.length; i++) {
            if (vertDestMap[i] != OUT_OF_CONTEXT) {
                final int vertDest = vertDestMap[i];
                wvert.add(i);

                if (vertDestMap[vertDest] != vertDest) {
                    /*
                     * The target vertex is also part of the context and needs to be referenced.
                     * `vertDestMap` could already indicate this from the beginning, but for better
                     * compatibility, it is done here as well.
                     */
                    vertDestMap[vertDest] = vertDest;
                    wvert.add(vertDest);
                }
            }
        }
        return wvert;
    }

    /* -------------------------------------------------------------------- */
    /* Edge API                                                             */

    private record EdgeCtx(List<WeldEdge> wedge, int edgeCollapsedLen) {
    }

    /**
     * Allocate weld edges.
     *
     * @param edgeDestMap Filled with the first step of the map of indices pointing at
     *                    edges that will be merged.
     */
    private static EdgeCtx weldEdgeCtxAllocAndFindCollapsed(int[] edgeVerts, int[] vertDestMap, int[] edgeDestMap) {
        /* Edge context. */
        int edgeCollapsedLen = 0;

        final int numEdges = edgeVerts.length / 2;
        List<WeldEdge> wedge = new ArrayList<>(numEdges);

        for (int i = 0; i < numEdges; i++) {
            final int v1 = edgeVerts[i * 2];
            final int v2 = edgeVerts[i * 2 + 1];
            final int vDest1 = vertDestMap[v1];
            final int vDest2 = vertDestMap[v2];
            if (vDest1 == OUT_OF_CONTEXT && vDest2 == OUT_OF_CONTEXT) {
                edgeDestMap[i] = OUT_OF_CONTEXT;
                continue;
            }

            final int vertA = (vDest1 == OUT_OF_CONTEXT) ? v1 : vDest1;
            final int vertB = (vDest2 == OUT_OF_CONTEXT) ? v2 : vDest2;

            if (vertA == vertB) {
                edgeDestMap[i] = ELEM_COLLAPSED;
                edgeCollapsedLen++;
            } else {
                wedge.add(new WeldEdge(i, vertA, vertB));
                edgeDestMap[i] = i;
            }
        }

        return new EdgeCtx(wedge, edgeCollapsedLen);
    }

    /**
     * Fills <code>edgeDestMap</code> indicating the duplicated edges.
     *
     * @param weldEdges   Candidate edges for merging (edges that don't collapse and
     *                    that have at least one weld vertex).
     * @param mvertNum    Number of vertices in the source mesh.
     * @param edgeDestMap Resulting map of indices pointing the source edges to each
     *                    target.
     * @return The number of duplicate edges to be destroyed.
     */
    private static int weldEdgeFindDoubles(List<WeldEdge> weldEdges, int mvertNum, int[] edgeDestMap) {
        /* Setup edge overlap. */
        int edgeDoubleKillLen = 0;

        if (weldEdges.isEmpty()) {
            return edgeDoubleKillLen;
        }

        /* Add +1 to allow calculation of the length of the last group. */
        int[] vLinks = new int[mvertNum + 1];

        for (WeldEdge we : weldEdges) {
            vLinks[we.vertA]++;
            vLinks[we.vertB]++;
        }

        int linkLen = 0;
        for (int i = 0; i < mvertNum; i++) {
            linkLen += vLinks[i];
            vLinks[i] = linkLen;
        }
        vLinks[mvertNum] = linkLen;

        int[] linkEdgeBuffer = new int[linkLen];

        /* Use a reverse for loop to ensure that indexes are assigned in ascending order. */
        for (int i = weldEdges.size(); i-- > 0; ) {
            final WeldEdge we = weldEdges.get(i);
            linkEdgeBuffer[--vLinks[we.vertA]] = i;
            linkEdgeBuffer[--vLinks[we.vertB]] = i;
        }

        for (int i = 0; i < weldEdges.size(); i++) {
            final WeldEdge we = weldEdges.get(i);
            if (edgeDestMap[we.edgeOrig] != we.edgeOrig) {
                /* Already a duplicate. */
                continue;
            }

            final int dstVertA = we.vertA;
            final int dstVertB = we.vertB;

            final int linkA = vLinks[dstVertA];
            final int linkB = vLinks[dstVertB];

            int edgesLenA = vLinks[dstVertA + 1] - linkA;
            int edgesLenB = vLinks[dstVertB + 1] - linkB;

            final int edgeOrig = we.edgeOrig;
            if (edgesLenA <= 1 || edgesLenB <= 1) {
                /*
                 * This edge would form a group with only one element. For better performance,
                 * mark these edges and avoid forming these groups.
                 */
                edgeDestMap[edgeOrig] = OUT_OF_CONTEXT;
                continue;
            }

            int edgesCtxA = linkA;
            int edgesCtxB = linkB;

            final int edgeDoubleLenPrev = edgeDoubleKillLen;
            for (; edgesLenA-- > 0; edgesCtxA++) {
                final int eCtxA = linkEdgeBuffer[edgesCtxA];
                if (eCtxA == i) {
                    continue;
                }
                while (edgesLenB > 0 && linkEdgeBuffer[edgesCtxB] < eCtxA) {
                    edgesCtxB++;
                    edgesLenB--;
                }
                if (edgesLenB == 0) {
                    break;
                }
                final int eCtxB = linkEdgeBuffer[edgesCtxB];
                if (eCtxA == eCtxB) {
                    final WeldEdge weB = weldEdges.get(eCtxB);
                    edgeDestMap[weB.edgeOrig] = edgeOrig;
                    edgeDoubleKillLen++;
                }
            }
            if (edgeDoubleLenPrev == edgeDoubleKillLen) {
                /*
                 * This edge would form a group with only one element. For better performance,
                 * mark these edges and avoid forming these groups.
                 */
                edgeDestMap[edgeOrig] = OUT_OF_CONTEXT;
            }
        }

        return edgeDoubleKillLen;
    }

    /* -------------------------------------------------------------------- */
    /* Poly and Loop API                                                    */

    /**
     * Allocate weld polygons and weld loops.
     *
     * @param weldMesh Loop and face members will be allocated here.
     */
    private static void weldPolyLoopCtxAlloc(int[] faceOffsets, int[] cornerVerts, int[] cornerEdges,
                                             WeldMesh weldMesh) {
        final int[] vertDestMap = weldMesh.vertDestMap;
        final int[] edgeDestMap = weldMesh.edgeDestMap;
        final int numFaces = faceOffsets.length - 1;

        /* Loop/poly context. */
        int[] loopMap = new int[cornerVerts.length];
        int[] faceMap = new int[numFaces];
        int wloopLen = 0;
        int wpolyLen = 0;
        int maxCtxPolyLen = 4;

        List<WeldLoop> wloop = new ArrayList<>(cornerVerts.length);
        List<WeldPoly> wpoly = new ArrayList<>(numFaces);

        for (int i = 0; i < numFaces; i++) {
            final int loopstart = faceOffsets[i];
            final int totloop = faceOffsets[i + 1] - loopstart;
            final int loopEnd = loopstart + totloop - 1;
            if (totloop == 0) {
                faceMap[i] = OUT_OF_CONTEXT;
                continue;
            }

            final int vFirst = cornerVerts[loopstart];
            final int vDestFirst = vertDestMap[vFirst];
            final boolean isVertFirstCtx = vDestFirst != OUT_OF_CONTEXT;

            int vNext = vFirst;
            int vDestNext = vDestFirst;
            boolean isVertNextCtx = isVertFirstCtx;

            final int prevWloopLen = wloopLen;
            for (int loopOrig = loopstart; loopOrig <= loopEnd; loopOrig++) {
                final int v = vNext;
                final int vDest = vDestNext;
                final boolean isVertCtx = isVertNextCtx;

                final int loopNext;
                if (loopOrig != loopEnd) {
                    loopNext = loopOrig + 1;
                    vNext = cornerVerts[loopNext];
                    vDestNext = vertDestMap[vNext];
                    isVertNextCtx = vDestNext != OUT_OF_CONTEXT;
                } else {
                    loopNext = loopstart;
                    vNext = vFirst;
                    vDestNext = vDestFirst;
                    isVertNextCtx = isVertFirstCtx;
                }

                if (isVertCtx || isVertNextCtx) {
                    final int e = cornerEdges[loopOrig];
                    final int eDest = edgeDestMap[e];
                    final boolean isEdgeCtx = eDest != OUT_OF_CONTEXT;

                    WeldLoop wl = new WeldLoop();
                    wl.vert = isVertCtx ? vDest : v;
                    wl.edge = isEdgeCtx ? eDest : e;
                    wl.loopOrig = loopOrig;
                    wl.loopNext = loopNext;
                    wloop.add(wl);

                    loopMap[loopOrig] = wloopLen++;
                } else {
                    loopMap[loopOrig] = OUT_OF_CONTEXT;
                }
            }

            if (wloopLen != prevWloopLen) {
                final int loopCtxLen = wloopLen - prevWloopLen;

                WeldPoly wp = new WeldPoly();
                wp.polyDst = OUT_OF_CONTEXT;
                wp.polyOrig = i;
                wp.loopStart = loopstart;
                wp.loopEnd = loopEnd;
                wp.loopCtxStart = prevWloopLen;
                wp.loopCtxLen = loopCtxLen;
                wpoly.add(wp);

                faceMap[i] = wpolyLen++;
                /*
                 * Unlike upstream, which only grows this for faces with more than five corners,
                 * every context face is accounted for so the group buffer can never overflow.
                 */
                maxCtxPolyLen = Math.max(maxCtxPolyLen, totloop);
            } else {
                faceMap[i] = OUT_OF_CONTEXT;
            }
        }

        weldMesh.wloop = wloop;
        weldMesh.wpoly = wpoly;
        weldMesh.wpolyNewLen = 0;
        weldMesh.loopMap = loopMap;
        weldMesh.faceMap = faceMap;
        weldMesh.maxFaceLen = maxCtxPolyLen;
    }

    /** Kill counters, replacing the C++ pointer out-parameters. */
    private static final class KillCount {
        int polyKill;
        int loopKill;
    }

    private static void weldPolySplitRecursive(int polyLoopLen, int[] vertDestMap, WeldPoly wp, WeldMesh weldMesh,
                                               KillCount kill) {
        if (polyLoopLen < 3) {
            return;
        }

        final int[] loopMap = weldMesh.loopMap;
        final List<WeldLoop> wloop = weldMesh.wloop;

        int loopKill = 0;

        int loopEnd = wp.loopEnd;
        final int loopCtxEnd = loopMap[loopEnd];
        WeldLoop wlaPrev = (loopCtxEnd != OUT_OF_CONTEXT) ? wloop.get(loopCtxEnd) : null;
        int la = wp.loopStart;
        do {
            final int loopCtxA = loopMap[la];
            if (loopCtxA == OUT_OF_CONTEXT) {
                la++;
                wlaPrev = null;
                continue;
            }

            WeldLoop wla = wloop.get(loopCtxA);

            final int vertA = wla.vert;
            if (vertDestMap[vertA] == OUT_OF_CONTEXT) {
                /* Only test vertices that will be merged. */
                la = wla.loopNext;
                wlaPrev = wla;
                continue;
            }

            int distA = 1;
            int lbPrev = la;
            WeldLoop wlbPrev = wla;
            int lb = wla.loopNext;
            do {
                final int loopCtxB = loopMap[lb];
                if (loopCtxB == OUT_OF_CONTEXT) {
                    distA++;
                    lbPrev = lb;
                    wlbPrev = null;
                    lb++;
                    continue;
                }

                WeldLoop wlb = wloop.get(loopCtxB);
                final int vertB = wlb.vert;
                if (vertA != vertB) {
                    distA++;
                    lbPrev = lb;
                    wlbPrev = wlb;
                    lb = wlb.loopNext;
                    continue;
                }

                int distB = polyLoopLen - distA;

                if (distA == 1 || distB == 1) {
                    /* One of the two loops is already collapsed; nothing to do. */
                } else if (distA == 2 && distB == 2) {
                    /*
                     * All loops are "collapsed". They could be flagged, but just the face is
                     * enough.
                     */
                    loopKill += 4;
                    wp.collapse();
                    kill.polyKill += 1;
                    kill.loopKill += loopKill;
                    /*
                     * Since all the loops are collapsed, avoid looping through them. This may
                     * result in wrong poly kill counts.
                     */
                    return;
                } else {
                    /*
                     * Upstream dereferences these unconditionally; they are null when the previous
                     * corner was out of context, so guard instead of crashing.
                     */
                    if (wlaPrev != null) {
                        wlaPrev.loopNext = lb;
                    }
                    if (wlbPrev != null) {
                        wlbPrev.loopNext = la;
                    }
                    if (wp.loopStart == la) {
                        wp.loopStart = lb;
                    }

                    if (distA == 2) {
                        wla.collapse();
                        if (wlbPrev != null) {
                            wlbPrev.collapse();
                        }
                        loopKill += 2;
                    } else if (distB == 2) {
                        wlb.collapse();
                        if (wlaPrev != null) {
                            wlaPrev.collapse();
                        }
                        loopKill += 2;

                        wp.loopStart = la;
                        wp.loopEnd = loopEnd = lbPrev;

                        polyLoopLen = distA;
                        break;
                    } else {
                        WeldPoly newTest = new WeldPoly();
                        newTest.polyDst = OUT_OF_CONTEXT;
                        newTest.polyOrig = wp.polyOrig;
                        newTest.loopStart = la;
                        newTest.loopEnd = lbPrev;
                        newTest.loopCtxStart = wp.loopCtxStart;
                        newTest.loopCtxLen = wp.loopCtxLen;
                        weldMesh.wpoly.add(newTest);
                        weldMesh.wpolyNewLen++;

                        weldPolySplitRecursive(distA, vertDestMap, newTest, weldMesh, kill);
                    }

                    la = lb;
                    wla = wlb;
                    polyLoopLen = distB;

                    distA = 1;
                }

                wlbPrev = wlb;
                lbPrev = lb;
                lb = wlb.loopNext;
            } while (lbPrev != loopEnd);

            wlaPrev = wla;
            if (la == loopEnd) {
                /* No need to start again. */
                break;
            }
            la = wla.loopNext;
        } while (la != loopEnd);

        kill.loopKill += loopKill;
    }

    /**
     * Configure weld polygons and weld loops, collapsing the faces that lost their
     * edges and splitting the ones that touch themselves.
     *
     * @param remainEdgeCtxLen Context weld edges that won't be destroyed by merging.
     */
    private static void weldPolyLoopCtxSetupCollapsedAndSplit(int remainEdgeCtxLen, WeldMesh weldMesh) {
        if (remainEdgeCtxLen == 0) {
            weldMesh.faceKillLen = weldMesh.wpoly.size();
            weldMesh.loopKillLen = weldMesh.wloop.size();

            for (WeldPoly wp : weldMesh.wpoly) {
                wp.collapse();
            }
            return;
        }

        final List<WeldPoly> wpoly = weldMesh.wpoly;
        final List<WeldLoop> wloop = weldMesh.wloop;
        final int[] loopMap = weldMesh.loopMap;
        final int[] vertDestMap = weldMesh.vertDestMap;

        KillCount kill = new KillCount();

        /*
         * Setup poly/loop. `wpoly.size()` may change during the loop, so make it clear
         * that we are only working with the original `wpoly` items.
         */
        final int wpolyOriginalLen = wpoly.size();
        for (int i = 0; i < wpolyOriginalLen; i++) {
            WeldPoly wp = wpoly.get(i);
            int polyLoopLen = (wp.loopEnd - wp.loopStart) + 1;
            WeldLoop wlPrev = null;
            boolean changLoopStart = false;
            int l = wp.loopStart;
            do {
                final int loopCtx = loopMap[l];
                if (loopCtx == OUT_OF_CONTEXT) {
                    wlPrev = null;
                    continue;
                }

                WeldLoop wl = wloop.get(loopCtx);
                if (wl.edge == ELEM_COLLAPSED) {
                    wl.collapse();
                    if (polyLoopLen == 3) {
                        wp.collapse();
                        kill.polyKill++;
                        kill.loopKill += 3;
                        polyLoopLen = 0;
                        break;
                    }

                    if (l == wp.loopStart) {
                        changLoopStart = true;
                    }

                    kill.loopKill++;
                    polyLoopLen--;
                } else {
                    if (changLoopStart) {
                        wp.loopStart = l;
                        changLoopStart = false;
                    }
                    if (wlPrev != null) {
                        wlPrev.loopNext = l;
                    }
                    wlPrev = wl;
                }
            } while (l++ != wp.loopEnd);

            if (polyLoopLen != 0) {
                if (wlPrev != null) {
                    wlPrev.loopNext = wp.loopStart;
                    wp.loopEnd = wlPrev.loopOrig;
                }

                weldPolySplitRecursive(polyLoopLen, vertDestMap, wp, weldMesh, kill);
            }
        }

        weldMesh.faceKillLen = kill.polyKill;
        weldMesh.loopKillLen = kill.loopKill;
    }

    private record DoublesResult(int[] doublesBuffer, int[] doublesOffsets, int doublesNum) {
    }

    /**
     * Find faces whose corner values are all shared with another face.
     *
     * @param polyCornerOffsets Offsets of each face into <code>corners</code>, with
     *                          one extra trailing entry.
     * @param polyNum           Number of faces.
     * @param corners           Corner values (edge indices) of every face.
     * @param cornerIndexMax    One past the largest value in <code>corners</code>.
     */
    private static DoublesResult polyFindDoubles(int[] polyCornerOffsets, int polyNum, int[] corners,
                                                 int cornerIndexMax) {
        /* Add +1 to allow calculation of the length of the last group. */
        int[] linkedFacesOffset = new int[cornerIndexMax + 1];

        for (int elemIndex : corners) {
            linkedFacesOffset[elemIndex]++;
        }

        int linkFacesBufferLen = 0;
        for (int elemIndex = 0; elemIndex < cornerIndexMax; elemIndex++) {
            linkFacesBufferLen += linkedFacesOffset[elemIndex];
            linkedFacesOffset[elemIndex] = linkFacesBufferLen;
        }
        linkedFacesOffset[cornerIndexMax] = linkFacesBufferLen;

        if (linkFacesBufferLen == 0) {
            return new DoublesResult(new int[0], new int[]{0}, 0);
        }

        int[] linkedFacesBuffer = new int[linkFacesBufferLen];

        /* Use a reverse for loop to ensure that indexes are assigned in ascending order. */
        for (int faceIndex = polyNum; faceIndex-- > 0; ) {
            for (int cornerIndex = polyCornerOffsets[faceIndex + 1] - 1;
                 cornerIndex >= polyCornerOffsets[faceIndex]; cornerIndex--) {
                final int elemIndex = corners[cornerIndex];
                linkedFacesBuffer[--linkedFacesOffset[elemIndex]] = faceIndex;
            }
        }

        int[] doublesBuffer = new int[polyNum];

        IntArrayList doublesOffsets = new IntArrayList((polyNum / 2) + 1);
        doublesOffsets.add(0);

        BitSet isDouble = new BitSet(polyNum);

        int doublesBufferNum = 0;
        int doublesNum = 0;
        for (int faceIndex = 0; faceIndex < polyNum; faceIndex++) {
            if (isDouble.get(faceIndex)) {
                continue;
            }

            final int cornerNum = polyCornerOffsets[faceIndex + 1] - polyCornerOffsets[faceIndex];
            if (cornerNum == 0) {
                continue;
            }

            /* Set or overwrite the first slot of the possible group. */
            doublesBuffer[doublesBufferNum] = faceIndex;

            final int cornerFirst = polyCornerOffsets[faceIndex];
            int elemIndex = corners[cornerFirst];
            int linkOffs = linkedFacesOffset[elemIndex];
            int facesANum = linkedFacesOffset[elemIndex + 1] - linkOffs;
            if (facesANum == 1) {
                continue;
            }

            /* Offset into `linkedFacesBuffer`, or into `doublesBuffer` once intersected. */
            int[] facesABuffer = linkedFacesBuffer;
            int facesA = linkOffs;
            int polyToTest;

            /* Skip polygons with lower index as these have already been checked. */
            do {
                polyToTest = facesABuffer[facesA];
                facesA++;
                facesANum--;
            } while (polyToTest != faceIndex);

            final int isectResult = doublesBufferNum + 1;

            /* `facesA` are the polygons connected to the first corner. So skip the first corner. */
            for (int cornerIndex = cornerFirst + 1; cornerIndex < cornerFirst + cornerNum; cornerIndex++) {
                elemIndex = corners[cornerIndex];
                linkOffs = linkedFacesOffset[elemIndex];
                int facesBNum = linkedFacesOffset[elemIndex + 1] - linkOffs;
                int facesB = linkOffs;

                /* Skip polygons with lower index as these have already been checked. */
                do {
                    polyToTest = linkedFacesBuffer[facesB];
                    facesB++;
                    facesBNum--;
                } while (polyToTest != faceIndex);

                doublesNum = intersect(facesABuffer, facesA, facesANum, linkedFacesBuffer, facesB, facesBNum,
                        isDouble, doublesBuffer, isectResult);

                if (doublesNum == 0) {
                    break;
                }

                /* Intersect the last result. */
                facesABuffer = doublesBuffer;
                facesA = isectResult;
                facesANum = doublesNum;
            }

            if (doublesNum != 0) {
                for (int i = 0; i < doublesNum; i++) {
                    isDouble.set(doublesBuffer[isectResult + i]);
                }
                doublesBufferNum += doublesNum;
                doublesOffsets.add(++doublesBufferNum);

                if ((doublesBufferNum + 1) == polyNum) {
                    /*
                     * The last slot is the remaining unduplicated face. Avoid checking intersection
                     * as there are no more slots left.
                     */
                    break;
                }
            }
        }

        return new DoublesResult(doublesBuffer, doublesOffsets.toIntArray(),
                doublesBufferNum - (doublesOffsets.size() - 1));
    }

    /**
     * Fills <code>rBuffer</code> with the intersection of two sorted, non-repeating
     * ranges of face indices. Safe to call with <code>rBuffer</code> aliasing
     * <code>a</code>, as the write position never runs ahead of the read position.
     *
     * @return The number of values written.
     */
    private static int intersect(int[] a, int aOff, int aLen, int[] b, int bOff, int bLen, BitSet isDouble,
                                 int[] rBuffer, int rOff) {
        int resultNum = 0;
        int indexA = 0;
        int indexB = 0;
        while (indexA < aLen && indexB < bLen) {
            final int valueA = a[aOff + indexA];
            final int valueB = b[bOff + indexB];
            if (valueA < valueB) {
                indexA++;
            } else if (valueB < valueA) {
                indexB++;
            } else {
                /*
                 * Equality. Do not add duplicates: as they are already in the original array,
                 * this can cause buffer overflow.
                 */
                if (!isDouble.get(valueA)) {
                    rBuffer[rOff + resultNum++] = valueA;
                }
                indexA++;
                indexB++;
            }
        }
        return resultNum;
    }

    private static void weldPolyFindDoubles(int[] cornerVerts, int[] cornerEdges, int medgeLen, WeldMesh weldMesh) {
        if (weldMesh.faceKillLen == weldMesh.wpoly.size()) {
            return;
        }

        final List<WeldPoly> wpoly = weldMesh.wpoly;
        final List<WeldLoop> wloop = weldMesh.wloop;
        final int[] loopMap = weldMesh.loopMap;

        final int faceLen = wpoly.size();
        int[] polyOffs = new int[faceLen + 1];
        IntArrayList newCornerEdges = new IntArrayList(Math.max(cornerVerts.length - weldMesh.loopKillLen, 0));

        WeldLoopOfPolyIter iter = new WeldLoopOfPolyIter();
        for (int faceIndex = 0; faceIndex < faceLen; faceIndex++) {
            final WeldPoly wp = wpoly.get(faceIndex);
            polyOffs[faceIndex] = newCornerEdges.size();

            if (!iter.begin(wp, wloop, cornerVerts, cornerEdges, loopMap, null)) {
                continue;
            }
            if (wp.polyDst != OUT_OF_CONTEXT) {
                continue;
            }

            do {
                newCornerEdges.add(iter.e);
            } while (iter.next());
        }
        polyOffs[faceLen] = newCornerEdges.size();

        DoublesResult doubles = polyFindDoubles(polyOffs, faceLen, newCornerEdges.toIntArray(), medgeLen);

        if (doubles.doublesNum() != 0) {
            int loopKillNum = 0;

            final int[] doublesOffsets = doubles.doublesOffsets();
            final int[] doublesBuffer = doubles.doublesBuffer();
            for (int i = 0; i < doublesOffsets.length - 1; i++) {
                final int polyDst = wpoly.get(doublesBuffer[doublesOffsets[i]]).polyOrig;

                for (int offset = doublesOffsets[i] + 1; offset < doublesOffsets[i + 1]; offset++) {
                    final int wpolyIndex = doublesBuffer[offset];
                    WeldPoly wp = wpoly.get(wpolyIndex);

                    wp.polyDst = polyDst;
                    loopKillNum += polyOffs[wpolyIndex + 1] - polyOffs[wpolyIndex];
                }
            }

            weldMesh.faceKillLen += doubles.doublesNum();
            weldMesh.loopKillLen += loopKillNum;
        }
    }

    /* -------------------------------------------------------------------- */
    /* Mesh API                                                             */

    private static WeldMesh weldMeshContextCreate(SourceMesh mesh, int[] vertDestMap, int vertKillLen,
                                                  boolean getDoubles) {
        WeldMesh weldMesh = new WeldMesh();

        IntArrayList wvert = weldVertCtxAllocAndSetup(vertDestMap, vertKillLen);
        weldMesh.vertKillLen = vertKillLen;

        weldMesh.edgeDestMap = new int[mesh.getNumEdges()];
        weldMesh.vertDestMap = vertDestMap;

        EdgeCtx edgeCtx = weldEdgeCtxAllocAndFindCollapsed(mesh.edgeVerts, vertDestMap, weldMesh.edgeDestMap);
        List<WeldEdge> wedge = edgeCtx.wedge();

        final int edgeDoubleKillLen = weldEdgeFindDoubles(wedge, mesh.getNumVerts(), weldMesh.edgeDestMap);

        weldPolyLoopCtxAlloc(mesh.faceOffsets, mesh.cornerVerts, mesh.cornerEdges, weldMesh);

        weldPolyLoopCtxSetupCollapsedAndSplit(wedge.size() - edgeDoubleKillLen, weldMesh);

        weldPolyFindDoubles(mesh.cornerVerts, mesh.cornerEdges, mesh.getNumEdges(), weldMesh);

        /*
         * Upstream also merges the edge records here. An OBJ has no edge records --
         * they were only synthesized to drive the algorithm -- so the merged edge list
         * would have nothing to be written to.
         */
        if (getDoubles) {
            weldMesh.doubleVerts = wvert;
        }

        return weldMesh;
    }

    /* -------------------------------------------------------------------- */
    /* Merging                                                              */

    /**
     * Create groups to merge, based on the provided <code>destMap</code>.
     *
     * @param destMap       Map that defines the source and target elements. The
     *                      source elements will be merged into the target. Each
     *                      target corresponds to a group.
     * @param doubleElems   Source and target elements in <code>destMap</code>, for
     *                      quick access.
     * @param groupsOffsets Filled with the offset each element group starts at. Must
     *                      be <code>destMap.length + 1</code> long.
     * @return Buffer containing the indices of all elements that merge.
     */
    private static int[] mergeGroupsCreate(int[] destMap, IntArrayList doubleElems, int[] groupsOffsets) {
        Arrays.fill(groupsOffsets, 0);

        for (int elemOrig : doubleElems) {
            groupsOffsets[destMap[elemOrig]]++;
        }

        int offs = 0;
        for (int i = 0; i < destMap.length; i++) {
            offs += groupsOffsets[i];
            groupsOffsets[i] = offs;
        }
        groupsOffsets[groupsOffsets.length - 1] = offs;

        int[] groupsBuffer = new int[offs];

        /* Use a reverse for loop to ensure that indices are assigned in ascending order. */
        for (int i = doubleElems.size(); i-- > 0; ) {
            final int elemOrig = doubleElems.getInt(i);
            groupsBuffer[--groupsOffsets[destMap[elemOrig]]] = elemOrig;
        }

        return groupsBuffer;
    }

    /**
     * @param finalMap        New index of each source element.
     * @param srcIndexOffsets Offsets into <code>srcIndexData</code> of each result
     *                        element, with one extra trailing entry.
     * @param srcIndexData    Source elements that make up each result element.
     */
    private record MergeResult(int[] finalMap, int[] srcIndexOffsets, int[] srcIndexData) {
        /** The number of result elements that were actually produced. */
        int getNumDest() {
            return srcIndexOffsets.length - 1;
        }

        int groupStart(int dst) {
            return srcIndexOffsets[dst];
        }

        int groupEnd(int dst) {
            return srcIndexOffsets[dst + 1];
        }
    }

    /**
     * Compute the new indices of a set of elements, along with the source elements
     * each result element is built from.
     *
     * @param destMap     Map that defines the source and target elements.
     * @param doubleElems Source and target elements in <code>destMap</code>.
     * @param destSize    Expected number of result elements.
     * @param doMixData   If true the target element will have its attributes
     *                    interpolated with all sources pointing to it.
     */
    private static MergeResult mergeCustomdataAll(int[] destMap, IntArrayList doubleElems, int destSize,
                                                  boolean doMixData) {
        final int sourceSize = destMap.length;
        IntArrayList srcIndexOffsets = new IntArrayList(Math.max(destSize + 1, 1));
        IntArrayList srcIndexData = new IntArrayList(sourceSize);

        /*
         * When mixing, `finalMap` doubles as the group offsets buffer while the groups
         * are being consumed; every slot is read before it is overwritten.
         */
        final int[] finalMap = new int[doMixData ? sourceSize + 1 : sourceSize];
        final int[] groupsBuffer = doMixData ? mergeGroupsCreate(destMap, doubleElems, finalMap) : null;

        boolean finalizeMap = false;
        int destIndex = 0;
        for (int i = 0; i < sourceSize; i++) {
            while (i < sourceSize && destMap[i] == OUT_OF_CONTEXT) {
                finalMap[i] = destIndex;
                srcIndexOffsets.add(srcIndexData.size());
                srcIndexData.add(i);
                destIndex++;
                i++;
            }

            if (i == sourceSize) {
                break;
            }
            if (destMap[i] == i) {
                srcIndexOffsets.add(srcIndexData.size());
                if (doMixData) {
                    /* Read the group before `finalMap[i]` clobbers its offset. */
                    final int groupStart = finalMap[i];
                    final int groupEnd = finalMap[i + 1];
                    for (int j = groupStart; j < groupEnd; j++) {
                        srcIndexData.add(groupsBuffer[j]);
                    }
                } else {
                    srcIndexData.add(i);
                }
                finalMap[i] = destIndex;
                destIndex++;
            } else if (destMap[i] == ELEM_COLLAPSED) {
                /* Any value will do. This field must not be accessed anymore. */
                finalMap[i] = 0;
            } else {
                final int elemDest = destMap[i];
                if (elemDest < i) {
                    finalMap[i] = finalMap[elemDest];
                } else {
                    /* Mark as negative to set at the end. */
                    finalMap[i] = -elemDest;
                    finalizeMap = true;
                }
            }
        }

        if (finalizeMap) {
            for (int i = 0; i < sourceSize; i++) {
                if (finalMap[i] < 0) {
                    finalMap[i] = finalMap[-finalMap[i]];
                }
            }
        }

        srcIndexOffsets.add(srcIndexData.size());

        return new MergeResult(finalMap, srcIndexOffsets.toIntArray(), srcIndexData.toIntArray());
    }

    /* -------------------------------------------------------------------- */
    /* Mesh Vertex Merging                                                  */

    private static Obj createMergedMesh(SourceMesh mesh, int[] vertDestMap, int removedVertexCount,
                                        boolean doMixData) {
        final int[] srcCornerVerts = mesh.cornerVerts;
        final int[] srcCornerEdges = mesh.cornerEdges;
        final int numSrcFaces = mesh.getNumFaces();

        WeldMesh weldMesh = weldMeshContextCreate(mesh, vertDestMap, removedVertexCount, doMixData);

        Obj result = Objs.create();
        if (!mesh.mtlFileNames.isEmpty()) {
            result.setMtlFileNames(mesh.mtlFileNames);
        }

        /* Vertices. */

        MergeResult verts = mergeCustomdataAll(vertDestMap, weldMesh.doubleVerts,
                mesh.getNumVerts() - weldMesh.vertKillLen, doMixData);
        final int[] vertFinalMap = verts.finalMap();

        for (int dstVert = 0; dstVert < verts.getNumDest(); dstVert++) {
            final int start = verts.groupStart(dstVert);
            final int end = verts.groupEnd(dstVert);

            Vector3f position = new Vector3f();
            Vector3f color = null;
            int colorCount = 0;
            for (int i = start; i < end; i++) {
                final int srcVert = verts.srcIndexData()[i];
                position.add(mesh.vertPositions[srcVert]);
                Vector3f srcColor = mesh.vertColors[srcVert];
                if (srcColor != null) {
                    color = (color == null) ? new Vector3f(srcColor) : color.add(srcColor);
                    colorCount++;
                }
            }
            position.div(end - start);

            if (color != null) {
                result.addVertex(new ColoredVertex(position, color.div(colorCount)));
            } else {
                result.addVertex(position.x, position.y, position.z);
            }
        }

        /* Faces/loops. */

        FaceWriter faces = new FaceWriter(result, mesh);
        int[] groupBuffer = new int[Math.max(weldMesh.maxFaceLen, 1)];
        int[] singleCorner = new int[1];
        WeldLoopOfPolyIter iter = new WeldLoopOfPolyIter();

        for (int i = 0; i < numSrcFaces; i++) {
            final int polyCtx = weldMesh.faceMap[i];
            if (polyCtx == OUT_OF_CONTEXT) {
                for (int loopOrig = mesh.faceOffsets[i]; loopOrig < mesh.faceOffsets[i + 1]; loopOrig++) {
                    singleCorner[0] = loopOrig;
                    faces.addCorner(vertFinalMap[srcCornerVerts[loopOrig]], singleCorner, 1);
                }
            } else {
                final WeldPoly wp = weldMesh.wpoly.get(polyCtx);
                if (!iter.begin(wp, weldMesh.wloop, srcCornerVerts, srcCornerEdges, weldMesh.loopMap, groupBuffer)) {
                    continue;
                }
                if (wp.polyDst != OUT_OF_CONTEXT) {
                    continue;
                }
                do {
                    faces.addCorner(vertFinalMap[iter.v], iter.group, iter.groupLen);
                } while (iter.next());
            }

            faces.finishFace(i);
        }

        /*
         * New polygons. NOTE: The number of "src" and "new" faces might not match
         * `wpolyNewLen`.
         */
        for (int i = weldMesh.wpoly.size() - weldMesh.wpolyNewLen; i < weldMesh.wpoly.size(); i++) {
            final WeldPoly wp = weldMesh.wpoly.get(i);

            if (!iter.begin(wp, weldMesh.wloop, srcCornerVerts, srcCornerEdges, weldMesh.loopMap, groupBuffer)) {
                continue;
            }
            if (wp.polyDst != OUT_OF_CONTEXT) {
                continue;
            }
            do {
                faces.addCorner(vertFinalMap[iter.v], iter.group, iter.groupLen);
            } while (iter.next());

            /*
             * Blender leaves face attributes at their default here; an OBJ face without a
             * material is useless, so inherit them from the face this was split off of.
             */
            faces.finishFace(wp.polyOrig);
        }

        return result;
    }

    /**
     * Accumulates the corners of one result face and writes it out, mixing the
     * texture coordinate and normal of each corner from the source corners that
     * merged into it and de-duplicating the results as they are added.
     */
    private static final class FaceWriter {
        private final Obj out;
        private final SourceMesh mesh;

        private final IntArrayList faceVerts = new IntArrayList();
        private final IntArrayList faceTexCoords = new IntArrayList();
        private final IntArrayList faceNormals = new IntArrayList();

        private final Object2IntMap<Vector2f> texCoordIndices = new Object2IntOpenHashMap<>();
        private final Object2IntMap<Vector3f> normalIndices = new Object2IntOpenHashMap<>();

        private String activeMaterial;
        private Set<String> activeGroups;

        FaceWriter(Obj out, SourceMesh mesh) {
            this.out = out;
            this.mesh = mesh;
            texCoordIndices.defaultReturnValue(-1);
            normalIndices.defaultReturnValue(-1);
        }

        /**
         * @param vert     Result vertex index of this corner.
         * @param group    Source corners that merged into this corner.
         * @param groupLen Number of valid entries in <code>group</code>.
         */
        void addCorner(int vert, int[] group, int groupLen) {
            faceVerts.add(vert);
            faceTexCoords.add(mixTexCoord(group, groupLen));
            faceNormals.add(mixNormal(group, groupLen));
        }

        /**
         * Emit the accumulated corners as a face, taking its material and groups from a
         * source face.
         *
         * @param srcFace Source face to inherit attributes from.
         */
        void finishFace(int srcFace) {
            if (faceVerts.isEmpty()) {
                return;
            }

            /* Texture coordinates and normals are all-or-nothing per face. */
            int[] v = faceVerts.toIntArray();
            int[] vt = faceTexCoords.contains(-1) ? null : faceTexCoords.toIntArray();
            int[] vn = faceNormals.contains(-1) ? null : faceNormals.toIntArray();

            faceVerts.clear();
            faceTexCoords.clear();
            faceNormals.clear();

            /*
             * A face only records an activation when it differs from the previous one.
             * Neither can go back to null: they are resolved by carrying forward, so null
             * only ever means "nothing was activated yet".
             */
            String material = mesh.faceMaterials[srcFace];
            if (material != null && !material.equals(activeMaterial)) {
                out.setActiveMaterialGroupName(material);
                activeMaterial = material;
            }
            Set<String> groups = mesh.faceGroups.get(srcFace);
            if (groups != null && !Objects.equals(groups, activeGroups)) {
                out.setActiveGroupNames(groups);
                activeGroups = groups;
            }

            out.addFace(v, vt, vn);
        }

        /** @return The index of the mixed texture coordinate, or -1 if there is none. */
        private int mixTexCoord(int[] group, int groupLen) {
            Vector2f mixed = null;
            int count = 0;
            for (int i = 0; i < groupLen; i++) {
                Vector2f value = mesh.cornerTexCoords[group[i]];
                if (value == null) {
                    continue;
                }
                mixed = (mixed == null) ? new Vector2f(value) : mixed.add(value);
                count++;
            }
            if (mixed == null) {
                return -1;
            }
            mixed.div(count);

            int index = texCoordIndices.getInt(mixed);
            if (index < 0) {
                index = out.getNumTexCoords();
                texCoordIndices.put(mixed, index);
                out.addTexCoord(mixed.x, mixed.y);
            }
            return index;
        }

        /** @return The index of the mixed normal, or -1 if there is none. */
        private int mixNormal(int[] group, int groupLen) {
            Vector3f mixed = null;
            int count = 0;
            for (int i = 0; i < groupLen; i++) {
                Vector3f value = mesh.cornerNormals[group[i]];
                if (value == null) {
                    continue;
                }
                mixed = (mixed == null) ? new Vector3f(value) : mixed.add(value);
                count++;
            }
            if (mixed == null) {
                return -1;
            }
            /*
             * Unlike a generic mixed attribute, an averaged normal is only meaningful
             * normalized. Opposing normals can cancel out entirely, which has no meaningful
             * direction to normalize towards, so leave those alone.
             */
            if (count > 1) {
                mixed.div(count);
                if (mixed.lengthSquared() > 1e-12f) {
                    mixed.normalize();
                }
            }

            int index = normalIndices.getInt(mixed);
            if (index < 0) {
                index = out.getNumNormals();
                normalIndices.put(mixed, index);
                out.addNormal(mixed.x, mixed.y, mixed.z);
            }
            return index;
        }
    }
}
