package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RepoFilterTest {

    private static int score(String name, String description, String language, List<String> topics,
                             String query) {
        return RepoFilter.score(name, "nikakvo/" + name, description, language, topics,
                RepoFilter.words(query));
    }

    @Test
    public void words() {
        assertEquals(Arrays.asList("open", "hub"), RepoFilter.words("  Open   HUB "));
        assertTrue(RepoFilter.words("   ").isEmpty());
        assertTrue(RepoFilter.words(null).isEmpty());
    }

    @Test
    public void nameFirst() {
        assertEquals(RepoFilter.NAME_START, score("OpenHub", null, "Java", null, "open"));
        assertEquals(RepoFilter.NAME, score("OpenHub", null, "Java", null, "hub"));
        assertEquals(RepoFilter.OTHER, score("tears-id", "Device id module", "Shell", null, "module"));
        assertEquals(RepoFilter.NAME_START, score("OpenHub", null, null, null, "OPEN hub"));
    }

    @Test
    public void everyWordMustMatch() {
        List<String> topics = Collections.singletonList("magisk");
        assertEquals(RepoFilter.OTHER, score("tears-id", "Device id", "Shell", topics, "magisk shell"));
        assertEquals(RepoFilter.NONE, score("tears-id", "Device id", "Shell", topics, "magisk kotlin"));
        assertEquals("owner/name", RepoFilter.OTHER, score("tears-id", null, null, null, "nikakvo/tears"));
        assertEquals(RepoFilter.NONE, score("tears-id", null, null, null, "openhub"));
    }

    @Test
    public void nullFieldsAreEmpty() {
        assertEquals(RepoFilter.NONE, RepoFilter.score(null, null, null, null, null,
                RepoFilter.words("x")));
        assertEquals(RepoFilter.OTHER, RepoFilter.score(null, null, null, null, null,
                RepoFilter.words("")));
    }
}
