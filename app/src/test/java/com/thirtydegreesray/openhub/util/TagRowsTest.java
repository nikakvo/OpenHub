package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TagRowsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    public void lightweightTag() {
        TagRows.Entry entry = TagRows.parse(json("{\"name\":\"v1.0\",\"target\":{\"__typename\":\"Commit\","
                + "\"oid\":\"0123456789abcdef0123456789abcdef01234567\",\"abbreviatedOid\":\"0123456\","
                + "\"committedDate\":\"2026-11-07T10:00:00Z\",\"messageHeadline\":\"Fix the build\"}}"));
        assertEquals("v1.0", entry.name);
        assertEquals("0123456789abcdef0123456789abcdef01234567", entry.commitOid);
        assertEquals("0123456", entry.shortOid);
        assertFalse(entry.annotated);
        assertEquals("Fix the build", entry.headline);
        assertEquals("2026-11-07T10:00:00Z", entry.date);
    }

    @Test
    public void annotatedTagUsesItsOwnMessageAndDate() {
        TagRows.Entry entry = TagRows.parse(json("{\"name\":\"v2\",\"target\":{\"__typename\":\"Tag\","
                + "\"oid\":\"tagobject\",\"abbreviatedOid\":\"tagobj\",\"message\":\"\\n  Version 2\\n\\nLong text\","
                + "\"tagger\":{\"date\":\"2026-11-08T08:00:00+02:00\"},"
                + "\"target\":{\"__typename\":\"Commit\",\"oid\":\"abcdef0123\",\"abbreviatedOid\":\"abcdef0\","
                + "\"committedDate\":\"2026-11-01T00:00:00Z\",\"messageHeadline\":\"Commit text\"}}}"));
        assertTrue(entry.annotated);
        assertEquals("abcdef0123", entry.commitOid);
        assertEquals("abcdef0", entry.shortOid);
        assertEquals("Version 2", entry.headline);
        assertEquals("2026-11-08T08:00:00+02:00", entry.date);
    }

    @Test
    public void annotatedTagWithoutMessageFallsBackToTheCommit() {
        TagRows.Entry entry = TagRows.parse(json("{\"name\":\"v3\",\"target\":{\"__typename\":\"Tag\","
                + "\"message\":\"\",\"tagger\":null,"
                + "\"target\":{\"__typename\":\"Commit\",\"oid\":\"1111111222\",\"abbreviatedOid\":\"\","
                + "\"committedDate\":\"2026-10-01T00:00:00Z\",\"messageHeadline\":\"From the commit\"}}}"));
        assertTrue(entry.annotated);
        assertEquals("1111111", entry.shortOid);
        assertEquals("From the commit", entry.headline);
        assertEquals("2026-10-01T00:00:00Z", entry.date);
    }

    @Test
    public void tagOfATagIsFollowedToTheCommit() {
        TagRows.Entry entry = TagRows.parse(json("{\"name\":\"outer\",\"target\":{\"__typename\":\"Tag\","
                + "\"message\":\"Outer\",\"tagger\":{\"date\":\"2026-11-02T00:00:00Z\"},"
                + "\"target\":{\"__typename\":\"Tag\",\"target\":{\"__typename\":\"Commit\","
                + "\"oid\":\"cafebabe00\",\"abbreviatedOid\":\"cafebab\",\"committedDate\":\"x\","
                + "\"messageHeadline\":\"Inner commit\"}}}}"));
        assertEquals("cafebabe00", entry.commitOid);
        assertEquals("Outer", entry.headline);
    }

    @Test
    public void tagOfATreeHasNoCommit() {
        TagRows.Entry entry = TagRows.parse(json("{\"name\":\"tree-tag\",\"target\":{\"__typename\":\"Tree\","
                + "\"oid\":\"deadbeef\",\"abbreviatedOid\":\"deadbee\"}}"));
        assertEquals("", entry.commitOid);
        assertEquals("", entry.shortOid);
        assertEquals("", entry.date);
    }

    @Test
    public void nodesWithoutNameAreSkipped() {
        assertNull(TagRows.parse(null));
        assertNull(TagRows.parse(json("{\"target\":null}")));
    }

    @Test
    public void writeAccess() {
        assertTrue(TagRows.canWrite("ADMIN"));
        assertTrue(TagRows.canWrite("MAINTAIN"));
        assertTrue(TagRows.canWrite("WRITE"));
        assertFalse(TagRows.canWrite("TRIAGE"));
        assertFalse(TagRows.canWrite("READ"));
        assertFalse(TagRows.canWrite(null));
    }

    @Test
    public void releasesQueryUsesVariables() {
        String query = TagRows.releasesQuery(2);
        assertTrue(query.startsWith("query($owner: String!, $repo: String!, $t0: String!, $t1: String!)"));
        assertTrue(query.contains("r0: release(tagName: $t0)"));
        assertTrue(query.contains("r1: release(tagName: $t1)"));
        assertFalse(query.contains("r2:"));
        assertEquals(count(query, '{'), count(query, '}'));
        assertEquals(count(TagRows.TAGS_QUERY, '{'), count(TagRows.TAGS_QUERY, '}'));
    }

    private static int count(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == c) n++;
        return n;
    }

    @Test
    public void releaseKinds() {
        assertEquals(TagRows.Kind.NONE, TagRows.kind(null));
        assertEquals(TagRows.Kind.DRAFT, TagRows.kind(json("{\"isDraft\":true,\"isLatest\":false}")));
        assertEquals(TagRows.Kind.PRE_RELEASE, TagRows.kind(json("{\"isDraft\":false,\"isPrerelease\":true}")));
        assertEquals(TagRows.Kind.LATEST, TagRows.kind(json("{\"isDraft\":false,\"isPrerelease\":false,\"isLatest\":true}")));
        assertEquals(TagRows.Kind.RELEASE, TagRows.kind(json("{\"isDraft\":false,\"isPrerelease\":false,\"isLatest\":false}")));
    }

    @Test
    public void releaseOfARow() {
        JsonObject repository = json("{\"r0\":null,\"r1\":{\"databaseId\":5,\"isDraft\":false}}");
        assertNull(TagRows.release(repository, 0));
        assertEquals(5, Json.integer(TagRows.release(repository, 1), "databaseId"));
        assertNull(TagRows.release(repository, 2));
    }

    @Test
    public void previousTag() {
        List<String> names = Arrays.asList("v3", "v2", "v1");
        assertEquals("v2", TagRows.previous(names, "v3"));
        assertEquals("v1", TagRows.previous(names, "v2"));
        assertNull(TagRows.previous(names, "v1"));
        assertNull(TagRows.previous(names, "v9"));
    }

    @Test
    public void dates() {
        assertEquals(1794117600000L, TagRows.date("2026-11-08T08:00:00+02:00").getTime());
        assertEquals(1794117600000L, TagRows.date("2026-11-08T06:00:00Z").getTime());
        assertNull(TagRows.date(""));
        assertNull(TagRows.date(null));
        assertNull(TagRows.date("not a date"));
    }

    @Test
    public void links() {
        assertEquals("https://api.github.com/repos/o/r/zipball/v1.0",
                TagRows.archiveUrl("o", "r", "v1.0", true));
        assertEquals("https://api.github.com/repos/o/r/tarball/release/1%2B2",
                TagRows.archiveUrl("o", "r", "release/1+2", false));
        assertEquals("r-release-1.0.zip", TagRows.archiveName("r", "release/1.0", true));
        assertEquals("r-v1.tar.gz", TagRows.archiveName("r", "v1", false));
        assertEquals("https://github.com/o/r/releases/tag/v1%20beta",
                TagRows.browserUrl("o", "r", "v1 beta"));
        assertEquals("https://github.com/o/r/tags", TagRows.tagsUrl("o", "r"));
    }
}
