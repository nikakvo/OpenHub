package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which tab of a pull request a github.com link means. Pure Java. */
public final class PullLinks {

    public static final int CONVERSATION = 0;
    public static final int COMMITS = 1;
    public static final int FILES = 2;

    private static final Pattern TAB = Pattern.compile(
            "github\\.com/[^/]+/[^/]+/pull/\\d+/([A-Za-z]+)");

    private PullLinks() {
    }

    /**
     * github.com/o/r/pull/12/files (also /changes, GitHub's newer name, and /files#diff-...)
     * = Files, /commits (and /commits/sha) = Commits, everything else = Conversation.
     */
    public static int tab(@NonNull String url) {
        Matcher m = TAB.matcher(url);
        if (!m.find()) return CONVERSATION;
        switch (m.group(1)) {
            case "files":
            case "changes":
                return FILES;
            case "commits":
                return COMMITS;
            default:
                return CONVERSATION;
        }
    }
}
