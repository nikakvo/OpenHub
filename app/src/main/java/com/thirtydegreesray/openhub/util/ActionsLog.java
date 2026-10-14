package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads the plain text log of a GitHub Actions job: removes the timestamps and terminal
 * colour codes, and understands the workflow commands GitHub writes into it
 * ({@code ##[group]}, {@code ##[endgroup]}, {@code ##[error]}, {@code ##[warning]}, ...).
 * Plain Java (unit tested).
 */
public final class ActionsLog {

    public static final int NORMAL = 0;
    public static final int ERROR = 1;
    public static final int WARNING = 2;
    public static final int NOTICE = 3;
    public static final int COMMAND = 4;
    public static final int DEBUG = 5;

    /** A collapsible ##[group] (or, with a null title, the lines outside any group). */
    public static final class Group {
        @Nullable public final String title;
        public int firstLine;
        public int lineCount;
        public boolean hasError;

        Group(@Nullable String title, int firstLine) {
            this.title = title;
            this.firstLine = firstLine;
        }
    }

    public static final class Line {
        @NonNull public final String text;
        public final int kind;
        /** index into {@link #groups} */
        public final int group;

        Line(@NonNull String text, int kind, int group) {
            this.text = text;
            this.kind = kind;
            this.group = group;
        }
    }

    public final List<Line> lines = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    /** lines dropped at the start because the log was longer than the limit */
    public int skipped;

    private static final Pattern TIMESTAMP =
            Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d+)?Z ?");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

    private ActionsLog() {
    }

    @NonNull
    public static ActionsLog read(@NonNull BufferedReader reader, int maxLines) throws IOException {
        ActionsLog log = new ActionsLog();
        Group current = log.newGroup(null);
        int currentIndex = 0;
        String raw;
        boolean first = true;
        while ((raw = reader.readLine()) != null) {
            if (first && raw.startsWith("\uFEFF")) raw = raw.substring(1);
            first = false;
            String text = clean(raw);
            if (text.startsWith("##[group]")) {
                current = log.newGroup(text.substring(9).trim());
                currentIndex = log.groups.size() - 1;
                continue;
            }
            if (text.startsWith("##[endgroup]")) {
                // back to the plain lines; a new "outside" block keeps the order on screen
                current = log.newGroup(null);
                currentIndex = log.groups.size() - 1;
                continue;
            }
            int kind = NORMAL;
            if (text.startsWith("##[error]")) {
                kind = ERROR;
                text = text.substring(9);
            } else if (text.startsWith("##[warning]")) {
                kind = WARNING;
                text = text.substring(11);
            } else if (text.startsWith("##[notice]")) {
                kind = NOTICE;
                text = text.substring(10);
            } else if (text.startsWith("##[command]")) {
                kind = COMMAND;
                text = text.substring(11);
            } else if (text.startsWith("##[debug]")) {
                kind = DEBUG;
                text = text.substring(9);
            } else if (text.startsWith("##[section]")) {
                text = text.substring(11);
            }
            if (kind == ERROR) current.hasError = true;
            log.lines.add(new Line(text, kind, currentIndex));
        }
        log.trim(maxLines);
        log.countLines();
        return log;
    }

    /** The line without timestamp and terminal colour codes. */
    @NonNull
    static String clean(@NonNull String raw) {
        String text = TIMESTAMP.matcher(raw).replaceFirst("");
        if (text.indexOf('\u001B') >= 0) text = ANSI.matcher(text).replaceAll("");
        return text;
    }

    private Group newGroup(@Nullable String title) {
        Group group = new Group(title, lines.size());
        groups.add(group);
        return group;
    }

    /** Keeps the last {@code maxLines} lines: errors are usually at the end. */
    private void trim(int maxLines) {
        if (maxLines <= 0 || lines.size() <= maxLines) return;
        skipped = lines.size() - maxLines;
        lines.subList(0, skipped).clear();
    }

    private void countLines() {
        for (Group group : groups) {
            group.lineCount = 0;
            group.firstLine = -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            Group group = groups.get(lines.get(i).group);
            if (group.firstLine < 0) group.firstLine = i;
            group.lineCount++;
        }
    }

    /** Index of the first line at or after {@code from} containing {@code query}, or -1. */
    public int find(@NonNull String query, int from) {
        String q = query.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return -1;
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            if (lines.get(i).text.toLowerCase(Locale.ROOT).contains(q)) return i;
        }
        return -1;
    }

    /** Index of the first error line at or after {@code from}, or -1. */
    public int nextError(int from) {
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            if (lines.get(i).kind == ERROR) return i;
        }
        return -1;
    }
}
