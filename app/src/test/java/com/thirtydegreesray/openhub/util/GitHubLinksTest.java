package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class GitHubLinksTest {

    @Test
    public void repositoryLinksAsPeopleShareThem() {
        String repo = "https://github.com/hxreborn/punch-hole-download-progress";
        assertEquals(repo, GitHubLinks.normalize(repo + ".git"));
        assertEquals(repo, GitHubLinks.normalize(repo + ".git/"));
        assertEquals(repo, GitHubLinks.normalize(repo + ".GIT"));
        assertEquals(repo, GitHubLinks.normalize(repo + "/"));
        assertEquals(repo, GitHubLinks.normalize(repo + "#readme"));
        assertEquals(repo, GitHubLinks.normalize(repo + "?tab=readme-ov-file"));
        assertEquals(repo, GitHubLinks.normalize("  http://www.github.com/hxreborn/punch-hole-download-progress.git "));
    }

    @Test
    public void otherLinksStayAsTheyAre() {
        assertEquals("https://github.com/o/r/issues/3", GitHubLinks.normalize("https://github.com/o/r/issues/3"));
        assertEquals("https://github.com/search?q=x", GitHubLinks.normalize("https://github.com/search?q=x"));
        assertEquals("https://github.com/topics/android?l=java",
                GitHubLinks.normalize("https://github.com/topics/android?l=java"));
        assertEquals("https://gitlab.com/o/r.git", GitHubLinks.normalize("https://gitlab.com/o/r.git"));
        assertEquals("https://github.com/o", GitHubLinks.normalize("https://github.com/o"));
        assertEquals("https://github.com/o/.git", GitHubLinks.normalize("https://github.com/o/.git"));
    }

    @Test
    public void wikiPages() {
        GitHubLinks.Wiki wiki = GitHubLinks.wiki(
                "https://github.com/v1s1t0r1sh3r3/airgeddon/wiki/Installation%20&%20Usage");
        assertEquals("v1s1t0r1sh3r3", wiki.owner);
        assertEquals("airgeddon", wiki.repo);
        assertEquals("Installation-&-Usage", wiki.page);
        assertEquals("Installation & Usage", wiki.title());
        assertEquals(Arrays.asList(
                "https://raw.githubusercontent.com/wiki/v1s1t0r1sh3r3/airgeddon/Installation-%26-Usage.md",
                "https://raw.githubusercontent.com/wiki/v1s1t0r1sh3r3/airgeddon/Installation-%26-Usage.markdown"),
                wiki.rawUrls());
        assertEquals("https://github.com/v1s1t0r1sh3r3/airgeddon/wiki/Installation-%26-Usage", wiki.htmlUrl());

        assertEquals("Home", GitHubLinks.wiki("https://github.com/o/r/wiki").page);
        assertEquals("Home", GitHubLinks.wiki("https://github.com/o/r/wiki/").page);
        assertEquals("https://github.com/o/r/wiki", GitHubLinks.wiki("https://github.com/o/r/wiki").htmlUrl());
        assertEquals("Bettercap-Tips", GitHubLinks.wiki("https://github.com/o/r/wiki/Bettercap-Tips#x").page);
        assertNull(GitHubLinks.wiki("https://github.com/o/r/wiki/_pages"));
        assertNull(GitHubLinks.wiki("https://github.com/o/r/wiki/Home/_edit"));
        assertNull(GitHubLinks.wiki("https://github.com/o/r/issues"));
        assertNull(GitHubLinks.wiki("https://example.com/o/r/wiki/Home"));
        assertEquals(Collections.emptyList(), GitHubLinks.wikiFiles("Page.mediawiki"));
        assertEquals(Collections.singletonList("CHANGELOG.md"), GitHubLinks.wikiFiles("CHANGELOG.md"));
    }

    @Test
    public void wikiLinksBecomeMarkdown() {
        assertEquals("See [Other Sources](Other-Sources) and [here](Bettercap-Tips#install).",
                GitHubLinks.wikiMarkdown("See [[Other Sources]] and [[here|Bettercap Tips#install]]."));
        assertEquals("![](images/a.png)", GitHubLinks.wikiMarkdown("[[images/a.png]]"));
        assertEquals("`[[keep]]` [keep](keep)", GitHubLinks.wikiMarkdown("`[[keep]]` [[keep]]"));
        String fenced = "```\n[[code]]\n```\n[[Page]]";
        assertEquals("```\n[[code]]\n```\n[Page](Page)", GitHubLinks.wikiMarkdown(fenced));
        assertEquals("[A & B](A-%26-B)", GitHubLinks.wikiMarkdown("[[A & B]]"));
    }

    @Test
    public void wikiHtmlLinks() {
        String html = "<a href=\"Other-Sources\">x</a><a href=\"#top\">t</a>"
                + "<a href=\"https://a.b/c\">c</a><img src=\"img/s.png\"><a href=\"/o/r/issues\">i</a>";
        assertEquals("<a href=\"https://github.com/o/r/wiki/Other-Sources\">x</a><a href=\"#top\">t</a>"
                + "<a href=\"https://a.b/c\">c</a><img src=\"https://raw.githubusercontent.com/wiki/o/r/img/s.png\">"
                + "<a href=\"https://github.com/o/r/issues\">i</a>", GitHubLinks.wikiHtml(html, "o", "r"));
    }

    @Test
    public void filesOfARepository() {
        GitHubLinks.Blob blob = GitHubLinks.blob("https://github.com/o/r/blob/main/docs/Install Guide.md#L3");
        assertEquals("o", blob.owner);
        assertEquals("r", blob.repo);
        assertEquals("main", blob.ref);
        assertEquals("docs/Install Guide.md", blob.path);
        assertEquals("Install Guide.md", blob.name());
        assertEquals("https://api.github.com/repos/o/r/contents/docs/Install%20Guide.md?ref=main", blob.apiUrl());
        assertEquals("https://raw.githubusercontent.com/o/r/main/docs/Install%20Guide.md", blob.rawUrl());
        assertEquals("https://github.com/o/r/blob/main/docs/Install%20Guide.md", blob.htmlUrl());
        assertNull(GitHubLinks.blob("https://github.com/o/r/blob/main"));
        assertNull(GitHubLinks.blob("https://github.com/o/r/tree/main/docs"));

        assertEquals("o/r", GitHubLinks.treeRoot("https://github.com/o/r/tree/dev"));
        assertNull(GitHubLinks.treeRoot("https://github.com/o/r/tree/dev/docs"));
        assertTrue(GitHubLinks.blob("https://github.com/o/r.git/blob/main/a.txt").repo.equals("r"));
    }
}
