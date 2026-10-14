package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A pending review: line comments collected on the phone and sent together as one review
 * ("Start a review" on github.com). Kept per pull request until it is sent or discarded,
 * so it survives closing the app.
 *
 * Stored as JSON "owner/repo#number" -> [{"id": local id, "path", "line", "side",
 * "start_line", "start_side", "body", "created": millis}].
 */
public final class ReviewDrafts {

    private static final String PREFS = "review_drafts";

    private ReviewDrafts() {
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String key(@NonNull String owner, @NonNull String repo, int number) {
        return owner + "/" + repo + "#" + number;
    }

    @NonNull
    private static JsonArray load(@NonNull Context context, @NonNull String key) {
        try {
            JsonElement element = JsonParser.parseString(prefs(context).getString(key, "[]"));
            if (element.isJsonArray()) return element.getAsJsonArray();
        } catch (Exception ignored) {
        }
        return new JsonArray();
    }

    private static void save(@NonNull Context context, @NonNull String key, @NonNull JsonArray drafts) {
        if (drafts.size() == 0) prefs(context).edit().remove(key).apply();
        else prefs(context).edit().putString(key, drafts.toString()).apply();
    }

    /** Copies of the pending comments, oldest first. */
    @NonNull
    public static synchronized List<JsonObject> list(@NonNull Context context, @NonNull String owner,
                                                     @NonNull String repo, int number) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement element : load(context, key(owner, repo, number))) {
            if (element.isJsonObject()) list.add(element.getAsJsonObject().deepCopy());
        }
        return list;
    }

    public static int count(@NonNull Context context, @NonNull String owner, @NonNull String repo, int number) {
        return load(context, key(owner, repo, number)).size();
    }

    /** @param startLine 0 for a comment on one line */
    public static synchronized void add(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                        int number, @NonNull String path, int line, @NonNull String side,
                                        int startLine, @Nullable String startSide, @NonNull String body) {
        String key = key(owner, repo, number);
        JsonArray drafts = load(context, key);
        JsonObject draft = new JsonObject();
        long now = System.currentTimeMillis();
        draft.addProperty("id", now);
        draft.addProperty("path", path);
        draft.addProperty("line", line);
        draft.addProperty("side", side);
        if (startLine > 0) {
            draft.addProperty("start_line", startLine);
            draft.addProperty("start_side", startSide == null ? side : startSide);
        }
        draft.addProperty("body", body);
        draft.addProperty("created", now);
        drafts.add(draft);
        save(context, key, drafts);
    }

    public static synchronized void update(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                           int number, long id, @NonNull String body) {
        String key = key(owner, repo, number);
        JsonArray drafts = load(context, key);
        for (JsonElement element : drafts) {
            JsonObject draft = element.getAsJsonObject();
            if (draft.get("id").getAsLong() == id) draft.addProperty("body", body);
        }
        save(context, key, drafts);
    }

    public static synchronized void remove(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                           int number, long id) {
        String key = key(owner, repo, number);
        JsonArray drafts = load(context, key);
        JsonArray kept = new JsonArray();
        for (JsonElement element : drafts) {
            if (element.getAsJsonObject().get("id").getAsLong() != id) kept.add(element);
        }
        save(context, key, kept);
    }

    public static synchronized void clear(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                          int number) {
        prefs(context).edit().remove(key(owner, repo, number)).apply();
    }

    /** The "comments" array of POST /pulls/{number}/reviews. */
    @NonNull
    public static List<Map<String, Object>> toReviewComments(@NonNull List<JsonObject> drafts) {
        List<Map<String, Object>> comments = new ArrayList<>();
        for (JsonObject draft : drafts) {
            Map<String, Object> comment = new HashMap<>();
            comment.put("path", Json.str(draft, "path"));
            comment.put("line", Json.integer(draft, "line"));
            comment.put("side", Json.str(draft, "side"));
            if (Json.integer(draft, "start_line") > 0) {
                comment.put("start_line", Json.integer(draft, "start_line"));
                comment.put("start_side", Json.str(draft, "start_side"));
            }
            comment.put("body", Json.str(draft, "body"));
            comments.add(comment);
        }
        return comments;
    }
}
