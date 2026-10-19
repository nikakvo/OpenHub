package com.thirtydegreesray.openhub.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * github.com links that the app opens itself: repository links as people share them
 * ("https://github.com/owner/repo.git", "...repo#readme", "...repo?tab=readme-ov-file"),
 * wiki pages and files of a repository (blob links). Pure Java, so it is unit tested.
 */
public final class GitHubLinks {

    private GitHubLinks() {
    }

    /** First path parts of github.com that are pages of GitHub itself, not users. */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "orgs", "topics", "settings", "marketplace", "sponsors", "apps", "collections",
            "features", "enterprise", "search", "notifications", "login", "logout", "about",
            "explore", "trending", "pulls", "issues", "codespaces", "new", "organizations",
            "users", "site", "security", "customer-stories", "readme", "events", "pricing"));

    private static final List<String> MARKDOWN = Arrays.asList(".md", ".markdown");
    private static final List<String> OTHER_WIKI_FORMATS = Arrays.asList(".mediawiki", ".wiki",
            ".asciidoc", ".adoc", ".textile", ".rdoc", ".org", ".creole", ".rst", ".pod");
    private static final List<String> IMAGES = Arrays.asList(".png", ".jpg", ".jpeg", ".gif",
            ".svg", ".webp");

    // ---------------------------------------------------------------------------------
    // Repository links
    // ---------------------------------------------------------------------------------

    /**
     * The link as the app's routing expects it. "github.com/owner/repo.git", a trailing
     * slash, "#readme" or "?tab=..." on a repository link are dropped (GitHub's API has no
     * repository "repo.git": the screen showed "Page not found"). Anything else is returned
     * unchanged.
     */
    public static String normalize(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        URI uri = parse(trimmed);
        if (uri == null || !isGitHub(uri)) return trimmed;
        List<String> parts = segments(uri.getRawPath());
        if (parts.size() != 2) return trimmed;
        String owner = parts.get(0);
        String repo = parts.get(1);
        if (RESERVED.contains(owner.toLowerCase(Locale.ROOT))) return trimmed;
        if (repo.length() > 4 && repo.toLowerCase(Locale.ROOT).endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        return "https://github.com/" + owner + "/" + repo;
    }

    /** "repo.git" -> "repo"; GitHub never gives a repository a name ending in ".git". */
    public static String stripGit(String repo) {
        if (repo == null) return null;
        return repo.length() > 4 && repo.toLowerCase(Locale.ROOT).endsWith(".git")
                ? repo.substring(0, repo.length() - 4) : repo;
    }

    // ---------------------------------------------------------------------------------
    // Files (blob links)
    // ---------------------------------------------------------------------------------

    /** github.com/owner/repo/blob/ref/path/to/file */
    public static final class Blob {
        public final String owner;
        public final String repo;
        public final String ref;
        public final String path;

        Blob(String owner, String repo, String ref, String path) {
            this.owner = owner;
            this.repo = repo;
            this.ref = ref;
            this.path = path;
        }

        /** The file through the contents API (what the file viewer loads). */
        public String apiUrl() {
            return "https://api.github.com/repos/" + owner + "/" + repo + "/contents/"
                    + encodePath(path) + "?ref=" + encodeSegment(ref);
        }

        public String rawUrl() {
            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/"
                    + encodePath(ref) + "/" + encodePath(path);
        }

        public String htmlUrl() {
            return "https://github.com/" + owner + "/" + repo + "/blob/" + encodePath(ref)
                    + "/" + encodePath(path);
        }

        public String name() {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }
    }

    /**
     * A file of a repository, or null. A branch name with "/" cannot be told apart from
     * the path (GitHub has the same problem); the first part is taken as the branch.
     */
    public static Blob blob(String url) {
        URI uri = parse(url == null ? null : url.trim());
        if (uri == null || !isGitHub(uri)) return null;
        List<String> parts = segments(uri.getPath());
        if (parts.size() < 5 || !"blob".equals(parts.get(2))) return null;
        String path = String.join("/", parts.subList(4, parts.size()));
        return new Blob(parts.get(0), stripGit(parts.get(1)), parts.get(3), path);
    }

    /**
     * github.com/owner/repo/tree/branch (the whole repository at a branch): the
     * repository's "owner/repo", or null. Folders inside it return null (the browser).
     */
    public static String treeRoot(String url) {
        URI uri = parse(url == null ? null : url.trim());
        if (uri == null || !isGitHub(uri)) return null;
        List<String> parts = segments(uri.getPath());
        if (parts.size() != 4 || !"tree".equals(parts.get(2))) return null;
        return parts.get(0) + "/" + stripGit(parts.get(1));
    }

    // ---------------------------------------------------------------------------------
    // Wiki
    // ---------------------------------------------------------------------------------

    /** A wiki page: github.com/owner/repo/wiki or .../wiki/Page-Name */
    public static final class Wiki {
        public final String owner;
        public final String repo;
        /** As GitHub names it in the address: "Installation-&-Usage", "Home" */
        public final String page;

        Wiki(String owner, String repo, String page) {
            this.owner = owner;
            this.repo = repo;
            this.page = page;
        }

        /** "Installation & Usage" for the title */
        public String title() {
            return GitHubLinks.title(page);
        }

        public String htmlUrl() {
            return wikiPageUrl(owner, repo, page);
        }

        /** The page file(s) to try, in order, from raw.githubusercontent.com. */
        public List<String> rawUrls() {
            List<String> urls = new ArrayList<>();
            for (String file : wikiFiles(page)) {
                urls.add("https://raw.githubusercontent.com/wiki/" + owner + "/" + repo + "/"
                        + encodeSegment(file));
            }
            return urls;
        }
    }

    public static Wiki wiki(String url) {
        URI uri = parse(url == null ? null : url.trim());
        if (uri == null || !isGitHub(uri)) return null;
        List<String> parts = segments(uri.getPath());
        if (parts.size() < 3 || parts.size() > 4 || !"wiki".equals(parts.get(2))) return null;
        if (RESERVED.contains(parts.get(0).toLowerCase(Locale.ROOT))) return null;
        String page = parts.size() == 3 ? "Home" : parts.get(3);
        // _pages, _new, _history, _compare, ... are GitHub's own wiki tools
        if (page.isEmpty() || page.startsWith("_")) return null;
        return new Wiki(parts.get(0), stripGit(parts.get(1)), page.replace(' ', '-'));
    }

    public static Wiki wiki(String owner, String repo, String page) {
        return new Wiki(owner, repo, (page == null || page.trim().isEmpty() ? "Home" : page.trim())
                .replace(' ', '-'));
    }

    public static String title(String page) {
        String name = page;
        for (String ext : MARKDOWN) {
            if (name.toLowerCase(Locale.ROOT).endsWith(ext)) {
                name = name.substring(0, name.length() - ext.length());
                break;
            }
        }
        return name.replace('-', ' ');
    }

    public static String wikiPageUrl(String owner, String repo, String page) {
        return "https://github.com/" + owner + "/" + repo + "/wiki"
                + ("Home".equals(page) ? "" : "/" + encodeSegment(page));
    }

    /**
     * Page file names: GitHub stores the page "Installation & Usage" as
     * "Installation-&-Usage.md". Pages in other formats (.mediawiki, .rst, ...) are not
     * rendered here: an empty list sends them to the browser.
     */
    public static List<String> wikiFiles(String page) {
        String name = page.trim().replace(' ', '-');
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : MARKDOWN) {
            if (lower.endsWith(ext)) return Collections.singletonList(name);
        }
        for (String ext : OTHER_WIKI_FORMATS) {
            if (lower.endsWith(ext)) return Collections.emptyList();
        }
        return Arrays.asList(name + ".md", name + ".markdown");
    }

    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\[\\]\\n]+?)]]");

    /**
     * GitHub wiki links ("[[Page Name]]", "[[Link text|Page Name]]", "[[image.png]]") as
     * plain Markdown, because the Markdown API does not know them. Code is left alone.
     */
    public static String wikiMarkdown(String markdown) {
        if (markdown == null || !markdown.contains("[[")) return markdown;
        StringBuilder out = new StringBuilder(markdown.length());
        boolean inFence = false;
        String[] lines = markdown.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.trim();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                inFence = !inFence;
            } else if (!inFence && line.contains("[[")) {
                line = convertLine(line);
            }
            out.append(line);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String convertLine(String line) {
        // inline code spans stay as they are
        StringBuilder out = new StringBuilder();
        String[] pieces = line.split("`", -1);
        for (int i = 0; i < pieces.length; i++) {
            if (i > 0) out.append('`');
            out.append(i % 2 == 0 ? convertPiece(pieces[i]) : pieces[i]);
        }
        return out.toString();
    }

    private static String convertPiece(String text) {
        Matcher m = WIKI_LINK.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String inner = m.group(1).trim();
            String label = inner;
            String target = inner;
            int bar = inner.indexOf('|');
            if (bar >= 0) {
                label = inner.substring(0, bar).trim();
                target = inner.substring(bar + 1).trim();
            }
            String replacement;
            if (isImage(target)) {
                replacement = "![" + (bar >= 0 ? label : "") + "](" + linkTarget(target) + ")";
            } else {
                replacement = "[" + label + "](" + linkTarget(target) + ")";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String linkTarget(String target) {
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target.replace(" ", "%20");
        }
        String anchor = "";
        int hash = target.indexOf('#');
        if (hash >= 0) {
            anchor = target.substring(hash);
            target = target.substring(0, hash);
        }
        return encodePath(target.trim().replace(' ', '-')) + anchor.replace(" ", "-");
    }

    private static boolean isImage(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        for (String ext : IMAGES) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static final Pattern HREF = Pattern.compile("(href|src)=\"([^\"]*)\"");

    /**
     * Relative links in a rendered wiki page point to other pages of the same wiki,
     * relative images to the wiki's files. "#anchors" stay inside the page.
     */
    public static String wikiHtml(String html, String owner, String repo) {
        if (html == null) return null;
        Matcher m = HREF.matcher(html);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String attr = m.group(1);
            String value = m.group(2);
            String fixed = value;
            String lower = value.toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.startsWith("#") || lower.startsWith("http:")
                    || lower.startsWith("https:") || lower.startsWith("mailto:")
                    || lower.startsWith("data:")) {
                fixed = value;
            } else if (value.startsWith("//")) {
                fixed = "https:" + value;
            } else if (value.startsWith("/")) {
                fixed = "https://github.com" + value;
            } else if ("src".equals(attr) || isImage(value)) {
                fixed = "https://raw.githubusercontent.com/wiki/" + owner + "/" + repo + "/" + value;
            } else {
                fixed = "https://github.com/" + owner + "/" + repo + "/wiki/" + value;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(attr + "=\"" + fixed + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static URI parse(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            // spaces and similar from hand-written links
            try {
                return new URI(url.replace(" ", "%20"));
            } catch (URISyntaxException e2) {
                return null;
            }
        }
    }

    private static boolean isGitHub(URI uri) {
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || scheme == null) return false;
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return false;
        return "github.com".equalsIgnoreCase(host) || "www.github.com".equalsIgnoreCase(host);
    }

    private static List<String> segments(String path) {
        List<String> parts = new ArrayList<>();
        if (path == null) return parts;
        for (String part : path.split("/")) {
            if (!part.isEmpty()) parts.add(part);
        }
        return parts;
    }

    /** Percent-encodes everything except unreserved characters (RFC 3986). */
    public static String encodeSegment(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return out.toString();
    }

    /** Like encodeSegment, but keeps the "/" between folders. */
    public static String encodePath(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append('/');
            out.append(encodeSegment(parts[i]));
        }
        return out.toString();
    }
}
