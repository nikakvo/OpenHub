package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import org.commonmark.Extension;
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
            StrikethroughExtension.create(), TaskListItemsExtension.create(), AlertsExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer DOCUMENT = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    /** in comments a single line break is a line break, as on github.com */
    private static final HtmlRenderer COMMENT = HtmlRenderer.builder().extensions(EXTENSIONS)
            .softbreak("<br />\n").build();

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
        return "<div class=\"markdown-body\">" + COMMENT.render(PARSER.parse(markdown)) + "</div>";
    }
}
