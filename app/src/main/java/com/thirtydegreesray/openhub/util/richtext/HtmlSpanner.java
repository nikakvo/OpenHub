package com.thirtydegreesray.openhub.util.richtext;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.util.Attachments;
import com.thirtydegreesray.openhub.util.GitHubHelper;
import com.thirtydegreesray.openhub.util.QuoteStyle;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns GitHub's rendered HTML (body_html, bodyHTML, POST /markdown) into Android text:
 * paragraphs, headings, quotes and alerts, lists and task lists, code with GitHub's
 * highlighting, tables, rules, links, mentions, emoji, pictures and videos.
 */
final class HtmlSpanner {

    private static final int NONE = 0;
    private static final int LINE = 1;
    private static final int BLANK = 2;

    private static final String OBJECT = "￼";
    private static final String ZERO_WIDTH = "​";

    private final RichDoc doc;
    private final RichStyle style;
    private final SpannableStringBuilder out;
    private final String playVideo;

    /** pending break before the next content */
    private int pending = NONE;
    private int listDepth;
    private int inListItem;
    /** How many margin blocks (lists, quotes) the current text is inside. */
    private int marginDepth;
    private boolean inPre;
    /** where the last empty line between blocks ends: never two of them in a row */
    private int blankAt = -1;
    /** the text right after an alert's title follows on the next line */
    private boolean tightNext;

    HtmlSpanner(@NonNull RichDoc doc, @NonNull SpannableStringBuilder out, @NonNull String playVideo) {
        this.doc = doc;
        this.style = doc.style;
        this.out = out;
        this.playVideo = playVideo;
    }

    void render(@NonNull Element root) {
        children(root);
        trimEnd();
    }

    // ---- structure

    private void children(@NonNull Element e) {
        for (Node child : new ArrayList<>(e.childNodes())) node(child);
    }

    private void node(@NonNull Node n) {
        if (n instanceof TextNode) text((TextNode) n);
        else if (n instanceof Element) element((Element) n);
    }

    private int gap() {
        return inListItem > 0 ? LINE : BLANK;
    }

    private void breakBefore(int kind) {
        pending = Math.max(pending, kind);
    }

