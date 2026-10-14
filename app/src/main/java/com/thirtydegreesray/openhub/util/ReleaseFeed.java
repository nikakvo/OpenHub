package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Newest releases of the starred repositories, read with GraphQL (50 repositories per
 * request instead of one REST request per repository).
 *
 * The result is kept in a file, so the screen shows the last list at once and refreshes
 * it in the background. Format:
 * {"updated": millis, "repos": [{"repo": "owner/name", "owner": login, "avatar": url,
 *   "stable": release or absent, "pre": release or absent}]}
 * A release: {"tag", "name", "published", "url", "pre": bool,
 *   "assets": [{"name", "size", "count", "url"}]}.
 * "pre" is only kept when it is newer than "stable".
 */
public final class ReleaseFeed {

    private static final String FILE = "release_feed.json";
    private static final String PREFS = "release_feed";
    private static final String KEY_SEEN = "seen_at";
    private static final String KEY_PRE = "show_prereleases";
    private static final int PAGE_SIZE = 50;
    /** At most 1000 starred repositories. */
    private static final int MAX_PAGES = 20;

    private static final String RELEASE_FIELDS = "tagName name publishedAt isPrerelease isDraft url"
            + " releaseAssets(first:30){nodes{name size downloadCount downloadUrl}}";
    private static final String QUERY = "query($after:String){viewer{starredRepositories(first:"
            + PAGE_SIZE + ",after:$after,orderBy:{field:STARRED_AT,direction:DESC}){"
            + "pageInfo{hasNextPage endCursor}"
            + "nodes{nameWithOwner owner{login avatarUrl(size:80)}"
            + " latestRelease{" + RELEASE_FIELDS + "}"
            + " releases(first:3,orderBy:{field:CREATED_AT,direction:DESC}){nodes{" + RELEASE_FIELDS + "}}"
            + "}}}}";

    private ReleaseFeed() {
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Releases published after this time are marked as new. 0 before the first visit. */
    public static long lastSeen(@NonNull Context context) {
        return prefs(context).getLong(KEY_SEEN, 0);
    }

    public static void markSeen(@NonNull Context context, long time) {
        prefs(context).edit().putLong(KEY_SEEN, time).apply();
    }

    public static boolean showPreReleases(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_PRE, true);
    }

    public static void setShowPreReleases(@NonNull Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_PRE, show).apply();
    }

    @Nullable
    public static synchronized JsonObject loadCache(@NonNull Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            JsonElement element = JsonParser.parseString(new String(data, 0, read, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void saveCache(@NonNull Context context, @NonNull JsonObject feed) {
        File file = new File(context.getFilesDir(), FILE);
        File tmp = new File(context.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(feed.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(file);
    }

    /** Blocking: reads all pages, saves and returns the feed. */
    @NonNull
    public static JsonObject fetch(@NonNull Context context) throws IOException {
        PullService service = GitHubApi.service(PullService.class);
        JsonArray repos = new JsonArray();
        String after = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("after", after);
            Map<String, Object> body = new HashMap<>();
            body.put("query", QUERY);
            body.put("variables", variables);
            JsonObject answer = GitHubApi.call(service.graphql(body));
            if (answer == null) break;
            if (answer.has("errors") && answer.get("errors").isJsonArray()
                    && answer.getAsJsonArray("errors").size() > 0
                    && Json.obj(answer, "data.viewer") == null) {
                throw new GitHubApi.ApiException(422,
                        Json.str(answer.getAsJsonArray("errors").get(0).getAsJsonObject(), "message"));
            }
            JsonObject starred = Json.obj(answer, "data.viewer.starredRepositories");
            if (starred == null) break;
            JsonElement nodes = starred.get("nodes");
            if (nodes != null && nodes.isJsonArray()) {
                for (JsonElement node : nodes.getAsJsonArray()) {
                    if (!node.isJsonObject()) continue;
                    JsonObject item = repoItem(node.getAsJsonObject());
                    if (item != null) repos.add(item);
                }
            }
            if (!Json.bool(starred, "pageInfo.hasNextPage")) break;
            after = Json.str(starred, "pageInfo.endCursor");
            if (after.isEmpty()) break;
        }
        JsonObject feed = new JsonObject();
        feed.addProperty("updated", System.currentTimeMillis());
        feed.add("repos", repos);
        saveCache(context, feed);
        return feed;
    }

    @Nullable
    private static JsonObject repoItem(@NonNull JsonObject node) {
        JsonObject stable = null;
        JsonObject latest = Json.obj(node, "latestRelease");
        if (latest != null && !Json.bool(latest, "isDraft") && !Json.bool(latest, "isPrerelease")) {
            stable = release(latest);
        }
        JsonObject pre = null;
        JsonElement recent = Json.get(node, "releases.nodes");
        if (recent != null && recent.isJsonArray()) {
            for (JsonElement element : recent.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                if (Json.bool(candidate, "isDraft") || Json.str(candidate, "publishedAt").isEmpty()) continue;
                if (Json.bool(candidate, "isPrerelease")) {
                    if (pre == null) pre = release(candidate);
                } else if (stable == null) {
                    stable = release(candidate);
                }
            }
        }
        if (pre != null && stable != null && published(pre) <= published(stable)) pre = null;
        if (stable == null && pre == null) return null;

        JsonObject item = new JsonObject();
        item.addProperty("repo", Json.str(node, "nameWithOwner"));
        item.addProperty("owner", Json.str(node, "owner.login"));
        item.addProperty("avatar", Json.str(node, "owner.avatarUrl"));
        if (stable != null) item.add("stable", stable);
        if (pre != null) item.add("pre", pre);
        return item;
    }

    @NonNull
    private static JsonObject release(@NonNull JsonObject node) {
        JsonObject release = new JsonObject();
        release.addProperty("tag", Json.str(node, "tagName"));
        release.addProperty("name", Json.str(node, "name"));
        release.addProperty("published", Json.str(node, "publishedAt"));
        release.addProperty("url", Json.str(node, "url"));
        release.addProperty("pre", Json.bool(node, "isPrerelease"));
        JsonArray assets = new JsonArray();
        JsonElement nodes = Json.get(node, "releaseAssets.nodes");
        if (nodes != null && nodes.isJsonArray()) {
            for (JsonElement element : nodes.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject asset = element.getAsJsonObject();
                JsonObject out = new JsonObject();
                out.addProperty("name", Json.str(asset, "name"));
                JsonElement size = Json.get(asset, "size");
                out.addProperty("size", size == null ? 0 : size.getAsLong());
                out.addProperty("count", Json.integer(asset, "downloadCount"));
                out.addProperty("url", Json.str(asset, "downloadUrl"));
                assets.add(out);
            }
        }
        release.add("assets", assets);
        return release;
    }

    /** Publication time in millis, 0 when unknown. */
    public static long published(@Nullable JsonObject release) {
        java.util.Date date = Json.date(release, "published");
        return date == null ? 0 : date.getTime();
    }

    /** The release to show for a repository item: the pre-release only when wanted. */
    @Nullable
    public static JsonObject shown(@NonNull JsonObject item, boolean withPreReleases) {
        JsonObject pre = Json.obj(item, "pre");
        if (withPreReleases && pre != null) return pre;
        return Json.obj(item, "stable");
    }
}
