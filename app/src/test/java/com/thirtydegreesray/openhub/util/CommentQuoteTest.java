package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommentQuoteTest {

    @Test
    public void emptyBodyQuotesNothing() {
        assertEquals("", CommentQuote.quote(null));
        assertEquals("", CommentQuote.quote(""));
        assertEquals("", CommentQuote.quote(" \n\r\n  \n"));
    }

    @Test
    public void everyLineIsQuotedAndAnEmptyLineFollows() {
        assertEquals("> one\n> two\n\n", CommentQuote.quote("one\ntwo"));
    }

    @Test
    public void emptyLinesBecomeABareMark() {
        assertEquals("> one\n>\n> two\n\n", CommentQuote.quote("one\n\ntwo"));
        assertEquals("> one\n>\n> two\n\n", CommentQuote.quote("one\n   \ntwo"));
    }

    @Test
    public void windowsAndOldMacLineEnds() {
        assertEquals("> a\n>\n> b\n\n", CommentQuote.quote("a\r\n\r\nb"));
        assertEquals("> a\n> b\n\n", CommentQuote.quote("a\rb"));
    }

    @Test
    public void trailingSpacesOfALineStay() {
        // two spaces at the end are a line break in Markdown
        assertEquals("> first  \n> second\n\n", CommentQuote.quote("first  \nsecond"));
    }

    @Test
    public void bodyEndingInNewlinesHasNoEmptyQuoteLinesAtTheEnd() {
        assertEquals("> done\n\n", CommentQuote.quote("done\n"));
        assertEquals("> done\n\n", CommentQuote.quote("done\r\n\r\n\r\n"));
        assertEquals("> done\n\n", CommentQuote.quote("\n\ndone"));
    }

    @Test
    public void codeFencesAndIndentStay() {
        String body = "Look:\n\n```java\nint a = 1;\n    b();\n```";
        assertEquals("> Look:\n>\n> ```java\n> int a = 1;\n>     b();\n> ```\n\n", CommentQuote.quote(body));
    }

    @Test
    public void existingQuotesNest() {
        assertEquals("> > old\n>\n> new\n\n", CommentQuote.quote("> old\n\nnew"));
    }

    @Test
    public void listsStay() {
        assertEquals("> - one\n>   - two\n\n", CommentQuote.quote("- one\n  - two"));
    }

    @Test
    public void longBodyIsQuotedWhole() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 800; i++) body.append("line ").append(i).append('\n');
        String quote = CommentQuote.quote(body.toString());
        assertTrue(quote.startsWith("> line 0\n"));
        assertTrue(quote.endsWith("> line 799\n\n"));
        assertEquals(801, quote.split("\n", -1).length - 1);
    }

    @Test
    public void quoteIsAddedAfterADraft() {
        assertEquals("> a\n\n", CommentQuote.addQuote(null, "> a\n\n"));
        assertEquals("> a\n\n", CommentQuote.addQuote("  \n", "> a\n\n"));
        assertEquals("My text\n\n> a\n\n", CommentQuote.addQuote("My text", "> a\n\n"));
        assertEquals("My text\n\n> a\n\n", CommentQuote.addQuote("My text\n\n\n", "> a\n\n"));
    }

    @Test
    public void mention() {
        assertEquals("@octo ", CommentQuote.mention("octo"));
        assertEquals("@octo ", CommentQuote.addMention(null, "octo"));
        assertEquals("@octo ", CommentQuote.addMention(" ", "octo"));
        assertEquals("Thanks @octo ", CommentQuote.addMention("Thanks", "octo"));
        assertEquals("Thanks @octo ", CommentQuote.addMention("Thanks ", "octo"));
        assertEquals("Line\n@octo ", CommentQuote.addMention("Line\n", "octo"));
    }

    @Test
    public void mentionIsNotAddedTwice() {
        assertEquals("Hi @octo ", CommentQuote.addMention("Hi @octo", "octo"));
        assertEquals("Hi @octo ", CommentQuote.addMention("Hi @octo  ", "octo"));
        assertEquals("@Octo ", CommentQuote.addMention("@Octo", "octo"));
        // another name that only ends the same is a different person
        assertEquals("Hi @superocto @octo ", CommentQuote.addMention("Hi @superocto", "octo"));
    }

    @Test
    public void whoCanBeMentioned() {
        assertTrue(CommentQuote.canMention("octo", "me", false));
        assertFalse(CommentQuote.canMention("Me", "me", false));
        assertFalse(CommentQuote.canMention("ghost", "me", false));
        assertFalse(CommentQuote.canMention("", "me", false));
        assertFalse(CommentQuote.canMention(null, "me", false));
        assertFalse(CommentQuote.canMention("dependabot[bot]", "me", false));
        assertFalse(CommentQuote.canMention("github-actions", "me", true));
        assertTrue(CommentQuote.canMention("octo", null, false));
    }
}
