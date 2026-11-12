package com.thirtydegreesray.openhub.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MarkdownRefsTest {

    private static final String REPO = "nikakvo/OpenHub";

    private static String scan(String text, String repo) {
        StringBuilder out = new StringBuilder();
        for (MarkdownRefs.Piece piece : MarkdownRefs.scan(text, repo)) out.append(piece);
        return out.toString();
    }

    private static String html(String markdown) {
        return MarkdownHtml.renderComment(markdown, REPO);
    }

    @Test
    public void mention() {
        assertEquals("hi [@nikakvo](https://github.com/nikakvo), look",
                scan("hi @nikakvo, look", REPO));
        assertEquals("[@a-b-c](https://github.com/a-b-c).", scan("@a-b-c.", null));
        assertEquals("([@user](https://github.com/user))", scan("(@user)", null));
    }

    @Test
    public void emailIsNotMention() {
        assertEquals("mail me at me@example.com", scan("mail me at me@example.com", REPO));
        assertEquals("a+b@host.org", scan("a+b@host.org", REPO));
    }

    @Test
    public void invalidLogins() {
        assertEquals("@-bad", scan("@-bad", null));
        assertEquals("@bad-", scan("@bad-", null));
        assertEquals("@a--b", scan("@a--b", null));
        // 40 characters is one too many
        String forty = "abcdefghijabcdefghijabcdefghijabcdefghij";
        assertEquals("@" + forty, scan("@" + forty, null));
        assertEquals("[@" + forty.substring(1) + "](https://github.com/" + forty.substring(1) + ")",
                scan("@" + forty.substring(1), null));
        // a team (@org/team) is left alone instead of linking only half of it
        assertEquals("@org/team", scan("@org/team", null));
    }

    @Test
    public void issueOfCurrentRepo() {
        assertEquals("fixes [#8](https://github.com/nikakvo/OpenHub/issues/8)!", scan("fixes #8!", REPO));
        assertEquals("fixes #8!", scan("fixes #8!", null));
        assertEquals("fixes #8!", scan("fixes #8!", "not a repo"));
    }

    @Test
    public void issueOfOtherRepo() {
        assertEquals("see [tiann/KernelSU#954](https://github.com/tiann/KernelSU/issues/954)",
                scan("see tiann/KernelSU#954", null));
        assertEquals("[my-org/repo.name_x#12](https://github.com/my-org/repo.name_x/issues/12)",
                scan("my-org/repo.name_x#12", null));
        // an owner cannot have a dot: no half link
        assertEquals("a.b/c#1", scan("a.b/c#1", null));
    }

    @Test
    public void notIssues() {
        assertEquals("page#12 x#1 #0 #12abc &#38; ##5", scan("page#12 x#1 #0 #12abc &#38; ##5", REPO));
    }

    @Test
    public void mixedOrder() {
        List<MarkdownRefs.Piece> pieces = MarkdownRefs.scan("#2 by @me and owner/r#3", REPO);
        assertEquals(5, pieces.size());
        assertEquals(MarkdownRefs.Piece.ISSUE, pieces.get(0).kind);
        assertEquals(MarkdownRefs.Piece.MENTION, pieces.get(2).kind);
        assertEquals("https://github.com/owner/r/issues/3", pieces.get(4).url);
    }

    @Test
    public void emojiTable() {
        assertTrue(EmojiCodes.size() >= 100);
        assertEquals("ok \uD83D\uDC4D \uD83C\uDF89", MarkdownRefs.emoji("ok :+1: :tada:"));
        assertEquals("time 10:30:00 :not_an_emoji:", MarkdownRefs.emoji("time 10:30:00 :not_an_emoji:"));
        assertNull(EmojiCodes.get("shipit"));
    }

    @Test
    public void renderedLinksHaveGitHubClasses() {
        String out = html("thanks @nikakvo for #8");
        assertTrue(out, out.contains("<a href=\"https://github.com/nikakvo\" class=\"user-mention\">@nikakvo</a>"));
        assertTrue(out, out.contains("<a href=\"https://github.com/nikakvo/OpenHub/issues/8\" class=\"issue-link\">#8</a>"));
    }

    @Test
    public void notInsideCodeOrLinks() {
        String out = html("`@nikakvo #8 :tada:`\n\n```\n@nikakvo #8 :tada:\n```\n\n    @x #9\n\n[@y #7](https://example.com)");
        assertFalse(out, out.contains("user-mention"));
        assertFalse(out, out.contains("issue-link"));
        assertFalse(out, out.contains("\uD83C\uDF89"));
        assertTrue(out, out.contains("<code>@nikakvo #8 :tada:</code>"));
    }

    @Test
    public void emojiInText() {
        String out = html("great :rocket: work");
        assertTrue(out, out.contains("great \uD83D\uDE80 work"));
    }

    @Test
    public void autolinks() {
        String out = html("see https://github.com/nikakvo/OpenHub and www.example.com or me@example.com");
        assertTrue(out, out.contains("<a href=\"https://github.com/nikakvo/OpenHub\">https://github.com/nikakvo/OpenHub</a>"));
        assertTrue(out, out.contains("<a href=\"http://www.example.com\">www.example.com</a>"));
        assertTrue(out, out.contains("<a href=\"mailto:me@example.com\">me@example.com</a>"));
        assertFalse(out, out.contains("user-mention"));
    }

    @Test
    public void footnotes() {
        String out = html("Text[^1].\n\n[^1]: The note.");
        assertTrue(out, out.contains("class=\"footnotes\""));
        assertTrue(out, out.contains("data-footnote-ref"));
        assertTrue(out, out.contains("The note."));
    }

    @Test
    public void documentsKeepMentionsAsText() {
        String out = MarkdownHtml.render("by @nikakvo #8 https://example.com");
        assertFalse(out, out.contains("user-mention"));
        assertTrue(out, out.contains("<a href=\"https://example.com\">"));
    }
}
