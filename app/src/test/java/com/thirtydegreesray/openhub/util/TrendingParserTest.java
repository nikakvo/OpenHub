package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

public class TrendingParserTest {

    /** A row of github.com/trending as served in November 2026 (svg paths shortened). */
    private static final String ROW_2026 =
            "<article class=\"Box-row\">\n"
            + "  <div class=\"float-right d-flex\">\n"
            + "    <a href=\"/sponsors/thedotmack\" class=\"Button--secondary Button--small Button mr-2 tmp-mr-2\">"
            + "<span class=\"Button-content\"><span class=\"Button-label\"><svg class=\"octicon octicon-heart\"><path d=\"m8\"></path></svg>"
            + "<span class=\"d-none d-md-inline v-align-middle\">Sponsor</span></span></span></a>\n"
            + "    <div class=\"BtnGroup d-flex\"><a href=\"/login?return_to=%2Fthedotmack%2Fclaude-mem\" class=\"tooltipped tooltipped-sw btn-sm btn\">"
            + "<svg class=\"octicon octicon-star\"><path d=\"M8\"></path></svg><span class=\"d-none d-md-inline\">Star</span></a></div>\n"
            + "  </div>\n"
            + "  <h2 class=\"h3 lh-condensed\">\n"
            + "    <a href=\"/thedotmack/claude-mem\" class=\"Link\"><svg class=\"octicon octicon-repo mr-1 tmp-mr-1 color-fg-muted\"><path d=\"M2\"></path></svg>\n"
            + "      <span class=\"text-normal\">\n        thedotmack /\n</span>\n      claude-mem</a>  </h2>\n"
            + "    <p class=\"col-9 color-fg-muted my-1 tmp-pr-4\">\n"
            + "      Persistent Context Across Sessions for Every Agent\n    </p>\n"
            + "  <div class=\"f6 color-fg-muted mt-2\">\n"
            + "      <span class=\"tmp-mr-3 d-inline-block ml-0 tmp-ml-0\">\n"
            + "  <span class=\"repo-language-color\" style=\"background-color: #3178c6\"></span>\n"
            + "  <span itemprop=\"programmingLanguage\">TypeScript</span>\n</span>\n"
            + "      <a href=\"/thedotmack/claude-mem/stargazers\" class=\"tmp-mr-3 Link Link--muted d-inline-block\">"
            + "<svg aria-label=\"star\" role=\"img\" class=\"octicon octicon-star\"><path d=\"M8\"></path></svg>\n        111,617</a>\n"
            + "      <a href=\"/thedotmack/claude-mem/forks\" class=\"tmp-mr-3 Link Link--muted d-inline-block\">"
            + "<svg aria-label=\"fork\" role=\"img\" class=\"octicon octicon-repo-forked\"><path d=\"M5\"></path></svg>\n        9,725</a>\n"
            + "      <span class=\"tmp-mr-3 d-inline-block\">\n        Built by\n"
            + "          <a class=\"d-inline-block\" href=\"/thedotmack\"><img class=\"avatar mb-1 avatar-user\" src=\"https://avatars.githubusercontent.com/u/683968?s=40&amp;v=4\" width=\"20\" height=\"20\" alt=\"@thedotmack\" /></a>\n"
            + "</span>\n"
            + "      <span class=\"d-inline-block float-sm-right\">\n"
            + "        <svg aria-hidden=\"true\" class=\"octicon octicon-star\"><path d=\"M8\"></path></svg>\n"
            + "        1,138 stars today\n</span>  </div>\n"
            + "</article>";

    /** The older layout (2023-2025) the app was written for. */
    private static final String ROW_OLD =
            "<article class=\"Box-row\">"
            + "<h2 class=\"h3 lh-condensed\"><a href=\"/octo/tool\"><span class=\"text-normal\">octo /</span> tool</a></h2>"
            + "<p class=\"col-9 color-fg-muted my-1 pr-4\">A tool</p>"
            + "<div class=\"f6 color-fg-muted mt-2\">"
            + "<span class=\"d-inline-block ml-0 mr-3\"><span class=\"repo-language-color\"></span>"
            + "<span itemprop=\"programmingLanguage\">Go</span></span>"
            + "<a class=\"Link Link--muted d-inline-block mr-3\" href=\"/octo/tool/stargazers\"><svg></svg> 1,234</a>"
            + "<a class=\"Link Link--muted d-inline-block mr-3\" href=\"/octo/tool/forks\"><svg></svg> 56</a>"
            + "<span class=\"d-inline-block float-sm-right\"><svg></svg> 7 stars this week</span>"
            + "</div></article>";

    private static Element row(String html) {
        Document doc = Jsoup.parse(html, "https://github.com");
        return doc.getElementsByClass("Box-row").first();
    }

    @Test
    public void parsesTheCurrentLayout() {
        TrendingParser.Item item = TrendingParser.parse(row(ROW_2026));
        assertNotNull(item);
        assertEquals("thedotmack/claude-mem", item.fullName);
        assertEquals("thedotmack", item.owner);
        assertEquals("claude-mem", item.name);
        assertEquals("Persistent Context Across Sessions for Every Agent", item.description);
        assertEquals("TypeScript", item.language);
        assertEquals(111617, item.stars);
        assertEquals(9725, item.forks);
        assertEquals(1138, item.periodStars);
    }

    @Test
    public void parsesTheOldLayout() {
        TrendingParser.Item item = TrendingParser.parse(row(ROW_OLD));
        assertNotNull(item);
        assertEquals("octo/tool", item.fullName);
        assertEquals("A tool", item.description);
        assertEquals("Go", item.language);
        assertEquals(1234, item.stars);
        assertEquals(56, item.forks);
        assertEquals(7, item.periodStars);
    }

    @Test
    public void rowWithoutDescriptionOrLanguage() {
        String html = "<article class=\"Box-row\"><h2><a href=\"/a/b/\">a / b</a></h2>"
                + "<div class=\"f6\"><a href=\"/a/b/stargazers\">3</a></div></article>";
        TrendingParser.Item item = TrendingParser.parse(row(html));
        assertNotNull(item);
        assertEquals("a/b", item.fullName);
        assertEquals("", item.description);
        assertEquals("unknown", item.language);
        assertEquals(3, item.stars);
        assertEquals(0, item.forks);
        assertEquals(0, item.periodStars);
    }

    @Test
    public void rowWithoutRepositoryLink() {
        assertNull(TrendingParser.parse(row("<article class=\"Box-row\"><h2>x</h2></article>")));
        assertNull(TrendingParser.parse(row("<article class=\"Box-row\"><h2><a href=\"/sponsors\">x</a></h2></article>")));
    }

    @Test
    public void numbers() {
        assertEquals(1138, TrendingParser.number("1,138 stars today"));
        assertEquals(111617, TrendingParser.number("\n   111,617 "));
        assertEquals(0, TrendingParser.number("no number"));
        assertEquals(0, TrendingParser.number(null));
        assertEquals(Integer.MAX_VALUE, TrendingParser.number("99999999999"));
    }
}
