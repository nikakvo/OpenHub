package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubIssuesTest {

    @Test
    public void parsesNumbersRefsAndLinks() {
        SubIssues.Ref r = SubIssues.parse("#12", "nikakvo", "test");
        assertEquals("nikakvo", r.owner);
        assertEquals("test", r.repo);
        assertEquals(12, r.number);
        assertEquals(7, SubIssues.parse(" 7 ", "nikakvo", "test").number);

        r = SubIssues.parse("nikakvo/OpenHub#5", "nikakvo", "test");
        assertEquals("OpenHub", r.repo);
        assertEquals(5, r.number);

        r = SubIssues.parse("https://github.com/nikakvo/OpenHub/issues/33#issuecomment-1", "x", "y");
        assertEquals("nikakvo", r.owner);
        assertEquals("OpenHub", r.repo);
        assertEquals(33, r.number);
        assertEquals(4, SubIssues.parse("github.com/a/b/issues/4", "x", "y").number);
        assertEquals(9, SubIssues.parse("https://api.github.com/repos/a/b/issues/9", "x", "y").number);

        assertNull(SubIssues.parse("", "x", "y"));
        assertNull(SubIssues.parse("#0", "x", "y"));
        assertNull(SubIssues.parse("crash on start", "x", "y"));
        assertNull(SubIssues.parse("https://github.com/a/b/pull/4", "x", "y"));
    }

    @Test
    public void repoOfApiUrl() {
        assertEquals("nikakvo/test", SubIssues.repoOf("https://api.github.com/repos/nikakvo/test"));
        assertEquals("nikakvo/test", SubIssues.repoOf("https://api.github.com/repos/nikakvo/test/"));
        assertEquals("", SubIssues.repoOf("https://api.github.com/users/nikakvo"));
        assertEquals("", SubIssues.repoOf(null));
    }

    @Test
    public void entryReadsGitHubJsonWith64BitIds() {
        JsonObject o = JsonParser.parseString("{\"id\": 3512345678901, \"number\": 3, \"title\": \"Child\","
                + " \"state\": \"closed\", \"state_reason\": \"not_planned\","
                + " \"repository_url\": \"https://api.github.com/repos/nikakvo/test\","
                + " \"html_url\": \"https://github.com/nikakvo/test/issues/3\","
                + " \"parent_issue_url\": \"https://api.github.com/repos/nikakvo/test/issues/1\"}").getAsJsonObject();
        SubIssues.Entry e = SubIssues.entry(o);
        assertEquals(3512345678901L, e.id);
        assertEquals(3, e.number);
        assertFalse(e.isOpen());
        assertTrue(e.isNotPlanned());
        assertTrue(e.hasParent);
        assertFalse(e.isPullRequest);
        assertTrue(e.sameRepo("NIKAKVO", "Test"));
        assertEquals("#3", SubIssues.label(e, "nikakvo", "test"));
        assertEquals("nikakvo/test#3", SubIssues.label(e, "nikakvo", "other"));

        JsonObject pr = JsonParser.parseString("{\"id\": 1, \"number\": 4, \"state\": \"open\","
                + " \"parent_issue_url\": null, \"pull_request\": {\"url\": \"x\"}}").getAsJsonObject();
        SubIssues.Entry p = SubIssues.entry(pr);
        assertTrue(p.isPullRequest);
        assertFalse(p.hasParent);
        assertTrue(p.isOpen());
    }

    private static SubIssues.Entry e(long id, int number, String title) {
        SubIssues.Entry e = new SubIssues.Entry();
        e.id = id;
        e.number = number;
        e.title = title;
        return e;
    }

    @Test
    public void candidatesSkipSelfParentCurrentAndPulls() {
        SubIssues.Entry pull = e(5, 5, "A pull request");
        pull.isPullRequest = true;
        List<SubIssues.Entry> open = Arrays.asList(e(1, 1, "Self"), e(2, 2, "Parent"),
                e(3, 3, "Already a child"), e(4, 14, "Crash on start"), pull, e(6, 41, "Docs"));
        List<SubIssues.Entry> current = new ArrayList<>();
        current.add(e(3, 3, "Already a child"));

        List<SubIssues.Entry> all = SubIssues.candidates(open, 1, 2, current, null);
        assertEquals(2, all.size());
        assertEquals(14, all.get(0).number);
        assertEquals(41, all.get(1).number);

        assertEquals(1, SubIssues.candidates(open, 1, 2, current, "crash").size());
        assertEquals(14, SubIssues.candidates(open, 1, 2, current, "#14").get(0).number);
        assertEquals(1, SubIssues.candidates(open, 1, 2, current, "4").size());
        assertEquals(0, SubIssues.candidates(open, 1, 2, current, "nothing").size());
        // no parent: 0 is not a real id
        assertEquals(3, SubIssues.candidates(open, 1, 0, current, "").size());
    }

    @Test
    public void moveGivesBeforeOrAfter() {
        List<SubIssues.Entry> list = Arrays.asList(e(10, 1, "a"), e(20, 2, "b"), e(30, 3, "c"));
        assertArrayEquals(new long[]{10, 0}, SubIssues.move(list, 1, true));
        assertArrayEquals(new long[]{0, 30}, SubIssues.move(list, 1, false));
        assertNull(SubIssues.move(list, 0, true));
        assertNull(SubIssues.move(list, 2, false));
        assertNull(SubIssues.move(list, 5, true));
        JsonArray empty = new JsonArray();
        assertEquals(0, SubIssues.entries(empty).size());
        assertEquals(0, SubIssues.entries(null).size());
    }

    @Test
    public void typedRefOffersOnlyUnknownIssues() {
        SubIssues.Entry pull = e(3, 3, "Stack PR");
        pull.isPullRequest = true;
        pull.owner = "nikakvo";
        pull.repo = "test";
        SubIssues.Entry issue = e(7, 7, "Seven");
        issue.owner = "nikakvo";
        issue.repo = "test";
        List<SubIssues.Entry> open = Arrays.asList(pull, issue);
        List<SubIssues.Entry> listed = Arrays.asList(issue);
        List<SubIssues.Entry> none = new ArrayList<>();

        assertTrue(SubIssues.isPlainNumber("#3"));
        assertTrue(SubIssues.isPlainNumber(" 3"));
        assertFalse(SubIssues.isPlainNumber("a/b#3"));

        assertEquals(SubIssues.RefState.PULL_REQUEST,
                SubIssues.refState(SubIssues.parse("3", "nikakvo", "test"), open, listed));
        assertEquals(SubIssues.RefState.LISTED,
                SubIssues.refState(SubIssues.parse("#7", "nikakvo", "test"), open, listed));
        // #7 is open but cannot be picked here (e.g. it is the issue itself)
        assertEquals(SubIssues.RefState.EXCLUDED,
                SubIssues.refState(SubIssues.parse("7", "nikakvo", "test"), open, none));
        // closed or unknown issue: GitHub decides
        assertEquals(SubIssues.RefState.OFFER,
                SubIssues.refState(SubIssues.parse("12", "nikakvo", "test"), open, listed));
        // the same number in another repository is a different issue
        assertEquals(SubIssues.RefState.OFFER,
                SubIssues.refState(SubIssues.parse("nikakvo/OpenHub#3", "nikakvo", "test"), open, listed));
    }
}
