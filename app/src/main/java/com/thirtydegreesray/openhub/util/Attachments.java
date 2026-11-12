package com.thirtydegreesray.openhub.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Pictures and videos attached to issues, comments and release notes, as on github.com.
 * GitHub has no documented API for them; the GitHub CLI (2.99, September 2026) uses
 * {@code POST https://uploads.github.com/user-attachments/assets?repository_id&name&content_type}
 * with the file as the body, and GitHub answers {@code {"url": ".../user-attachments/assets/<uuid>"}}.
 * It needs write access to the repository (GitHub answers 404 without it). The rules here
 * are the ones the CLI checks before it uploads (file types and sizes of the web upload).
 * Pure Java, tested in AttachmentsTest.
 */
public final class Attachments {

    public static final String UPLOAD_BASE = "https://uploads.github.com/user-attachments/assets";
    /** Pictures and GIFs: 10 MB. */
    public static final long IMAGE_LIMIT = 10L * 1024 * 1024;
    /** Videos: 10 MB on Free plans, 100 MB on paid plans; GitHub decides between the two. */
    public static final long VIDEO_LIMIT = 100L * 1024 * 1024;

    /**
     * GitHub's link of a file uploaded from this phone -> the file on the phone (content://
     * URI). A new upload is not public until a comment that uses it is sent, so GitHub
     * answers 404 for it in the preview; the preview (and the comment just sent) shows the
     * same file from the phone instead. Kept in a small file ({@link #STORE_NAME}) so it
     * survives when Android stops the app while the editor is in the background: at most
     * {@link #MAX_LOCAL_COPIES} links, each for {@link #LOCAL_COPY_KEEP_MS}.
     */
    private static final java.util.Map<String, String> LOCAL_COPIES =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** GitHub's link -> the content type of a file uploaded from this phone. */
    private static final java.util.Map<String, String> LOCAL_TYPES =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** GitHub's link -> when it was uploaded (ms since 1970). */
    private static final java.util.Map<String, Long> LOCAL_TIMES =
            new java.util.concurrent.ConcurrentHashMap<>();
    public static final String STORE_NAME = "attachments.json";
    public static final int MAX_LOCAL_COPIES = 200;
    public static final long LOCAL_COPY_KEEP_MS = 24L * 60 * 60 * 1000;
    private static volatile java.io.File store;
    private static final java.util.concurrent.Executor SAVER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "attachments-store");
                t.setDaemon(true);
                return t;
            });
    private static final java.util.regex.Pattern ASSET_LINK = java.util.regex.Pattern.compile(
            "https://github\\.com/user-attachments/assets/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private Attachments() {
    }

    public static void rememberLocalCopy(String url, String localUri, String contentType) {
        if (!putLocalCopy(url, localUri, contentType, System.currentTimeMillis())) return;
        final java.io.File file = store;
        if (file != null) SAVER.execute(() -> saveLocalCopies(file, System.currentTimeMillis()));
    }

    static synchronized boolean putLocalCopy(String url, String localUri, String contentType,
                                             long time) {
        if (url == null || localUri == null) return false;
        LOCAL_COPIES.put(url, localUri);
        if (contentType != null) LOCAL_TYPES.put(url, contentType);
        else LOCAL_TYPES.remove(url);
        LOCAL_TIMES.put(url, time);
        trimLocalCopies(time);
        return true;
    }

    /** Drops links older than a day, then the oldest ones over the limit. */
    static synchronized void trimLocalCopies(long now) {
        for (String url : new java.util.ArrayList<>(LOCAL_COPIES.keySet())) {
            Long time = LOCAL_TIMES.get(url);
            if (time == null || time > now + LOCAL_COPY_KEEP_MS || now - time > LOCAL_COPY_KEEP_MS) {
                forgetLocalCopy(url);
            }
        }
        if (LOCAL_COPIES.size() <= MAX_LOCAL_COPIES) return;
        java.util.List<String> oldestFirst = new java.util.ArrayList<>(LOCAL_COPIES.keySet());
        java.util.Collections.sort(oldestFirst, (a, b) -> Long.compare(timeOf(a), timeOf(b)));
        for (int i = 0; i < oldestFirst.size() - MAX_LOCAL_COPIES; i++) {
            forgetLocalCopy(oldestFirst.get(i));
        }
    }

    private static long timeOf(String url) {
        Long time = LOCAL_TIMES.get(url);
        return time == null ? Long.MIN_VALUE : time;
    }

    private static void forgetLocalCopy(String url) {
        LOCAL_COPIES.remove(url);
        LOCAL_TYPES.remove(url);
        LOCAL_TIMES.remove(url);
    }

    /** Forgets every link (tests). */
    static synchronized void clearLocalCopies() {
        LOCAL_COPIES.clear();
        LOCAL_TYPES.clear();
        LOCAL_TIMES.clear();
    }

    /**
     * Reads the links kept in {@code dir/attachments.json} (app start) and saves there from
     * now on. Links older than a day are dropped; a broken file is ignored.
     */
    public static void loadLocalCopies(java.io.File dir) {
        java.io.File file = new java.io.File(dir, STORE_NAME);
        long now = System.currentTimeMillis();
        int before = LOCAL_COPIES.size();
        readLocalCopies(file, now);
        store = file;
        if (file.exists() && LOCAL_COPIES.size() != before) {
            SAVER.execute(() -> saveLocalCopies(file, System.currentTimeMillis()));
        }
    }

    static synchronized void readLocalCopies(java.io.File file, long now) {
        if (!file.isFile()) return;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), now);
        } catch (Exception ignored) {
            // unreadable file: start without the old links
        }
    }

    static synchronized void saveLocalCopies(java.io.File file, long now) {
        trimLocalCopies(now);
        java.io.File tmp = new java.io.File(file.getPath() + ".tmp");
        try {
            java.nio.file.Files.write(tmp.toPath(),
                    toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** {@code [{"url":..,"local":..,"type":..,"time":..}, ...]} */
    static synchronized String toJson() {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (java.util.Map.Entry<String, String> entry : LOCAL_COPIES.entrySet()) {
            com.google.gson.JsonObject item = new com.google.gson.JsonObject();
            item.addProperty("url", entry.getKey());
            item.addProperty("local", entry.getValue());
            String type = LOCAL_TYPES.get(entry.getKey());
            if (type != null) item.addProperty("type", type);
            item.addProperty("time", timeOf(entry.getKey()));
            array.add(item);
        }
        return array.toString();
    }

    /** Adds the links of {@link #toJson()} text; drops old, broken and extra entries. */
    static synchronized void fromJson(String json, long now) {
        com.google.gson.JsonElement root;
        try {
            root = com.google.gson.JsonParser.parseString(json == null ? "" : json);
        } catch (RuntimeException e) {
            return;
        }
        if (root == null || !root.isJsonArray()) return;
        for (com.google.gson.JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            com.google.gson.JsonObject item = element.getAsJsonObject();
            String url = string(item, "url");
            String local = string(item, "local");
            if (url == null || local == null || LOCAL_COPIES.containsKey(url)) continue;
            long time;
            try {
                time = item.has("time") ? item.get("time").getAsLong() : -1;
            } catch (RuntimeException e) {
                continue;
            }
            if (time < 0) continue;
            LOCAL_COPIES.put(url, local);
            String type = string(item, "type");
            if (type != null) LOCAL_TYPES.put(url, type);
            LOCAL_TIMES.put(url, time);
        }
        trimLocalCopies(now);
    }

    private static String string(com.google.gson.JsonObject item, String key) {
        com.google.gson.JsonElement value = item.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        String text = value.getAsString();
        return text.isEmpty() ? null : text;
    }

    /**
     * True for a link that should open the video player: GitHub's link of an uploaded file
     * ({@code https://github.com/user-attachments/assets/<uuid>}). github.com writes pictures
     * as images and videos as such bare links, so a link of this kind is a video, unless it
     * is a file uploaded here whose type says otherwise.
     */
    public static boolean isVideoLink(String url) {
        if (url == null || !ASSET_LINK.matcher(url.trim()).matches()) return false;
        String type = LOCAL_TYPES.get(url.trim());
        return type == null || isVideo(type);
    }

    /** The links uploaded from this phone (a copy). */
    public static java.util.Set<String> localUrls() {
        return new java.util.HashSet<>(LOCAL_COPIES.keySet());
    }

    /** The content type of a file uploaded from this phone, or null. */
    public static String localType(String url) {
        return url == null ? null : LOCAL_TYPES.get(url);
    }

    /** The file on the phone for an uploaded link, or null. */
    public static String localCopy(String url) {
        return url == null ? null : LOCAL_COPIES.get(url);
    }

    /**
     * The content type GitHub takes for a file, from its name first, then from the type the
     * phone reports; null when GitHub does not take this kind of file.
     */
    public static String contentType(String name, String mimeType) {
        String ext = extension(name);
        switch (ext) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "mp4": return "video/mp4";
            case "mov": return "video/quicktime";
            case "webm": return "video/webm";
            default: break;
        }
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT).trim();
        switch (mime) {
            case "image/png":
            case "image/jpeg":
            case "image/gif":
            case "image/webp":
            case "image/svg+xml":
            case "video/mp4":
            case "video/quicktime":
            case "video/webm":
                return mime;
            case "image/jpg": return "image/jpeg";
            default: return null;
        }
    }

    /** The name sent to GitHub: the phone's name with the extension that matches the type. */
    public static String fileName(String name, String contentType) {
        String base = name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (base.isEmpty()) base = "file";
        String wanted = extensionFor(contentType);
        if (wanted == null) return base;
        String ext = extension(base);
        boolean matches = ext.equals(wanted) || (wanted.equals("jpg") && ext.equals("jpeg"));
        return matches ? base : base + "." + wanted;
    }

    public static boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith("video/");
    }

    /** The largest file GitHub may take for this type (videos: the paid-plan limit). */
    public static long limit(String contentType) {
        return isVideo(contentType) ? VIDEO_LIMIT : IMAGE_LIMIT;
    }

    /** True when the size is known and above what GitHub takes for this type. */
    public static boolean tooBig(String contentType, long size) {
        return size > limit(contentType);
    }

    public static String uploadUrl(long repositoryId, String name, String contentType) {
        return UPLOAD_BASE + "?repository_id=" + repositoryId
                + "&name=" + encode(name) + "&content_type=" + encode(contentType);
    }

    /** Write access = what GitHub needs for the upload (read and triage get a 404). */
    public static boolean canUpload(boolean admin, boolean maintain, boolean push) {
        return admin || maintain || push;
    }

    /**
     * The Markdown for an uploaded file, as github.com writes it: a picture as
     * {@code ![name](url)}; a video as the bare link, which GitHub shows as a player.
     */
    public static String markdown(String name, String url, String contentType) {
        if (isVideo(contentType)) return url;
        String alt = name == null ? "" : name.replaceAll("[\\[\\]\\\\\\r\\n]", " ").trim();
        int dot = alt.lastIndexOf('.');
        if (dot > 0) alt = alt.substring(0, dot);
        if (alt.isEmpty()) alt = "image";
        return "![" + alt + "](" + url + ")";
    }

    /** Text with an inserted block and the cursor after it. */
    public static final class Insertion {
        public final String text;
        public final int cursor;

        Insertion(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }
    }

    /**
     * Puts {@code block} in place of the selection as a paragraph of its own: an empty line
     * before it (unless it starts the text) and after it. One line break does not end a
     * paragraph in Markdown, and GitHub shows a video as a player only when its link stands
     * alone. The cursor goes after the empty line that follows, ready to write on.
     */
    public static Insertion insert(String text, int selectionStart, int selectionEnd, String block) {
        String current = text == null ? "" : text;
        int start = clamp(Math.min(selectionStart, selectionEnd), current.length());
        int end = clamp(Math.max(selectionStart, selectionEnd), current.length());
        if (selectionStart < 0 || selectionEnd < 0) {
            start = current.length();
            end = current.length();
        }
        String before = current.substring(0, start);
        String after = current.substring(end);
        StringBuilder middle = new StringBuilder();
        if (!before.isEmpty()) {
            if (!before.endsWith("\n")) middle.append("\n\n");
            else if (!before.endsWith("\n\n")) middle.append('\n');
        }
        middle.append(block);
        int breaks = after.startsWith("\n\n") ? 2 : after.startsWith("\n") ? 1 : 0;
        for (int i = breaks; i < 2; i++) middle.append('\n');
        int cursor = before.length() + middle.length() + breaks;
        return new Insertion(before + middle + after, cursor);
    }

    /** The message for a refused upload: GitHub's own text where it gives one. */
    public enum Problem { NO_WRITE_ACCESS, NOT_ACCEPTED, RATE_LIMITED, GITHUB_MESSAGE, OTHER }

    public static Problem problem(int code, String gitHubMessage) {
        if (code == 404) return Problem.NO_WRITE_ACCESS;
        if (code == 401 || code == 403) return Problem.NOT_ACCEPTED;
        if (code == 429) return Problem.RATE_LIMITED;
        if (gitHubMessage != null && !gitHubMessage.trim().isEmpty()) return Problem.GITHUB_MESSAGE;
        return Problem.OTHER;
    }

    static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }

    static String extensionFor(String contentType) {
        if (contentType == null) return null;
        switch (contentType) {
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            case "image/svg+xml": return "svg";
            case "video/mp4": return "mp4";
            case "video/quicktime": return "mov";
            case "video/webm": return "webm";
            default: return null;
        }
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
