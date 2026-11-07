package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggested changes in pull request review comments (```suggestion blocks), like "Commit
 * suggestion" and "Add suggestion to batch" on github.com. Pure Java.
 */
public final class Suggestions {

    /** A fence line that opens a suggestion: ```suggestion (3 or more backticks or tildes). */
    private static final Pattern OPEN = Pattern.compile("^[ ]{0,3}(`{3,}|~{3,})[ \\t]*suggestion[ \\t]*$");

    private Suggestions() {
    }

    /** One suggestion to apply: lines {@code start}..{@code end} (1-based, inclusive) become {@code text}. */
    public static final class Edit {
        public final long commentId;
        @NonNull public final String path;
        public final int start;
        public final int end;
        /** The new lines; "" removes the lines. Without a final line break. */
        @NonNull public final String text;
        @NonNull public final String authorLogin;
        public final long authorId;
        /** The commented line as the comment saw it (last line of its diff hunk); null = unknown. */
        @Nullable public final String expectedLast;

        public Edit(long commentId, @NonNull String path, int start, int end, @NonNull String text,
                    @NonNull String authorLogin, long authorId) {
            this(commentId, path, start, end, text, authorLogin, authorId, null);
        }

        public Edit(long commentId, @NonNull String path, int start, int end, @NonNull String text,
                    @NonNull String authorLogin, long authorId, @Nullable String expectedLast) {
            this.expectedLast = expectedLast;
            this.commentId = commentId;
            this.path = path;
            this.start = start;
            this.end = end;
            this.text = text;
            this.authorLogin = authorLogin;
            this.authorId = authorId;
        }
    }

    /**
     * The text of the first suggestion block in a comment, without its final line break;
     * "" for an empty block (the lines are removed); null when there is none or it is not closed.
     */
    @Nullable
    public static String parse(@Nullable String body) {
        if (body == null) return null;
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher open = OPEN.matcher(lines[i]);
            if (!open.matches()) continue;
            String fence = open.group(1);
            char mark = fence.charAt(0);
            StringBuilder text = new StringBuilder();
            for (int j = i + 1; j < lines.length; j++) {
                String trimmed = lines[j].trim();
                if (trimmed.length() >= fence.length() && isFence(trimmed, mark)) {
                    return text.toString();
                }
                if (j > i + 1) text.append('\n');
                text.append(lines[j]);
            }
            return null;
        }
        return null;
    }

    private static boolean isFence(String line, char mark) {
        for (int k = 0; k < line.length(); k++) {
            if (line.charAt(k) != mark) return false;
        }
        return true;
    }

    /**
     * Applies the edits to a file. Keeps the file's line breaks (\r\n or \n) and whether it ends
     * with one. Throws IllegalArgumentException when a line is outside the file or two edits touch
     * the same lines.
     */
    @NonNull
    public static String apply(@NonNull String file, @NonNull List<Edit> edits) {
        String eol = file.contains("\r\n") ? "\r\n" : "\n";
        String normalized = file.replace("\r\n", "\n");
        boolean endsWithBreak = normalized.endsWith("\n");
        String content = endsWithBreak ? normalized.substring(0, normalized.length() - 1) : normalized;
        List<String> lines = new ArrayList<>();
        if (!content.isEmpty() || endsWithBreak) Collections.addAll(lines, content.split("\n", -1));

        List<Edit> sorted = new ArrayList<>(edits);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.start, a.start));
        int lowestTouched = Integer.MAX_VALUE;
        for (Edit e : sorted) {
            if (e.start < 1 || e.end < e.start || e.end > lines.size()) {
                throw new IllegalArgumentException("Line " + e.start + "-" + e.end + " is not in " + e.path);
            }
            if (e.end >= lowestTouched) {
                throw new IllegalArgumentException("Two suggestions change the same lines of " + e.path);
            }
            if (e.expectedLast != null && !e.expectedLast.equals(lines.get(e.end - 1))) {
                throw new IllegalStateException("Line " + e.end + " of " + e.path
                        + " changed since the suggestion was made");
            }
            lowestTouched = e.start;
            List<String> replacement = new ArrayList<>();
            if (!e.text.isEmpty()) Collections.addAll(replacement, e.text.replace("\r\n", "\n").split("\n", -1));
            for (int i = e.end; i >= e.start; i--) lines.remove(i - 1);
            lines.addAll(e.start - 1, replacement);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append(eol);
            out.append(lines.get(i));
        }
        if (endsWithBreak && !lines.isEmpty()) out.append(eol);
        return out.toString();
    }

    /**
     * The suggested change of a REST review comment, or null when it has none or it cannot be
     * applied any more (outdated: no line; or on removed lines).
     */
    @Nullable
    public static Edit fromComment(@NonNull JsonObject c) {
        String text = parse(Json.str(c, "body"));
        if (text == null) return null;
        int line = Json.str(c, "line").isEmpty() ? 0 : Json.integer(c, "line");
        if (line <= 0 || "LEFT".equals(Json.str(c, "side"))) return null;
        int start = line;
        if (!Json.str(c, "start_line").isEmpty() && !"LEFT".equals(Json.str(c, "start_side"))) {
            start = Math.min(line, Json.integer(c, "start_line"));
        }
        JsonElement id = Json.get(c, "id");
        JsonElement uid = Json.get(c, "user.id");
        return new Edit(id != null && id.isJsonPrimitive() ? id.getAsLong() : 0, Json.str(c, "path"),
                start, line, text, Json.str(c, "user.login"),
                uid != null && uid.isJsonPrimitive() ? uid.getAsLong() : 0, lastHunkLine(Json.str(c, "diff_hunk")));
    }

    /** True when both describe the same change (same file, lines and text). */
    public static boolean sameChange(@NonNull Edit a, @NonNull Edit b) {
        return a.path.equals(b.path) && a.start == b.start && a.end == b.end && a.text.equals(b.text);
    }

    /**
     * The new-file line a review comment points at: the last line of its diff hunk without the
     * leading "+" or " ". Null when the hunk is empty or ends with a removed line.
     */
    @Nullable
    public static String lastHunkLine(@Nullable String diffHunk) {
        if (diffHunk == null || diffHunk.isEmpty()) return null;
        String[] lines = diffHunk.replace("\r\n", "\n").split("\n");
        String last = lines[lines.length - 1];
        if (last.isEmpty() || last.startsWith("@@")) return null;
        char mark = last.charAt(0);
        if (mark != '+' && mark != ' ') return null;
        return last.substring(1);
    }

    /** The edits grouped by file, files in the order they first appear. */
    @NonNull
    public static Map<String, List<Edit>> byFile(@NonNull List<Edit> edits) {
        Map<String, List<Edit>> map = new LinkedHashMap<>();
        for (Edit e : edits) {
            List<Edit> list = map.get(e.path);
            if (list == null) {
                list = new ArrayList<>();
                map.put(e.path, list);
            }
            list.add(e);
        }
        return map;
    }

    /** GitHub's default message: "Apply suggestion from code review" or "...suggestions...". */
    @NonNull
    public static String defaultMessage(int count) {
        return count == 1 ? "Apply suggestion from code review" : "Apply suggestions from code review";
    }

    /**
     * "Co-authored-by: login <id+login@users.noreply.github.com>" for every author of the edits
     * other than {@code self}, each once, so the suggestions' authors get credit like on github.com.
     */
    @NonNull
    public static String coAuthors(@NonNull List<Edit> edits, @Nullable String self) {
        StringBuilder text = new StringBuilder();
        List<String> seen = new ArrayList<>();
        for (Edit e : edits) {
            String login = e.authorLogin.trim();
            if (login.isEmpty()) continue;
            String key = login.toLowerCase(Locale.ROOT);
            if (seen.contains(key) || (self != null && key.equals(self.trim().toLowerCase(Locale.ROOT)))) continue;
            seen.add(key);
            if (text.length() > 0) text.append('\n');
            String email = e.authorId > 0 ? e.authorId + "+" + login + "@users.noreply.github.com"
                    : login + "@users.noreply.github.com";
            text.append("Co-authored-by: ").append(login).append(" <").append(email).append('>');
        }
        return text.toString();
    }

    /** The commit description: the user's text, then the co-author lines after an empty line. */
    @NonNull
    public static String description(@Nullable String text, @NonNull String coAuthors) {
        String t = text == null ? "" : text.trim();
        if (coAuthors.isEmpty()) return t;
        return t.isEmpty() ? coAuthors : t + "\n\n" + coAuthors;
    }
}
