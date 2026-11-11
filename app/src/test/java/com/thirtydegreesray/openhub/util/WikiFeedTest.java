package com.thirtydegreesray.openhub.util;

import com.thirtydegreesray.openhub.mvp.model.WikiFeedModel;
import com.thirtydegreesray.openhub.mvp.model.WikiModel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WikiFeedTest {

    private static final String FEED = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<feed xml:lang=\"en-US\" xmlns=\"http://www.w3.org/2005/Atom\">\n"
            + "  <id>tag:github.com,2008:/nikakvo/test/wiki</id>\n"
            + "  <link type=\"text/html\" rel=\"alternate\" href=\"https://github.com/nikakvo/test/wiki\"/>\n"
            + "  <title>Recent updates to the test wiki</title>\n"
            + "  <updated>2026-11-11T18:00:00Z</updated>\n"
            + "  <entry>\n"
            + "    <id>tag:github.com,2008:https://github.com/nikakvo/test/wiki/Getting-Started/abc123</id>\n"
            + "    <published>2026-11-11T18:00:00Z</published>\n"
            + "    <updated>2026-11-11T18:00:00Z</updated>\n"
            + "    <link type=\"text/html\" rel=\"alternate\" href=\"https://github.com/nikakvo/test/wiki/Getting-Started\"/>\n"
            + "    <title>Getting Started</title>\n"
            + "    <content type=\"html\">&lt;h2&gt;Install&lt;/h2&gt;\n&lt;p&gt;Run &lt;code&gt;a &amp;amp;&amp;amp; b&lt;/code&gt;&lt;/p&gt;</content>\n"
            + "  </entry>\n"
            + "  <entry>\n"
            + "    <id>tag:github.com,2008:https://github.com/nikakvo/test/wiki/Home/def456</id>\n"
            + "    <published>2026-11-10T10:00:00Z</published>\n"
            + "    <updated>2026-11-10T10:00:00Z</updated>\n"
            + "    <title>Home</title>\n"
            + "    <content type=\"html\">&lt;p&gt;Welcome&lt;/p&gt;</content>\n"
            + "  </entry>\n"
            + "</feed>\n";

    @Test
    public void readsTheEntries() {
        WikiFeedModel feed = WikiFeed.parse(FEED);
        assertEquals("Recent updates to the test wiki", feed.getTitle());
        assertEquals("2026-11-11T18:00:00Z", feed.getUpdated());
        assertEquals(2, feed.getWikiList().size());
        WikiModel first = feed.getWikiList().get(0);
        assertEquals("2026-11-11T18:00:00Z", first.getPublished());
        assertEquals("Getting Started", first.getName());
        assertEquals("<h2>Install</h2>\n<p>Run <code>a &amp;&amp; b</code></p>", first.getContent());
        assertEquals("Home", feed.getWikiList().get(1).getName());
    }

    @Test
    public void anHtmlPageIsAnEmptyList() {
        WikiFeedModel feed = WikiFeed.parse("<!DOCTYPE html><html><body><p>Not found</p></body></html>");
        assertNotNull(feed.getWikiList());
        assertTrue(feed.getWikiList().isEmpty());
    }

    @Test
    public void aFeedWithoutEntriesIsAnEmptyList() {
        WikiFeedModel feed = WikiFeed.parse("<feed xmlns=\"http://www.w3.org/2005/Atom\"><id>x</id></feed>");
        assertTrue(feed.getWikiList().isEmpty());
    }
}
