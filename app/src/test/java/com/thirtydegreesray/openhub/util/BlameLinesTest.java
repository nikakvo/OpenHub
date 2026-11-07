package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class BlameLinesTest {

    @Test
    public void splitLines() {
        assertEquals(Arrays.asList("a", "b"), BlameLines.splitLines("a\nb\n"));
        assertEquals(Arrays.asList("a", "b"), BlameLines.splitLines("a\r\nb"));
        assertEquals(Arrays.asList("a", "", "c"), BlameLines.splitLines("a\n\nc\n"));
        assertEquals(Arrays.asList("", "x"), BlameLines.splitLines("\nx"));
        assertTrue(BlameLines.splitLines("").isEmpty());
        assertTrue(BlameLines.splitLines(null).isEmpty());
        assertEquals(Arrays.asList("only"), BlameLines.splitLines("only"));
    }

    private static final String BLAME = "{\"ranges\":["
            + "{\"startingLine\":3,\"endingLine\":4,\"age\":1,\"commit\":{\"oid\":\"bbbb2222\","
            + "\"abbreviatedOid\":\"bbbb222\",\"messageHeadline\":\"Second\",\"committedDate\":\"2026-11-07T10:00:00Z\","
            + "\"author\":{\"name\":\"Tears Burn\",\"user\":{\"login\":\"nikakvo\"}}}},"
            + "{\"startingLine\":1,\"endingLine\":2,\"age\":10,\"commit\":{\"oid\":\"aaaa1111\","
            + "\"abbreviatedOid\":\"aaaa111\",\"messageHeadline\":\"First\",\"committedDate\":\"2025-01-01T00:00:00Z\","
            + "\"author\":{\"name\":\"Someone\",\"user\":null}}}"
            + "]}";

    @Test
    public void parseSortsAndReadsCommits() {
        JsonObject blame = JsonParser.parseString(BLAME).getAsJsonObject();
        List<BlameLines.Range> ranges = BlameLines.parse(blame);
        assertEquals(2, ranges.size());
        BlameLines.Range first = ranges.get(0);
        assertEquals(1, first.start);
        assertEquals(2, first.end);
        assertEquals(10, first.age);
        assertEquals("aaaa111", first.shortOid);
        assertEquals("First", first.headline);
        assertEquals("", first.login);
        assertEquals("Someone", first.who());
        assertNotNull(first.date);
        BlameLines.Range second = ranges.get(1);
        assertEquals("nikakvo", second.who());
        assertEquals("bbbb2222", second.oid);
        assertTrue(BlameLines.parse(null).isEmpty());
    }

    @Test
    public void rowsHeaderThenLines() {
        List<BlameLines.Range> ranges = BlameLines.parse(JsonParser.parseString(BLAME).getAsJsonObject());
        List<BlameLines.Row> rows = BlameLines.rows(ranges, Arrays.asList("l1", "l2", "l3", "l4"));
        assertEquals(6, rows.size());
        assertTrue(rows.get(0).isHeader());
        assertEquals("First", rows.get(0).range.headline);
        assertFalse(rows.get(1).isHeader());
        assertEquals(1, rows.get(1).number);
        assertEquals("l1", rows.get(1).text);
        assertEquals("aaaa1111", rows.get(1).owner.oid);
        assertNull(rows.get(1).range);
        assertTrue(rows.get(3).isHeader());
        assertEquals(4, rows.get(5).number);
        assertEquals("l4", rows.get(5).text);
    }

    @Test
    public void rowsOfAShortenedText() {
        List<BlameLines.Range> ranges = BlameLines.parse(JsonParser.parseString(BLAME).getAsJsonObject());
        // only 3 lines: range 3-4 keeps line 3
        List<BlameLines.Row> rows = BlameLines.rows(ranges, Arrays.asList("l1", "l2", "l3"));
        assertEquals(5, rows.size());
        assertEquals(3, rows.get(4).number);
        // only 2 lines: the second range has no line left, so no header either
        assertEquals(3, BlameLines.rows(ranges, Arrays.asList("l1", "l2")).size());
    }

    @Test
    public void ageColors() {
        assertEquals(0xFFF0883E, BlameLines.ageColor(1, true));
        assertEquals(0xFF5A636E, BlameLines.ageColor(10, true));
        assertEquals(0xFFB4BBC3, BlameLines.ageColor(10, false));
        assertEquals(BlameLines.ageColor(1, false), BlameLines.ageColor(0, false));
        assertEquals(BlameLines.ageColor(10, true), BlameLines.ageColor(42, true));
        int middle = BlameLines.ageColor(5, true);
        assertTrue(((middle >> 16) & 0xFF) < 0xF0 && ((middle >> 16) & 0xFF) > 0x5A);
    }
}
