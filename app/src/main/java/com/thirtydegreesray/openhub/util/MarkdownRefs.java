package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commonmark.node.Code;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.PostProcessor;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text written on the phone (drafts, the offline preview) gets what github.com adds when it
 * renders a comment: {@code @user} links to the profile, {@code #123} to an issue of the
 * current repository, {@code owner/repo#123} to an issue of that repository, and common
 * {@code :emoji:} shortcodes become the emoji. Never inside code spans, code blocks or links;
 * e-mail addresses are not mentions.
 */
public final class MarkdownRefs implements PostProcessor {

    /** a GitHub login: letters, digits and single hyphens inside, at most 39 characters */
    private static final String LOGIN = "[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}";

    private static final Pattern MENTION = Pattern.compile(
            "(?<![A-Za-z0-9_`./@+-])@(" + LOGIN + ")(?![A-Za-z0-9_/-])");

    private static final Pattern ISSUE = Pattern.compile(
            "(?<![A-Za-z0-9_&/#.@-])(?:(" + LOGIN + ")/([A-Za-z0-9._-]{1,100}))?#([1-9][0-9]{0,9})(?![A-Za-z0-9_])");

    private static final Pattern EMOJI = Pattern.compile(":([a-z0-9_+-]{1,40}):");

    /** One piece of a text: plain text, or a link with its address and kind. */
    public static final class Piece {
        public static final int TEXT = 0;
        public static final int MENTION = 1;
        public static final int ISSUE = 2;

        public final int kind;
        public final String text;
        @Nullable public final String url;

        Piece(int kind, @NonNull String text, @Nullable String url) {
            this.kind = kind;
            this.text = text;
            this.url = url;
        }

        @Override
        public String toString() {
            return kind == TEXT ? text : "[" + text + "](" + url + ")";
        }
    }

    /**
     * A link made by this class. The HTML renderer only knows the exact node classes, so
     * {@link #renderer} draws it: {@code <a href class="user-mention|issue-link">}.
     */
    public static final class RefLink extends Link {
        public final int kind;

        RefLink(int kind, @NonNull String url) {
            super(url, null);
            this.kind = kind;
        }

        @NonNull
        public String cssClass() {
            return kind == Piece.MENTION ? "user-mention" : "issue-link";
        }
    }

    /** HTML renderer for {@link RefLink}, for {@code HtmlRenderer.Builder.nodeRendererFactory}. */
    @NonNull
    public static NodeRenderer renderer(@NonNull HtmlNodeRendererContext context) {
        return new NodeRenderer() {
            @Override
            public Set<Class<? extends Node>> getNodeTypes() {
                return Collections.singleton(RefLink.class);
            }

            @Override
            public void render(Node node) {
                RefLink link = (RefLink) node;
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("href", context.encodeUrl(link.getDestination()));
                attrs.put("class", link.cssClass());
                HtmlWriter html = context.getWriter();
                html.tag("a", context.extendAttributes(node, "a", attrs));
                for (Node child = node.getFirstChild(); child != null; ) {
                    Node next = child.getNext();
                    context.render(child);
                    child = next;
                }
                html.tag("/a");
            }
        };
    }

    @Nullable private final String repoFullName;

    /** {@code repoFullName} ("owner/repo") for bare {@code #123}; null: they stay text. */
    public MarkdownRefs(@Nullable String repoFullName) {
        this.repoFullName = isRepo(repoFullName) ? repoFullName : null;
    }

    @Override
    public Node process(Node document) {
        walk(document);
        return document;
    }

    private void walk(@NonNull Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            if (child instanceof Text) {
                replace((Text) child);
            } else if (!(child instanceof Link || child instanceof Code
                    || child instanceof HtmlInline || child instanceof HtmlBlock)) {
                walk(child);
            }
            child = next;
        }
    }

    private void replace(@NonNull Text node) {
        String literal = emoji(node.getLiteral());
        List<Piece> pieces = scan(literal, repoFullName);
        if (pieces.size() == 1 && pieces.get(0).kind == Piece.TEXT) {
            node.setLiteral(literal);
            return;
        }
        for (Piece piece : pieces) {
            if (piece.kind == Piece.TEXT) {
                node.insertBefore(new Text(piece.text));
            } else {
                RefLink link = new RefLink(piece.kind, piece.url);
                link.appendChild(new Text(piece.text));
                node.insertBefore(link);
            }
        }
        node.unlink();
    }

    /** Splits a plain text into text, mentions and issue references. */
    @NonNull
    public static List<Piece> scan(@NonNull String text, @Nullable String repoFullName) {
        String repo = isRepo(repoFullName) ? repoFullName : null;
        List<int[]> found = new ArrayList<>();
        List<Piece> links = new ArrayList<>();
        Matcher m = MENTION.matcher(text);
        while (m.find()) {
            found.add(new int[]{m.start(), m.end()});
            links.add(new Piece(Piece.MENTION, m.group(), "https://github.com/" + m.group(1)));
        }
        m = ISSUE.matcher(text);
        while (m.find()) {
            String target = m.group(1) != null ? m.group(1) + "/" + m.group(2) : repo;
            if (target == null || overlaps(found, m.start(), m.end())) continue;
            found.add(new int[]{m.start(), m.end()});
            links.add(new Piece(Piece.ISSUE, m.group(), "https://github.com/" + target + "/issues/" + m.group(3)));
        }
        List<Piece> out = new ArrayList<>();
        int at = 0;
        while (true) {
            int best = -1;
            for (int i = 0; i < found.size(); i++) {
                if (found.get(i)[0] >= at && (best < 0 || found.get(i)[0] < found.get(best)[0])) best = i;
            }
            if (best < 0) break;
            int[] span = found.get(best);
            if (span[0] > at) out.add(new Piece(Piece.TEXT, text.substring(at, span[0]), null));
            out.add(links.get(best));
            at = span[1];
        }
        if (at < text.length() || out.isEmpty()) out.add(new Piece(Piece.TEXT, text.substring(at), null));
        return out;
    }

    /** {@code :shortcode:} of the built-in table become the emoji; unknown ones stay. */
    @NonNull
    public static String emoji(@NonNull String text) {
        if (text.indexOf(':') < 0) return text;
        Matcher m = EMOJI.matcher(text);
        StringBuilder out = null;
        int at = 0;
        while (m.find()) {
            String emoji = EmojiCodes.get(m.group(1));
            if (emoji == null) continue;
            if (out == null) out = new StringBuilder(text.length());
            out.append(text, at, m.start()).append(emoji);
            at = m.end();
        }
        if (out == null) return text;
        return out.append(text, at, text.length()).toString();
    }

    private static boolean overlaps(@NonNull List<int[]> found, int start, int end) {
        for (int[] span : found) if (start < span[1] && span[0] < end) return true;
        return false;
    }

    private static boolean isRepo(@Nullable String name) {
        return name != null && name.matches(LOGIN + "/[A-Za-z0-9._-]{1,100}");
    }
}
