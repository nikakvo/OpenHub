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

    @Test
    public void accentsDoNotMatter() {
        assertEquals("cancion", RepoFilter.lower("Canci\u00f3n"));
        assertEquals("strasse uber", RepoFilter.lower("Stra\u00dfe \u00dcber"));
        assertEquals("lodz", RepoFilter.lower("\u0141\u00f3d\u017a"));
        assertTrue(score("canci\u00f3n-app", null, null, null, "cancion") > RepoFilter.NONE);
        assertTrue(score("cancion-app", null, null, null, "Canci\u00f3n") > RepoFilter.NONE);
        assertTrue(score("tool", "Gr\u00fc\u00dfe aus K\u00f6ln", null, null, "grusse koln") > RepoFilter.NONE);
    }

    @Test
    public void otherScriptsKeepTheirLetters() {
        // Cyrillic \u0439 (short i) is its own letter, not \u0438 with a mark
        assertEquals("\u043c\u043e\u0439", RepoFilter.lower("\u041c\u043e\u0439"));
        assertEquals(RepoFilter.NONE, score("\u043c\u043e\u0438", null, null, null, "\u043c\u043e\u0439"));
        assertEquals("open hub", RepoFilter.lower("Open Hub"));
        assertEquals("", RepoFilter.lower(null));
    }
}