    /** Writes the pending break; the next character starts a new line. */
    private void flush() {
        if (out.length() == 0) {
            pending = NONE;
            return;
        }
        if (pending >= LINE && out.charAt(out.length() - 1) != '\n') {
            // no space at the end of a line
            int end = out.length();
            while (end > 0 && out.charAt(end - 1) == ' ') end--;
            if (end < out.length()) out.delete(end, out.length());
            out.append('\n');
        }
        if (pending == BLANK && blankAt != out.length()) {
            int s = out.length();
            out.append('\n');
            // the empty line between two blocks is half a line high
            out.setSpan(new RelativeSizeSpan(0.5f), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            blankAt = out.length();
        }
        pending = NONE;
    }

    /** Starts a block: returns where its text begins. */
    private int open(int kind) {
        if (tightNext) {
            tightNext = false;
            kind = Math.min(kind, LINE);
            pending = Math.min(pending, LINE);
        }
        breakBefore(kind);
        flush();
        return out.length();
    }

    private void close(int kind) {
        breakBefore(kind);
    }

    private void span(@NonNull Object what, int start) {
        if (out.length() > start) out.setSpan(what, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * A span that moves the text to the right (list item, quote bar, code block). The
     * outer block of a nested pair is added last, but Android draws the margins in the
     * order it gets the spans: a higher priority for the outer block keeps the inner
     * marker or bar next to its own text instead of at the left edge.
     */
    private void marginSpan(@NonNull Object what, int start) {
        if (out.length() <= start) return;
        int priority = Math.max(1, 200 - marginDepth);
        out.setSpan(what, start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | (priority << Spanned.SPAN_PRIORITY_SHIFT));
    }

    // ---- text

    private void text(@NonNull TextNode node) {
        if (inPre) {
            String raw = node.getWholeText();
            if (raw.isEmpty()) return;
            flush();
            out.append(raw);
            return;
        }
        String s = node.getWholeText().replaceAll("[ \\t\\n\\r\\f]+", " ");
        if (s.isEmpty()) return;
        if (s.trim().isEmpty()) {
            // only a space: between two words, not at the start of a line or a block
            if (pending == NONE && out.length() > 0 && !endsWithSpace()) out.append(' ');
            return;
        }
        flush();
        if (s.charAt(0) == ' ' && (out.length() == 0 || endsWithSpace())) s = s.substring(1);
        out.append(s);
    }

    private boolean endsWithSpace() {
        char last = out.charAt(out.length() - 1);
        return last == ' ' || last == '\n';
    }

    private void append(@NonNull String s) {
        flush();
        out.append(s);
    }

    private void trimEnd() {
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == '\n' || out.charAt(end - 1) == ' ')) end--;
        if (end < out.length()) out.delete(end, out.length());
    }

    // ---- elements

    private void element(@NonNull Element e) {
        String tag = e.normalName();
        if (skipped(e, tag)) return;
        int start;
        switch (tag) {
            case "br":
                if (pending != NONE) flush();
                out.append('\n');
                return;
            case "p":
                if (e.hasClass("markdown-alert-title")) {
                    alertTitle(e);
                    return;
                }
                start = open(gap());
                children(e);
                close(gap());
                return;
            case "div":
                if (e.hasClass("markdown-alert")) {
                    alert(e);
                    return;
                }
                start = open(LINE);
                children(e);
                close(LINE);
                return;
            case "section": case "article": case "header": case "footer": case "figure":
            case "figcaption": case "dl": case "dt": case "dd": case "details": case "main":
            case "aside": case "nav": case "address":
                open(LINE);
                children(e);
                close(LINE);
                return;
            case "summary":
                start = open(LINE);
                append("▾ ");
                children(e);
                span(new Spans.Bold(), start);
                close(LINE);
                return;
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                heading(e, tag.charAt(1) - '0');
                return;
            case "blockquote":
                start = open(gap());
                marginDepth++;
                children(e);
                marginDepth--;
                marginSpan(new QuoteStyle.BarSpan(style.quoteBar, style.quoteBarWidth, style.quoteGap), start);
                span(new ForegroundColorSpan(style.secondaryColor), start);
                close(gap());
                return;
            case "ul": case "ol":
                list(e, tag.equals("ol"));
                return;
            case "li":
                // a list item outside a list: as a bullet item
                listItem(e, "•");
                return;
            case "pre":
                pre(e);
                return;
            case "code": case "kbd": case "samp": case "tt":
                if (inPre) {
                    children(e);
                    return;
                }
                start = out.length();
                children(e);
                span(new TypefaceSpan("monospace"), start);
                span(new BackgroundColorSpan(style.codeBackground), start);
                span(new RelativeSizeSpan(0.9f), start);
                return;
            case "span":
                start = out.length();
                children(e);
                if (inPre) {
                    Integer color = codeColor(e);
                    if (color != null) span(new ForegroundColorSpan(color), start);
                }
                return;
            case "a":
                link(e);
                return;
            case "img":
                image(e, true);
                return;
            case "video":
                video(e);
                return;
            case "b": case "strong":
                start = out.length();
                children(e);
                span(new Spans.Bold(), start);
                return;
            case "i": case "em": case "cite": case "var": case "dfn":
                start = out.length();
                children(e);
                span(new StyleSpan(Typeface.ITALIC), start);
                return;
            case "del": case "s": case "strike":
                start = out.length();
                children(e);
                span(new StrikethroughSpan(), start);
                return;
            case "u": case "ins":
                start = out.length();
                children(e);
                span(new UnderlineSpan(), start);
                return;
            case "sup":
                start = out.length();
                children(e);
                span(new SuperscriptSpan(), start);
                span(new RelativeSizeSpan(0.75f), start);
                return;
            case "sub":
                start = out.length();
                children(e);
                span(new SubscriptSpan(), start);
                span(new RelativeSizeSpan(0.75f), start);
                return;
            case "small":
                start = out.length();
                children(e);
                span(new RelativeSizeSpan(0.85f), start);
                return;
            case "mark":
                start = out.length();
                children(e);
                span(new BackgroundColorSpan(style.dark ? 0x66BB8009 : 0x66FFF8C5), start);
                return;
            case "hr":
                start = open(gap());
                out.append(ZERO_WIDTH);
                span(new Spans.Rule(style.borderColor, Math.max(1, style.px(1))), start);
                close(gap());
                return;
            case "table":
                table(e);
                return;
            default:
                // g-emoji, math-renderer, markdown-accessiblity-table and other wrappers
                children(e);
        }
    }

    private static boolean skipped(@NonNull Element e, @NonNull String tag) {
        switch (tag) {
            case "svg": case "script": case "style": case "template": case "button":
            case "clipboard-copy": case "input": case "tool-tip": case "include-fragment":
            case "noscript": case "head": case "title": case "meta": case "link":
                return true;
            default:
                // the hidden "Footnotes" heading and the permalink icons of headings
                return e.hasClass("sr-only") || e.hasAttr("hidden")
                        || (tag.equals("a") && e.hasClass("anchor"));
        }
    }

    private void heading(@NonNull Element e, int level) {
        float[] sizes = {1.6f, 1.4f, 1.2f, 1.05f, 0.95f, 0.9f};
        int start = open(gap());
        children(e);
        span(new Spans.Bold(), start);
        span(new RelativeSizeSpan(sizes[Math.max(1, Math.min(6, level)) - 1]), start);
        close(gap());
    }

    private void alert(@NonNull Element e) {
        String type = "note";
        for (String cls : e.classNames()) {
            if (cls.startsWith("markdown-alert-")) type = cls.substring("markdown-alert-".length());
        }
        int color = style.alertColor(type);
        int start = open(gap());
        e.attr("data-alert-type", type);
        marginDepth++;
        children(e);
        marginDepth--;
        marginSpan(new QuoteStyle.BarSpan(color, style.quoteBarWidth, style.quoteGap), start);
        close(gap());
    }

    private void alertTitle(@NonNull Element e) {
        Element alert = e.parent();
        String type = alert == null ? "note" : alert.attr("data-alert-type");
        if (type.isEmpty()) type = "note";
        int start = open(LINE);
        append(RichStyle.alertMark(type));
        children(e);
        span(new Spans.Bold(), start);
        span(new ForegroundColorSpan(style.alertColor(type)), start);
        close(LINE);
        tightNext = true;
    }

    private void list(@NonNull Element e, boolean ordered) {
        open(listDepth == 0 ? gap() : LINE);
        listDepth++;
        int number = 1;
        if (ordered) {
            try {
                number = Integer.parseInt(e.attr("start").trim());
            } catch (NumberFormatException ignored) {
                number = 1;
            }
        }
        for (Element child : e.children()) {
            if (!child.normalName().equals("li")) {
                element(child);
                continue;
            }
            String marker = ordered ? number + "." : bullet();
            listItem(child, marker);
            number++;
        }
        listDepth--;
        close(listDepth == 0 ? gap() : LINE);
    }

    @NonNull
    private String bullet() {
        switch (Math.max(0, listDepth - 1) % 3) {
            case 0: return "•";
            case 1: return "◦";
            default: return "▪";
        }
    }

    private void listItem(@NonNull Element li, @NonNull String marker) {
        Element box = taskBox(li);
        if (box != null) {
            marker = box.hasAttr("checked") ? "☑" : "☐";
            box.remove();
        }
        int start = open(LINE);
        inListItem++;
        marginDepth++;
        children(li);
        marginDepth--;
        inListItem--;
        if (out.length() == start) out.append(ZERO_WIDTH);
        marginSpan(new Spans.ListItem(marker, style.px(24)), start);
        close(LINE);
    }

    /** The checkbox of a task list item ({@code - [x] done}), at the start of the item. */
    @Nullable
    private static Element taskBox(@NonNull Element li) {
        Element first = li.selectFirst("> input[type=checkbox], > p > input[type=checkbox]");
        return first;
    }

    private void pre(@NonNull Element e) {
        int start = open(gap());
        boolean was = inPre;
        inPre = true;
        children(e);
        inPre = was;
        // the last line break of a code block is not an empty line
        while (out.length() > start && out.charAt(out.length() - 1) == '\n') out.delete(out.length() - 1, out.length());
        if (out.length() == start) out.append(' ');
        span(new TypefaceSpan("monospace"), start);
        span(new RelativeSizeSpan(0.85f), start);
        marginSpan(new Spans.CodeBlock(style.codeBackground, style.px(8), style.secondaryColor,
                style.px(16), style.density * 1.5f), start);
        close(gap());
    }

    @Nullable
    private Integer codeColor(@NonNull Element e) {
        for (String cls : e.classNames()) {
            Integer color = style.codeColor(cls);
            if (color != null) return color;
        }
        return null;
    }

    // ---- links, pictures, videos

    private void link(@NonNull Element a) {
        String href = a.attr("href").trim();
        Element only = onlyImage(a);
        if (only != null && looksLikeImage(href, only)) {
            // a picture GitHub links to itself: a tap opens it in the viewer
            image(only, true);
            return;
        }
        if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) {
            children(a);
            return;
        }
        int start = out.length();
        if (only != null) image(only, false);
        else children(a);
        if (style.clickable) span(new Spans.Link(href, Spans.Link.LINK), start);
        else span(new ForegroundColorSpan(linkColorFallback()), start);
    }

