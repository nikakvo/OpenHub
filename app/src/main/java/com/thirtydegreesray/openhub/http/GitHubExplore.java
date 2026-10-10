package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.mvp.model.Collection;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.Topic;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Repo collections and featured topics from GitHub's own data instead of scraping
 * github.com HTML (which GitHub redesigns every so often and broke both screens).
 *
 * - Collections: the public github/explore repository, which is the source of
 *   github.com/collections (collections/<id>/index.md with YAML front matter).
 * - Featured topics: the REST search API, q=is:featured.
 *
 * All methods are blocking; call them off the main thread.
 */
public final class GitHubExplore {

    private static final String API = "https://api.github.com/";
    private static final String RAW = "https://raw.githubusercontent.com/github/explore/main/";
    private static final int PARALLEL = 8;

    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern REPO_NAME = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern GITHUB_REPO_URL =
            Pattern.compile("^https?://(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/?$");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /** The collection list rarely changes, keep it for the life of the process. */
    private static volatile ArrayList<Collection> cachedCollections;

    private GitHubExplore() {
    }

    // ---------------------------------------------------------------------------------
    // Collections
    // ---------------------------------------------------------------------------------

    @NonNull
    public static ArrayList<Collection> getCollections(boolean forceReload) throws Exception {
        ArrayList<Collection> cached = cachedCollections;
        if (!forceReload && cached != null) return new ArrayList<>(cached);

        JsonArray dirs = JsonParser.parseString(
                get(API + "repos/github/explore/contents/collections", true)).getAsJsonArray();
        List<String> ids = new ArrayList<>();
        for (JsonElement e : dirs) {
            JsonObject o = e.getAsJsonObject();
            if ("dir".equals(o.get("type").getAsString())) ids.add(o.get("name").getAsString());
        }

        List<Collection> loaded = parallel(ids, id -> {
            FrontMatter fm = FrontMatter.parse(get(RAW + "collections/" + id + "/index.md", false));
            String name = fm.values.containsKey("display_name") ? fm.values.get("display_name") : titleOf(id);
            return new Collection(id, name, fm.summary());
        });

        ArrayList<Collection> result = new ArrayList<>();
        for (Collection c : loaded) if (c != null) result.add(c);
        Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        cachedCollections = result;
        return new ArrayList<>(result);
    }

    /** Repositories listed in a collection, in the collection's own order. */
    @NonNull
    public static ArrayList<Repository> getCollectionRepos(@NonNull String collectionId) throws Exception {
        FrontMatter fm = FrontMatter.parse(get(RAW + "collections/" + collectionId + "/index.md", false));
        List<String> names = new ArrayList<>();
        for (String item : fm.items) {
            String name = repoNameOf(item);
            if (name != null) names.add(name);
        }
        final Gson gson = new Gson();
        List<Repository> repos = parallel(names, name -> gson.fromJson(get(API + "repos/" + name, true), Repository.class));
        ArrayList<Repository> result = new ArrayList<>();
        // Items can also be users, orgs, articles or videos; those simply do not resolve.
        for (Repository r : repos) if (r != null) result.add(r);
        return result;
    }

    // ---------------------------------------------------------------------------------
    // Featured topics
    // ---------------------------------------------------------------------------------

    @NonNull
    public static ArrayList<Topic> getFeaturedTopics() throws Exception {
        Map<String, Topic> topics = new LinkedHashMap<>();
        for (int page = 1; page <= 5; page++) {
            JsonObject json = JsonParser.parseString(get(API
                    + "search/topics?q=is:featured&per_page=100&page=" + page, true)).getAsJsonObject();
            JsonArray items = json.getAsJsonArray("items");
            for (JsonElement e : items) {
                JsonObject o = e.getAsJsonObject();
                String id = string(o, "name");
                if (id == null) continue;
                String name = string(o, "display_name");
                String desc = string(o, "short_description");
                topics.put(id, new Topic()
                        .setId(id)
                        .setName(StringUtils.isBlank(name) ? id : name)
                        .setDesc(desc == null ? "" : desc)
                        .setImage(RAW + "topics/" + id + "/" + id + ".png"));
            }
            int total = json.has("total_count") ? json.get("total_count").getAsInt() : 0;
            if (items.size() < 100 || page * 100 >= total) break;
        }
        ArrayList<Topic> result = new ArrayList<>(topics.values());
        Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private interface Loader<T> {
        T load(String key) throws Exception;
    }

    /** Runs {@code loader} for every key in parallel; failed keys give null, order is kept. */
    @NonNull
    private static <T> List<T> parallel(@NonNull List<String> keys, @NonNull Loader<T> loader)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (String key : keys) futures.add(pool.submit(() -> loader.load(key)));
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                try {
                    results.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    results.add(null);
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @NonNull
    private static String get(@NonNull String url, boolean api) throws IOException {
        Request.Builder rb = new Request.Builder().url(url).header("User-Agent", "OpenHub");
        if (api) {
            rb.header("Accept", "application/vnd.github+json");
            String token = AppData.INSTANCE.getAccessToken();
            if (!StringUtils.isBlank(token)) {
                rb.header("Authorization", token.startsWith("Basic") ? token : "token " + token);
            }
        }
        try (Response response = CLIENT.newCall(rb.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code() + " " + url);
            }
            return body.string();
        }
    }

    @Nullable
    private static String string(@NonNull JsonObject o, @NonNull String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    @Nullable
    private static String repoNameOf(@NonNull String item) {
        String s = item.trim();
        if (REPO_NAME.matcher(s).matches()) return s;
        Matcher m = GITHUB_REPO_URL.matcher(s);
        return m.matches() ? m.group(1) : null;
    }

    @NonNull
    private static String titleOf(@NonNull String id) {
        String s = id.replace('-', ' ');
        return s.isEmpty() ? id : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** Minimal reader for the YAML front matter used in github/explore. */
    private static final class FrontMatter {
        final Map<String, String> values = new LinkedHashMap<>();
        final List<String> items = new ArrayList<>();
        String body = "";

        static FrontMatter parse(@NonNull String text) {
            FrontMatter fm = new FrontMatter();
            String[] lines = text.replace("\r", "").split("\n");
            int i = 0;
            if (lines.length == 0 || !lines[0].trim().equals("---")) {
                fm.body = text;
                return fm;
            }
            String currentList = null;
            for (i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.trim().equals("---")) break;
                String trimmed = line.trim();
                if (trimmed.startsWith("- ") && currentList != null) {
                    if ("items".equals(currentList)) fm.items.add(unquote(trimmed.substring(2)));
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon > 0 && !Character.isWhitespace(line.charAt(0))) {
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    if (value.isEmpty()) {
                        currentList = key;
                    } else {
                        currentList = null;
                        fm.values.put(key, unquote(value));
                    }
                }
            }
            StringBuilder body = new StringBuilder();
            for (i = i + 1; i < lines.length; i++) body.append(lines[i]).append('\n');
            fm.body = body.toString().trim();
            return fm;
        }

        /** First paragraph of the body as plain text. */
        @NonNull
        String summary() {
            String text = body;
            int blank = text.indexOf("\n\n");
            if (blank > 0) text = text.substring(0, blank);
            text = MD_LINK.matcher(text).replaceAll("$1");
            return text.replace('\n', ' ').replace("**", "").replace("`", "").trim();
        }

        @NonNull
        private static String unquote(@NonNull String s) {
            s = s.trim();
            if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                    || (s.startsWith("'") && s.endsWith("'")))) {
                s = s.substring(1, s.length() - 1);
            }
            return s;
        }
    }
}
