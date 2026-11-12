package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The text of "Quote reply" and "Mention @login" in the comment menus, the same as
 * github.com: the comment's Markdown with "> " before every line, or "@login ".
 */
public final class CommentQuote {

    private CommentQuote() {
    }

    /**
     * Every line of {@code body} with "> " before it (an empty line becomes ">"), then
     * an empty line for the answer. Empty when there is nothing to quote.
     */
    @NonNull
    public static String quote(@Nullable String body) {
        if (body == null) return "";
        String text = body.replace("\r\n", "\n").replace('\r', '\n');
        // no empty quote lines at the end
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        text = text.substring(0, end);
        // no empty quote lines at the start either, but the first line keeps its indent
        int start = 0;
        int lineStart = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            if (text.charAt(start) == '\n') lineStart = start + 1;
            start++;
        }
        text = text.substring(lineStart);
        if (text.trim().isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length() + 64);
        for (String line : text.split("\n", -1)) {
            if (line.trim().isEmpty()) out.append(">\n");
            else out.append("> ").append(line).append('\n');
        }
        return out.append('\n').toString();
    }

    /** "@login " for "Mention". */
    @NonNull
    public static String mention(@NonNull String login) {
        return "@" + login + " ";
    }

    /**
     * Whether the author of a comment can be mentioned: not yourself, not a deleted user
     * ("ghost" or no login) and not a bot (bots have no profile to mention).
     */
    public static boolean canMention(@Nullable String login, @Nullable String me, boolean bot) {
        if (login == null || login.trim().isEmpty() || bot) return false;
        String l = login.trim().toLowerCase(Locale.ROOT);
        if (l.equals("ghost") || l.endsWith("[bot]")) return false;
        return me == null || !l.equals(me.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The quote added to what is already written: after it, with one empty line between;
     * never in place of it.
     */
    @NonNull
    public static String addQuote(@Nullable String draft, @NonNull String quote) {
        if (draft == null || draft.trim().isEmpty()) return quote;
        return trimEnd(draft) + "\n\n" + quote;
    }

    /**
     * "@login " added to what is already written, with a space before it when needed; not
     * added again when the text already ends with that mention.
     */
    @NonNull
    public static String addMention(@Nullable String draft, @NonNull String login) {
        String mention = mention(login);
        if (draft == null || draft.trim().isEmpty()) return mention;
        String body = trimEnd(draft);
        String tag = "@" + login.toLowerCase(Locale.ROOT);
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.endsWith(tag)) {
            int before = body.length() - tag.length() - 1;
            if (before < 0 || Character.isWhitespace(body.charAt(before))) return body + " ";
        }
        if (Character.isWhitespace(draft.charAt(draft.length() - 1)) && !draft.endsWith("\n")) {
            return draft + mention;
        }
        return body + (draft.endsWith("\n") ? "\n" : " ") + mention;
    }

    @NonNull
    private static String trimEnd(@NonNull String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
