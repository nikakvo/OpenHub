package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PathSearchTest {

    private static final String PATH = "app/src/main/java/com/example/util/EdgeToEdge.java";

    @Test
    public void wordsAndQualifiers() {
        assertTrue(PathSearch.matches(PATH, "EdgeToEdge"));
        assertTrue(PathSearch.matches(PATH, "edge util"));
        assertTrue(PathSearch.matches(PATH, "edge extension:java"));
        assertFalse(PathSearch.matches(PATH, "edge extension:kt"));
        assertTrue(PathSearch.matches(PATH, "path:src/main filename:edge"));
        assertFalse(PathSearch.matches(PATH, "filename:util"));
        assertTrue("unknown qualifiers are ignored", PathSearch.matches(PATH, "edge language:java"));
        assertFalse(PathSearch.matches(PATH, "help"));
    }
}
