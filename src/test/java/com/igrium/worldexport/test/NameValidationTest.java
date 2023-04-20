package com.igrium.worldexport.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.scaffoldeditor.worldexport.util.UtilFunctions;

import com.google.common.collect.ImmutableSet;

public class NameValidationTest {
    private Set<String> existingNames = ImmutableSet.of("test", "secondTest.1", "thirdTest", "thirdTest.1", "filename.png");

    @Test
    void testNoCollision() {
        String str = "noCollision";
        assertEquals(str, UtilFunctions.validateName(str, existingNames));
    }

    @Test
    void testNoCollisionDot() {
        String str = "noCollision.png";
        assertEquals(str, UtilFunctions.validateName(str, existing -> existingNames.contains(existing)));
    }

    @Test
    void testOneCollision() {
        assertEquals("test.1", UtilFunctions.validateName("test", existingNames));
    }

    @Test
    void testCustomPredicate() {
        assertEquals("secondTest.2", UtilFunctions.validateName("secondTest",
                name -> name.equals("secondTest") || existingNames.contains(name)));
    }

    @Test
    void testTwoCollisions() {
        assertEquals("thirdTest.2", UtilFunctions.validateName("thirdTest", existingNames));
    }

    @Test
    void testFilename() {
        assertEquals("filename.png.1", UtilFunctions.validateName("filename.png", existingNames));
    }
}
