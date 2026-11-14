package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentDraftsTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void keysPerPlace() {
        assertEquals("issue:nikakvo/openhub#12", CommentDrafts.issue("nikakvo", "OpenHub", 12));
        assertEquals("pull:nikakvo/openhub#12", CommentDrafts.pull("Nikakvo", "OpenHub", 12));
        assertEquals("discussion:o/r#3", CommentDrafts.discussion("o", "r", 3, null));
        assertEquals("discussion:o/r#3:DC_abc", CommentDrafts.discussion("o", "r", 3, "DC_abc"));
        assertEquals("gist:abc123", CommentDrafts.gist("abc123"));
    }

    @Test
    public void oldDraftsExpire() {
        assertFalse(CommentDrafts.expired(NOW - 29 * DAY, NOW));
        assertTrue(CommentDrafts.expired(NOW - 31 * DAY, NOW));
    }

    @Test
    public void expiredAndOldestBeyondTheLimitAreDropped() {
        Map<String, Long> saved = new HashMap<>();
        saved.put("old", NOW - 40 * DAY);
        for (int i = 0; i < CommentDrafts.MAX_DRAFTS + 2; i++) saved.put("d" + i, NOW - 1000L * (i + 1));
        List<String> drop = CommentDrafts.toDrop(saved, NOW);
        assertEquals(3, drop.size());
        assertTrue(drop.contains("old"));
        // the two oldest of the rest
        assertTrue(drop.contains("d" + (CommentDrafts.MAX_DRAFTS + 1)));
        assertTrue(drop.contains("d" + CommentDrafts.MAX_DRAFTS));
        assertFalse(drop.contains("d0"));
    }

    @Test
    public void nothingToDropWhenFew() {
        Map<String, Long> saved = new HashMap<>();
        saved.put("a", NOW);
        assertTrue(CommentDrafts.toDrop(saved, NOW).isEmpty());
    }
}
