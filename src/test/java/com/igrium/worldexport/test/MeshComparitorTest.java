package com.igrium.worldexport.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Test;
import org.scaffoldeditor.worldexport.util.MeshComparator;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjReader;

public class MeshComparitorTest {

    @Test
    void testSelf() {
        Obj obj = read(TestMeshes.CUBE1);
        assertTrue(new MeshComparator().meshEquals(obj, obj, 0, 0));
    }

    @Test
    void testIdentical() throws IOException {
        Obj obj = read(TestMeshes.CUBE1);
        Obj obj2 = read(TestMeshes.CUBE1);
        assertTrue(new MeshComparator().meshEquals(obj, obj2, 0, MeshComparator.NO_SORT));
    }

    @Test
    void testCongruent() throws IOException {
        Obj cube1 = read(TestMeshes.CUBE1);
        Obj cube2 = read(TestMeshes.CUBE2);

        assertTrue(new MeshComparator().meshEquals(cube1, cube2, 0, 0));
    }
    
    @Test
    void testUnsorted() throws IOException {
        Obj cube1 = read(TestMeshes.CUBE1);
        Obj cube2 = read(TestMeshes.CUBE2);

        assertFalse(new MeshComparator().meshEquals(cube1, cube2, 0, MeshComparator.NO_SORT));
    }

    @Test
    void testTris() throws IOException {
        Obj cube1 = read(TestMeshes.CUBE1);
        Obj cubeTris = read(TestMeshes.CUBE_TRIS);

        // assertTrue(new MeshComparator().meshEquals(cube1, cubeTris, 0, 0));
        MeshComparator comparator = new MeshComparator();
        assertTrue(comparator.meshEquals(cube1, cubeTris, 0, MeshComparator.LENIENT_FACE_MATCHING),
                "Triangulation should be ignored with lenient face matching.");
        assertFalse(comparator.meshEquals(cube1, cubeTris, 0, 0),
                "Triangulation matters with lenient face matching.");
    }

    @Test
    void testEpsilon() throws IOException {
        Obj cube1 = read(TestMeshes.CUBE1);
        Obj cubeSimilar = read(TestMeshes.CUBE_SIMILAR);
        
        MeshComparator comparator = new MeshComparator();
        assertTrue(comparator.meshEquals(cube1, cubeSimilar, .05f, 0),
                "With an epsilon of at least .05, this should succeed.");
        assertFalse(comparator.meshEquals(cube1, cubeSimilar, 0, 0),
                "With an epsilon under .05, this should fail.");
    }

    @Test
    void testDifferent() throws IOException {
        Obj cube1 = read(TestMeshes.CUBE1);
        Obj notCube = read(TestMeshes.NOT_CUBE);

        assertFalse(new MeshComparator().meshEquals(cube1, notCube, .05f, 0));
    }

    private Obj read(String obj) {
        try (StringReader reader = new StringReader(obj)) {
            return ObjReader.read(reader);
        } catch (IOException e) {
            throw new AssertionError("Failed to read the OBJ string.", e);
        }
    }
}
