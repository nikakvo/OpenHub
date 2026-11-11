package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.WikiFeedModel;
import com.thirtydegreesray.openhub.mvp.model.WikiModel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;

/**
 * Reads GitHub's wiki Atom feed (github.com/owner/repo/wiki.atom, "Recent Wiki Updates")
 * with jsoup's XML parser. Anything that is not an Atom feed (a repository without a
 * wiki answers with an HTML page) gives an empty list.
 */
public final class WikiFeed {

    private WikiFeed() {
    }

    @NonNull
    public static WikiFeedModel parse(@NonNull String xml) {
        WikiFeedModel feed = new WikiFeedModel();
        ArrayList<WikiModel> list = new ArrayList<>();
        feed.setWikiList(list);
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        Element root = doc.selectFirst("feed");
        if (root == null) return feed;
        feed.setId(childText(root, "id"));
        feed.setTitle(childText(root, "title"));
        feed.setUpdated(childText(root, "updated"));
        for (Element entry : root.children()) {
            if (!"entry".equals(entry.normalName())) continue;
            WikiModel wiki = new WikiModel();
            wiki.setId(childText(entry, "id"));
            wiki.setPublished(childText(entry, "published"));
            wiki.setUpdated(childText(entry, "updated"));
            Element content = child(entry, "content");
            // type="html": the page's HTML is escaped text inside the element
            wiki.setContent(content == null ? "" : content.wholeText());
            list.add(wiki);
        }
        return feed;
    }

    private static Element child(@NonNull Element parent, @NonNull String name) {
        for (Element e : parent.children()) {
            if (name.equals(e.normalName())) return e;
        }
        return null;
    }

    private static String childText(@NonNull Element parent, @NonNull String name) {
        Element e = child(parent, name);
        return e == null ? null : e.text();
    }
}
