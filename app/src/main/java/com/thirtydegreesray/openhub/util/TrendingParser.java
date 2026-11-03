package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Element;

/**
 * Reads one repository row ({@code article.Box-row}) of github.com/trending.
 * The page has no API, so it is parsed from HTML. GitHub renames its CSS utility
 * classes from time to time (pr-4 became tmp-pr-4 in 2026), so nothing here depends
 * on class names: the parts are found by what they are (the heading link, the
 * stargazers and forks links, the programmingLanguage item, the "today" counter).
 */
public final class TrendingParser {

    private TrendingParser() {
    }

    public static final class Item {
        public String fullName;
        public String owner;
        public String name;
        public String description = "";
        public String language = "unknown";
        public int stars;
        public int forks;
        public int periodStars;
    }

    /** @return the parsed row, or null when the row has no owner/repo link */
    @Nullable
    public static Item parse(@NonNull Element row) {
        Element link = row.selectFirst("h2 a[href]");
        if (link == null) return null;
        String path = link.attr("href").trim();
        int cut = indexOfAny(path, '?', '#');
        if (cut >= 0) path = path.substring(0, cut);
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.indexOf('/');
        if (slash <= 0 || slash != path.lastIndexOf('/') || slash == path.length() - 1) return null;

        Item item = new Item();
        item.fullName = path;
        item.owner = path.substring(0, slash);
        item.name = path.substring(slash + 1);

        Element desc = row.selectFirst("p");
        if (desc != null) item.description = desc.text().trim();

        Element language = row.selectFirst("[itemprop=programmingLanguage]");
        if (language != null && !language.text().trim().isEmpty()) {
            item.language = language.text().trim();
        }

        Element stars = row.selectFirst("a[href$=\"/stargazers\"]");
        if (stars != null) item.stars = number(stars.text());

        Element forks = row.selectFirst("a[href$=\"/forks\"], a[href$=\"/network/members\"]");
        if (forks != null) item.forks = number(forks.text());

        Element period = row.selectFirst(".float-sm-right");
        if (period != null) item.periodStars = number(period.text());
        return item;
    }

    /** First number in the text, thousands separators removed ("1,138 stars today" -> 1138). */
    static int number(@Nullable String text) {
        if (text == null) return 0;
        int i = 0;
        int n = text.length();
        while (i < n && !Character.isDigit(text.charAt(i))) i++;
        long value = 0;
        boolean any = false;
        for (; i < n; i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
                any = true;
                if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            } else if (c != ',' && c != ' ' && c != ' ') {
                break;
            }
        }
        return any ? (int) value : 0;
    }

    private static int indexOfAny(String s, char a, char b) {
        int x = s.indexOf(a);
        int y = s.indexOf(b);
        if (x < 0) return y;
        if (y < 0) return x;
        return Math.min(x, y);
    }
}
