package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * Turns Markdown into HTML on the phone (commonmark-java, which Markwon already brings),
 * for text that does not come pre-rendered from GitHub: gist files and the help page.
 * The viewer expects HTML.
 */
public final class MarkdownHtml {

    private static final List<Extension> EXTENSIONS =
            Arrays.asList(TablesExtension.create(), StrikethroughExtension.create());

    private MarkdownHtml() {
    }

    @NonNull
    public static String render(@NonNull String markdown) {
        Parser parser = Parser.builder().extensions(EXTENSIONS).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();
        return "<div class=\"markdown-body\">" + renderer.render(parser.parse(markdown)) + "</div>";
    }
}
