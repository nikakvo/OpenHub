package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sub-issues: GitHub's JSON turned into rows, issue references typed by the user, and
 * the reorder positions. Pure Java (Gson only), tested in SubIssuesTest.
 */
public final class SubIssues {

    private SubIssues() {
    }

    /** One issue as the sub-issue box and the picker show it. */
    public static class Entry {
        /** GitHub's 64-bit issue id: sub-issues are added, removed and moved by it. */
        public long id;
        public int number;
        public String title = "";
        public String owner = "";
        public String repo = "";
        /** "open" or "closed". */
        public String state = "";
        /** "completed", "not_planned", "duplicate", "reopened" or "". */
        public String stateReason = "";
        public String htmlUrl = "";
        /** True when the issue already has a parent (GitHub's parent_issue_url). */
        public boolean hasParent;
        public boolean isPullRequest;

        public boolean isOpen() {
            return !"closed".equals(state);
        }

        public boolean isNotPlanned() {
            return "not_planned".equals(stateReason) || "duplicate".equals(stateReason);
        }

        public boolean sameRepo(@NonNull String owner, @NonNull String repo) {
            return this.owner.equalsIgnoreCase(owner) && this.repo.equalsIgnoreCase(repo);
        }
    }

    /** An issue typed by the user: "#12", "12", "owner/repo#12" or a github.com issue link. */
    public static class Ref {
        public final String owner;
        public final String repo;
        public final int number;

        Ref(String owner, String repo, int number) {
            this.owner = owner;
            this.repo = repo;
            this.number = number;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("^#?(\\d{1,9})$");
    private static final Pattern REPO_NUMBER = Pattern.compile(
            "^([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9._-]{1,100})#(\\d{1,9})$");
    private static final Pattern LINK = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})/issues/(\\d{1,9})(?:[/?#].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern API_LINK = Pattern.compile(
            "^(?:https?://)?api\\.github\\.com/repos/([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})/issues/(\\d{1,9})/?$",
            Pattern.CASE_INSENSITIVE);

    /** The issue the user typed, or null when the text is not an issue reference. */
    @Nullable
    public static Ref parse(@Nullable String input, @NonNull String owner, @NonNull String repo) {
        if (input == null) return null;
        String text = input.trim();
        if (text.isEmpty()) return null;
        Matcher m = NUMBER.matcher(text);
        if (m.matches()) return ref(owner, repo, m.group(1));
        for (Pattern pattern : new Pattern[]{REPO_NUMBER, LINK, API_LINK}) {
            m = pattern.matcher(text);
            if (m.matches()) return ref(m.group(1), m.group(2), m.group(3));
        }
        return null;
    }

    @Nullable
    private static Ref ref(String owner, String repo, String number) {
        try {
            int n = Integer.parseInt(number);
            return n > 0 ? new Ref(owner, repo, n) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "owner/repo" from "https://api.github.com/repos/owner/repo", or "" for anything else. */
    @NonNull
    public static String repoOf(@Nullable String repositoryUrl) {
        if (repositoryUrl == null) return "";
        int at = repositoryUrl.indexOf("/repos/");
        if (at < 0) return "";
        String rest = repositoryUrl.substring(at + 7);
        while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
        String[] parts = rest.split("/");
        return parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty() ? parts[0] + "/" + parts[1] : "";
    }

    /** An issue of the sub-issues list, the parent answer or the open issues list. */
    @NonNull
    public static Entry entry(@NonNull JsonObject issue) {
        Entry e = new Entry();
        JsonElement id = issue.get("id");
        if (id != null && id.isJsonPrimitive()) {
            try {
                e.id = id.getAsLong();
            } catch (NumberFormatException ignored) {
            }
        }
        e.number = intOf(issue, "number");
        e.title = strOf(issue, "title");
        e.state = strOf(issue, "state");
        e.stateReason = strOf(issue, "state_reason");
        e.htmlUrl = strOf(issue, "html_url");
        String repo = repoOf(strOf(issue, "repository_url"));
        int slash = repo.indexOf('/');
        if (slash > 0) {
            e.owner = repo.substring(0, slash);
            e.repo = repo.substring(slash + 1);
        }
        JsonElement parent = issue.get("parent_issue_url");
        e.hasParent = parent != null && !parent.isJsonNull() && !parent.getAsString().isEmpty();
        JsonElement pull = issue.get("pull_request");
        e.isPullRequest = pull != null && !pull.isJsonNull();
        return e;
    }

    @NonNull
    public static List<Entry> entries(@Nullable JsonArray array) {
        List<Entry> list = new ArrayList<>();
        if (array == null) return list;
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) list.add(entry(element.getAsJsonObject()));
        }
        return list;
    }

    /**
     * Issues the user may pick as a new sub-issue of {@code self}: no pull requests, not the
     * issue itself, not its parent, not one that is already its sub-issue. The text filter
     * matches the number ("12" or "#12") or a part of the title, case-insensitive.
     */
    @NonNull
    public static List<Entry> candidates(@NonNull List<Entry> open, long self, long parent,
                                         @NonNull List<Entry> current, @Nullable String filter) {
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        if (f.startsWith("#")) f = f.substring(1);
        List<Entry> list = new ArrayList<>();
        for (Entry e : open) {
            if (e.isPullRequest || e.id == self || (parent != 0 && e.id == parent)) continue;
            boolean already = false;
            for (Entry c : current) {
                if (c.id == e.id) {
                    already = true;
                    break;
                }
            }
            if (already) continue;
            if (!f.isEmpty() && !String.valueOf(e.number).startsWith(f)
                    && !e.title.toLowerCase(Locale.ROOT).contains(f)) continue;
            list.add(e);
        }
        return list;
    }

    /**
     * Where a sub-issue goes when it moves one place up or down: {before_id, after_id}, one of
     * them 0. Null when it cannot move that way (first up, last down, unknown index).
     */
    @Nullable
    public static long[] move(@NonNull List<Entry> list, int index, boolean up) {
        if (index < 0 || index >= list.size()) return null;
        if (up) {
            if (index == 0) return null;
            return new long[]{list.get(index - 1).id, 0};
        }
        if (index == list.size() - 1) return null;
        return new long[]{0, list.get(index + 1).id};
    }

    /** "#12" for the same repository, "owner/repo#12" for another one. */
    @NonNull
    public static String label(@NonNull Entry e, @NonNull String owner, @NonNull String repo) {
        if (e.owner.isEmpty() || e.sameRepo(owner, repo)) return "#" + e.number;
        return e.owner + "/" + e.repo + "#" + e.number;
    }

    private static String strOf(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() || !v.isJsonPrimitive() ? "" : v.getAsString();
    }

    private static int intOf(JsonObject o, String key) {
        JsonElement v = o.get(key);
        try {
            return v == null || v.isJsonNull() || !v.isJsonPrimitive() ? 0 : v.getAsInt();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
