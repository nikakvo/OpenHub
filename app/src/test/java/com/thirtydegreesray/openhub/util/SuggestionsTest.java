package com.thirtydegreesray.openhub.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class SuggestionsTest {

    private static Suggestions.Edit edit(int start, int end, String text) {
        return new Suggestions.Edit(1, "a.txt", start, end, text, "alice", 42);
    }

    @Test
    public void parsesSuggestionBlocks() {
        assertEquals("new line", Suggestions.parse("Please:\n```suggestion\nnew line\n```\nthanks"));
        assertEquals("a\n  b", Suggestions.parse("```suggestion\r\na\r\n  b\r\n```"));
        assertEquals("", Suggestions.parse("```suggestion\n```"));
        assertEquals("x\n```js\ny", Suggestions.parse("````suggestion\nx\n```js\ny\n````"));
        assertEquals("t", Suggestions.parse("~~~ suggestion\nt\n~~~"));
        assertNull(Suggestions.parse("```js\ncode\n```"));
        assertNull(Suggestions.parse("```suggestion\nnot closed"));
        assertNull(Suggestions.parse(null));
        assertEquals("first", Suggestions.parse("```suggestion\nfirst\n```\n```suggestion\nsecond\n```"));
    }

    @Test
    public void appliesOneEdit() {
        assertEquals("one\nTWO\nthree\n", Suggestions.apply("one\ntwo\nthree\n",
                Collections.singletonList(edit(2, 2, "TWO"))));
        assertEquals("one\nthree", Suggestions.apply("one\ntwo\nthree",
                Collections.singletonList(edit(2, 2, ""))));
        assertEquals("one\r\nA\r\nB\r\nthree\r\n", Suggestions.apply("one\r\ntwo\r\nthree\r\n",
                Collections.singletonList(edit(2, 2, "A\nB"))));
        assertEquals("X\n", Suggestions.apply("a\nb\nc\n", Collections.singletonList(edit(1, 3, "X"))));
    }

    @Test
    public void appliesSeveralEditsBottomFirst() {
        List<Suggestions.Edit> edits = Arrays.asList(edit(1, 1, "A1\nA2"), edit(3, 4, "C"));
        assertEquals("A1\nA2\nb\nC\ne\n", Suggestions.apply("a\nb\nc\nd\ne\n", edits));
    }

    @Test
    public void refusesBadEdits() {
        try {
            Suggestions.apply("a\nb\n", Collections.singletonList(edit(3, 3, "x")));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            Suggestions.apply("a\nb\nc\n", Arrays.asList(edit(1, 2, "x"), edit(2, 3, "y")));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void checksTheCommentedLine() {
        assertEquals("  return b;", Suggestions.lastHunkLine("@@ -1,3 +1,3 @@\n int a;\n-return a;\n+  return b;"));
        assertEquals("ctx", Suggestions.lastHunkLine("@@ -1 +1 @@\n ctx"));
        assertNull(Suggestions.lastHunkLine("@@ -1 +1 @@\n-gone"));
        assertNull(Suggestions.lastHunkLine(""));
        Suggestions.Edit ok = new Suggestions.Edit(1, "a", 2, 2, "B", "x", 1, "b");
        assertEquals("a\nB\n", Suggestions.apply("a\nb\n", Collections.singletonList(ok)));
        Suggestions.Edit stale = new Suggestions.Edit(1, "a", 2, 2, "B", "x", 1, "old b");
        try {
            Suggestions.apply("a\nb\n", Collections.singletonList(stale));
            fail();
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void readsCommentsAndComparesChanges() {
        com.google.gson.JsonObject c = com.google.gson.JsonParser.parseString("{\"id\":5,\"path\":\"notes.txt\","
                + "\"line\":5,\"start_line\":4,\"side\":\"RIGHT\",\"start_side\":\"RIGHT\","
                + "\"body\":\"```suggestion\\nFOUR\\n```\",\"user\":{\"login\":\"bob\",\"id\":9},"
                + "\"diff_hunk\":\"@@ -0,0 +1,5 @@\\n+a\\n+b\\n+c\\n+d\\n+e\"}").getAsJsonObject();
        Suggestions.Edit e = Suggestions.fromComment(c);
        assertEquals(4, e.start);
        assertEquals(5, e.end);
        assertEquals("FOUR", e.text);
        assertEquals("e", e.expectedLast);
        assertEquals(9L, e.authorId);
        assertEquals(5L, e.commentId);
        c.add("line", com.google.gson.JsonNull.INSTANCE);
        assertNull(Suggestions.fromComment(c));
        Suggestions.Edit edited = new Suggestions.Edit(5, "notes.txt", 4, 5, "FOUR!", "bob", 9);
        org.junit.Assert.assertFalse(Suggestions.sameChange(e, edited));
        org.junit.Assert.assertTrue(Suggestions.sameChange(e, new Suggestions.Edit(5, "notes.txt", 4, 5, "FOUR", "x", 1)));
    }

    @Test
    public void creditsAuthors() {
        List<Suggestions.Edit> edits = Arrays.asList(
                new Suggestions.Edit(1, "a", 1, 1, "x", "alice", 42),
                new Suggestions.Edit(2, "b", 1, 1, "y", "Alice", 42),
                new Suggestions.Edit(3, "b", 3, 3, "z", "nikakvo", 7),
                new Suggestions.Edit(4, "c", 1, 1, "w", "bob", 0));
        assertEquals("Co-authored-by: alice <42+alice@users.noreply.github.com>\n"
                + "Co-authored-by: bob <bob@users.noreply.github.com>", Suggestions.coAuthors(edits, "NIKAKVO"));
        assertEquals("Why\n\nCo-authored-by: x", Suggestions.description(" Why ", "Co-authored-by: x"));
        assertEquals("Co-authored-by: x", Suggestions.description(null, "Co-authored-by: x"));
        Map<String, List<Suggestions.Edit>> files = Suggestions.byFile(edits);
        assertEquals(Arrays.asList("a", "b", "c"), new java.util.ArrayList<>(files.keySet()));
        assertEquals(2, files.get("b").size());
        assertEquals("Apply suggestions from code review", Suggestions.defaultMessage(2));
    }
}