    private int linkColorFallback() {
        return style.dark ? 0xFF4493F8 : 0xFF0969DA;
    }

    @Nullable
    private static Element onlyImage(@NonNull Element a) {
        if (a.children().size() != 1 || !a.child(0).normalName().equals("img")) return null;
        for (Node n : a.childNodes()) {
            if (n instanceof TextNode && !((TextNode) n).isBlank()) return null;
        }
        return a.child(0);
    }

    private static boolean looksLikeImage(@NonNull String href, @NonNull Element img) {
        if (href.isEmpty()) return true;
        if (href.equals(img.attr("src")) || href.equals(img.attr("data-canonical-src"))) return true;
        String lower = href.toLowerCase(Locale.ROOT);
        return lower.contains("private-user-images.githubusercontent.com")
                || lower.contains("camo.githubusercontent.com")
                || lower.contains("/user-attachments/assets/")
                || lower.contains("user-images.githubusercontent.com")
                || GitHubHelper.isImage(href.replaceAll("[?#].*$", ""));
    }

    private void image(@NonNull Element img, boolean opensViewer) {
        String src = img.attr("src").trim();
        String canonical = img.attr("data-canonical-src").trim();
        if (src.isEmpty()) src = canonical;
        if (src.isEmpty()) return;
        // the link of the viewer: GitHub's own link of a fresh upload, else what is loaded
        String viewUrl = canonical.contains("/user-attachments/") ? canonical : src;
        boolean emoji = img.hasClass("emoji");
        if (!style.loadImages && !emoji) {
            // pictures switched off (Settings, on mobile data): the picture as a link
            String alt = img.attr("alt").trim();
            int start = out.length();
            append("[" + (alt.isEmpty() ? "image" : alt) + "]");
            if (style.clickable) span(new Spans.Link(viewUrl, Spans.Link.IMAGE), start);
            span(new ForegroundColorSpan(linkColorFallback()), start);
            return;
        }
        String local = Attachments.localCopy(canonical.isEmpty() ? src : canonical);
        if (local == null) local = Attachments.localCopy(src);
        Object source = local != null ? android.net.Uri.parse(local) : src;
        ImageSpan span = new ImageSpan(doc, source, dimension(img, "width"), dimension(img, "height"), emoji);
        doc.images.add(span);
        flush();
        int start = out.length();
        out.append(OBJECT);
        out.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (opensViewer && style.clickable && !emoji) {
            out.setSpan(new Spans.Link(viewUrl, Spans.Link.IMAGE), start, start + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** width="300" or height="200" of a picture (CSS pixels); 0 when missing or in %. */
    private static int dimension(@NonNull Element img, @NonNull String name) {
        String value = img.attr(name).trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("px")) value = value.substring(0, value.length() - 2).trim();
        try {
            return Math.max(0, (int) Float.parseFloat(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void video(@NonNull Element video) {
        String src = video.attr("src").trim();
        String canonical = video.attr("data-canonical-src").trim();
        if (src.isEmpty()) {
            Element source = video.selectFirst("source[src]");
            if (source != null) src = source.attr("src").trim();
        }
        // GitHub's own link of an upload (github.com/user-attachments/...) when there is one:
        // the player knows it and plays a fresh upload from the phone
        String url = canonical.contains("/user-attachments/") ? canonical : (src.isEmpty() ? canonical : src);
        if (url.isEmpty()) return;
        int start = open(LINE);
        append("▶ " + playVideo);
        if (style.clickable) span(new Spans.Link(url, Spans.Link.VIDEO), start);
        else span(new ForegroundColorSpan(linkColorFallback()), start);
        span(new Spans.Bold(), start);
        close(LINE);
    }

    // ---- tables

    private void table(@NonNull Element table) {
        List<Element> rows = table.select("> thead > tr, > tbody > tr, > tfoot > tr, > tr");
        if (rows.isEmpty()) return;
        int columns = 0;
        for (Element row : rows) columns = Math.max(columns, cellsOf(row).size());
        if (columns == 0) return;
        int tableStart = open(gap());
        doc.hasTables = true;
        boolean first = true;
        for (Element row : rows) {
            List<Element> cells = cellsOf(row);
            CharSequence[] texts = new CharSequence[columns];
            Layout.Alignment[] alignments = new Layout.Alignment[columns];
            boolean header = !cells.isEmpty();
            for (int i = 0; i < columns; i++) {
                Element cell = i < cells.size() ? cells.get(i) : null;
                SpannableStringBuilder cellText = new SpannableStringBuilder();
                if (cell != null) {
                    HtmlSpanner inner = new HtmlSpanner(doc, cellText, playVideo);
                    inner.render(cell);
                    if (cell.normalName().equals("th") && cellText.length() > 0) {
                        cellText.setSpan(new Spans.Bold(), 0, cellText.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (!cell.normalName().equals("th")) header = false;
                }
                texts[i] = cellText;
                alignments[i] = alignment(cell);
            }
            breakBefore(LINE);
            flush();
            int start = out.length();
            out.append(OBJECT);
            out.setSpan(new TableRowSpan(doc, texts, alignments, columns, header, first),
                    start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            breakBefore(LINE);
            first = false;
        }
        // a tap opens the table on its own page: wide tables scroll sideways there, and the
        // links in its cells work
        if (style.clickable && out.length() > tableStart) {
            out.setSpan(new Spans.Link(table.outerHtml(), Spans.Link.TABLE), tableStart, out.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        close(gap());
    }

    @NonNull
    private static List<Element> cellsOf(@NonNull Element row) {
        List<Element> cells = new ArrayList<>();
        for (Element c : row.children()) {
            String n = c.normalName();
            if (n.equals("td") || n.equals("th")) cells.add(c);
        }
        return cells;
    }

    @NonNull
    private static Layout.Alignment alignment(@Nullable Element cell) {
        if (cell == null) return Layout.Alignment.ALIGN_NORMAL;
        String align = cell.attr("align").toLowerCase(Locale.ROOT);
        String css = cell.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
        if (align.equals("center") || css.contains("text-align:center")) return Layout.Alignment.ALIGN_CENTER;
        if (align.equals("right") || css.contains("text-align:right")) return Layout.Alignment.ALIGN_OPPOSITE;
        return Layout.Alignment.ALIGN_NORMAL;
    }
}
