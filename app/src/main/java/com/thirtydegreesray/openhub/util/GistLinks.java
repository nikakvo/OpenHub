package com.thirtydegreesray.openhub.util;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The id of a gist.github.com link ({@code /user/id} or {@code /id}), else null. Unit tested. */
public final class GistLinks {

    private static final Pattern GIST = Pattern.compile(
            "^https?://gist\\.github\\.com/(?:[^/?#]+/)?([0-9a-f]{5,})(?:/[0-9a-f]{40})?/?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    private GistLinks() {
    }

    @Nullable
    public static String id(@Nullable String url) {
        if (url == null) return null;
        Matcher m = GIST.matcher(url.trim());
        return m.matches() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }
}
