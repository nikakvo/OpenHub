package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Turns GitHub's blame (GraphQL {@code Commit.blame(path).ranges}) and the file text into
 * the rows of the Blame screen: one header per range (the commit that last changed those
 * lines), then its lines with their numbers. Pure Java, tested.
 */
public final class BlameLines {

    private BlameLines() {
    }

    /** One blame range: lines start..end (1-based, inclusive) last changed by one commit. */
    public static final class Range {
        public final int start;
        public final int end;
        /** 1 = among the newest changes of the file, 10 = among the oldest (GitHub's scale) */
        public final int age;
        public final String oid;
        public final String shortOid;
        public final String headline;
        /** login of the author's GitHub account, or "" */
        public final String login;
        /** git author name, or "" */
        public final String authorName;
        @Nullable public final Date date;

        public Range(int start, int end, int age, @NonNull String oid, @NonNull String shortOid,
                     @NonNull String headline, @NonNull String login, @NonNull String authorName,
                     @Nullable Date date) {
            this.start = start;
            this.end = end;
            this.age = age;
            this.oid = oid;
            this.shortOid = shortOid;
            this.headline = headline;
            this.login = login;
            this.authorName = authorName;
            this.date = date;
        }

        /** The GitHub login when there is one, else the git author name. */
        @NonNull
        public String who() {
            return login.isEmpty() ? authorName : login;
        }
    }

    /** A header (range != null, number 0) or one line of the file. */
    public static final class Row {
        @Nullable public final Range range;
        public final int number;
        @NonNull public final String text;
        /** the range this line belongs to (also set for line rows) */
        @Nullable public final Range owner;

        private Row(@Nullable Range range, int number, @NonNull String text, @Nullable Range owner) {
            this.range = range;
            this.number = number;
            this.text = text;
            this.owner = owner;
        }

        public boolean isHeader() {
            return range != null;
        }
    }

    /**
     * The lines of a text: "\n" and "\r\n" both end a line; a final line break does not
     * start an extra empty line (like git and GitHub count lines).
     */
    @NonNull
    public static List<String> splitLines(@Nullable String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        int start = 0;
        int length = text.length();
        while (start < length) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) {
                lines.add(text.substring(start));
                break;
            }
            int end = newline > start && text.charAt(newline - 1) == '\r' ? newline - 1 : newline;
            lines.add(text.substring(start, end));
            start = newline + 1;
        }
        return lines;
    }

    /** {@code blame} = the GraphQL Blame object: {"ranges": [...]}. Sorted by start line. */
    @NonNull
    public static List<Range> parse(@Nullable JsonObject blame) {
        List<Range> ranges = new ArrayList<>();
        JsonElement items = Json.get(blame, "ranges");
        if (items == null || !items.isJsonArray()) return ranges;
        for (JsonElement e : items.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            int start = Json.integer(r, "startingLine");
            int end = Json.integer(r, "endingLine");
            if (start <= 0 || end < start) continue;
            ranges.add(new Range(start, end, Json.integer(r, "age"),
                    Json.str(r, "commit.oid"), Json.str(r, "commit.abbreviatedOid"),
                    Json.str(r, "commit.messageHeadline"), Json.str(r, "commit.author.user.login"),
                    Json.str(r, "commit.author.name"), Json.date(r, "commit.committedDate")));
        }
        Collections.sort(ranges, (a, b) -> Integer.compare(a.start, b.start));
        return ranges;
    }

    /**
     * Header + lines for every range. Lines the text does not have (a shortened file) are
     * left out, and a range without any line left gets no header either.
     */
    @NonNull
    public static List<Row> rows(@NonNull List<Range> ranges, @NonNull List<String> lines) {
        List<Row> rows = new ArrayList<>();
        for (Range range : ranges) {
            if (range.start > lines.size()) continue;
            rows.add(new Row(range, 0, "", range));
            int last = Math.min(range.end, lines.size());
            for (int n = range.start; n <= last; n++) {
                rows.add(new Row(null, n, lines.get(n - 1), range));
            }
        }
        return rows;
    }

    /**
     * Color of the age bar: orange for the newest changes (age 1) fading to grey for the
     * oldest (10), like github.com's blame. The grey stays clearly visible on the background.
     */
    public static int ageColor(int age, boolean dark) {
        int newest = 0xFFF0883E;
        int oldest = dark ? 0xFF5A636E : 0xFFB4BBC3;
        int clamped = Math.max(1, Math.min(10, age));
        float t = (clamped - 1) / 9f;
        return blend(newest, oldest, t);
    }

    static int blend(int from, int to, float t) {
        int a = channel(from, to, 24, t);
        int r = channel(from, to, 16, t);
        int g = channel(from, to, 8, t);
        int b = channel(from, to, 0, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, int shift, float t) {
        int x = (from >>> shift) & 0xFF;
        int y = (to >>> shift) & 0xFF;
        return Math.round(x + (y - x) * t) & 0xFF;
    }
}
