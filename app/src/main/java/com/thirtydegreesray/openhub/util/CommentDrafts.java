package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The unsent text of a new comment, per place (an issue, a pull request, a discussion or a
 * reply in it, a gist). Saved while typing, so it survives Back, Cancel, closing the app and
 * Android ending the process; removed when the comment is sent or the box is emptied.
 * Drafts older than {@link #MAX_AGE_MS} are dropped, at most {@link #MAX_DRAFTS} are kept.
 *
 * Stored in its own preferences file: "t:" + key = the text, "s:" + key = when it was saved.
 */
public final class CommentDrafts {

    private static final String PREFS = "comment_drafts";
    static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    static final int MAX_DRAFTS = 100;

    private CommentDrafts() {
    }

    // ---- keys

    @NonNull
    public static String issue(@NonNull String owner, @NonNull String repo, int number) {
        return "issue:" + place(owner, repo, number);
    }

    @NonNull
    public static String pull(@NonNull String owner, @NonNull String repo, int number) {
        return "pull:" + place(owner, repo, number);
    }

    /** @param replyTo the comment answered, null for a new comment of the discussion */
    @NonNull
    public static String discussion(@NonNull String owner, @NonNull String repo, int number,
                                    @Nullable String replyTo) {
        return "discussion:" + place(owner, repo, number) + (replyTo == null ? "" : ":" + replyTo);
    }

    @NonNull
    public static String gist(@NonNull String gistId) {
        return "gist:" + gistId;
    }

    @NonNull
    private static String place(@NonNull String owner, @NonNull String repo, int number) {
        return (owner + "/" + repo).toLowerCase(Locale.ROOT) + "#" + number;
    }

    // ---- store

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The saved text, or null. */
    @Nullable
    public static String get(@NonNull Context context, @Nullable String key) {
        if (key == null) return null;
        SharedPreferences prefs = prefs(context);
        long saved = prefs.getLong("s:" + key, 0);
        if (saved != 0 && expired(saved, System.currentTimeMillis())) {
            remove(context, key);
            return null;
        }
        String text = prefs.getString("t:" + key, null);
        return text == null || text.trim().isEmpty() ? null : text;
    }

    /** Saves the text; an empty text removes the draft. */
    public static void save(@NonNull Context context, @Nullable String key, @Nullable String text) {
        if (key == null) return;
        if (text == null || text.trim().isEmpty()) {
            remove(context, key);
            return;
        }
        SharedPreferences prefs = prefs(context);
        boolean isNew = !prefs.contains("t:" + key);
        prefs.edit().putString("t:" + key, text).putLong("s:" + key, System.currentTimeMillis()).apply();
        if (isNew) prune(prefs);
    }

    public static void remove(@NonNull Context context, @Nullable String key) {
        if (key == null) return;
        prefs(context).edit().remove("t:" + key).remove("s:" + key).apply();
    }

    private static void prune(@NonNull SharedPreferences prefs) {
        Map<String, Long> saved = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("s:") && entry.getValue() instanceof Long) {
                saved.put(entry.getKey().substring(2), (Long) entry.getValue());
            }
        }
        List<String> drop = toDrop(saved, System.currentTimeMillis());
        if (drop.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : drop) editor.remove("t:" + key).remove("s:" + key);
        editor.apply();
    }

    // ---- pure rules (tested)

    static boolean expired(long savedMs, long nowMs) {
        return nowMs - savedMs > MAX_AGE_MS;
    }

    /** The drafts to drop: the expired ones, then the oldest beyond {@link #MAX_DRAFTS}. */
    @NonNull
    static List<String> toDrop(@NonNull Map<String, Long> saved, long nowMs) {
        List<String> drop = new ArrayList<>();
        List<Map.Entry<String, Long>> kept = new ArrayList<>();
        for (Map.Entry<String, Long> entry : saved.entrySet()) {
            if (expired(entry.getValue(), nowMs)) drop.add(entry.getKey());
            else kept.add(entry);
        }
        if (kept.size() > MAX_DRAFTS) {
            kept.sort((a, b) -> Long.compare(a.getValue(), b.getValue()));
            for (int i = 0; i < kept.size() - MAX_DRAFTS; i++) drop.add(kept.get(i).getKey());
        }
        return drop;
    }
}
