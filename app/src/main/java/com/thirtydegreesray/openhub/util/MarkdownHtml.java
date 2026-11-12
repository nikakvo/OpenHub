package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * Turns Markdown into HTML on the phone (commonmark-java), for text that does not come
 * pre-rendered from GitHub: gist files, the help page, drafts and the offline preview.
 */
public final class MarkdownHtml {

    private static final List<Extension> EXTENSIONS = Arrays.asList(TablesExtension.create(),
            StrikethroughExtension.create(), TaskListItemsExtension.create(), AlertsExtension.create(),
            AutolinkExtension.create(), FootnotesExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    /** comments: also @user, #123 and :emoji: like github.com (here without a repository) */
    private static final Parser COMMENT_PARSER = commentParser(null);
    private static final HtmlRenderer DOCUMENT = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    /** in comments a single line break is a line break, as on github.com */
    private static final HtmlRenderer COMMENT = HtmlRenderer.builder().extensions(EXTENSIONS)
            .softbreak("<br />\n")
            .nodeRendererFactory(MarkdownRefs::renderer)
            .build();

    private MarkdownHtml() {
    }

    /** A document (README, wiki page, gist file) for the viewer. */
    @NonNull
    public static String render(@NonNull String markdown) {
        return "<div class=\"markdown-body\">" + DOCUMENT.render(PARSER.parse(markdown)) + "</div>";
    }

    /** A comment, issue or pull request text: line breaks stay line breaks. */
    @NonNull
    public static String renderComment(@NonNull String markdown) {
        return renderComment(markdown, null);
    }

    /**
     * Like {@link #renderComment(String)}; with {@code repoFullName} ("owner/repo") a bare
     * {@code #123} links to that repository's issue, without it only {@code owner/repo#123} does.
     */
    @NonNull
    public static String renderComment(@NonNull String markdown, @Nullable String repoFullName) {
        Parser parser = repoFullName == null ? COMMENT_PARSER : commentParser(repoFullName);
        return "<div class=\"markdown-body\">" + COMMENT.render(parser.parse(markdown)) + "</div>";
    }

    @NonNull
    private static Parser commentParser(@Nullable String repoFullName) {
        return Parser.builder().extensions(EXTENSIONS).postProcessor(new MarkdownRefs(repoFullName)).build();
    }
}
