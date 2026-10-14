package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.http.RepoAdminService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * Commits file changes to a repository the way the GitHub website does: all changes
 * of one action (an edit, a rename, several uploaded files) become one commit, either
 * on the current branch or on a new branch with an optional pull request.
 *
 * Uses the Git Data API (blobs, tree, commit, ref). File modes are kept, so an
 * executable script stays executable after an edit. A repository without any commit
 * cannot have a tree yet; there each new file is created with the Contents API, which
 * makes the first commit.
 */
public final class RepoCommitter {

    /** GitHub refuses larger files; big binaries belong in a release. */
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;

    private static final String MODE_FILE = "100644";

    private RepoCommitter() {
    }

    /** One file change. */
    public static final class Change {
        final String path;
        @Nullable final String text;
        @Nullable final Uri file;
        @Nullable final String blobSha;
        /** keep the file mode of this path (rename) */
        @Nullable final String modeFrom;
        final boolean delete;

        private Change(String path, String text, Uri file, String blobSha, String modeFrom, boolean delete) {
            this.path = path;
            this.text = text;
            this.file = file;
            this.blobSha = blobSha;
            this.modeFrom = modeFrom;
            this.delete = delete;
        }

        /** Create or replace a text file. */
        public static Change text(@NonNull String path, @NonNull String text) {
            return new Change(path, text, null, null, null, false);
        }

        /** Text file under a new path that keeps the mode of {@code oldPath}. */
        public static Change textMoved(@NonNull String path, @NonNull String text, @NonNull String oldPath) {
            return new Change(path, text, null, null, oldPath, false);
        }

        /** Create or replace a file with the bytes of a local file. */
        public static Change file(@NonNull String path, @NonNull Uri uri) {
            return new Change(path, null, uri, null, null, false);
        }

        /** Existing blob under a new path (a rename without a content change). */
        public static Change moved(@NonNull String path, @NonNull String blobSha, @NonNull String oldPath) {
            return new Change(path, null, null, blobSha, oldPath, false);
        }

        public static Change delete(@NonNull String path) {
            return new Change(path, null, null, null, path, true);
        }
    }

    /** What the commit dialog asked for. */
    public static final class Options {
        public final String message;
        @Nullable public final String description;
        /** null: commit to the current branch */
        @Nullable public final String newBranch;
        public final boolean openPullRequest;
        /** with no new branch: the branch a pull request from the current branch goes into */
        @Nullable public final String pullBase;

        public Options(@NonNull String message, @Nullable String description,
                       @Nullable String newBranch, boolean openPullRequest) {
            this(message, description, newBranch, openPullRequest, null);
        }

        public Options(@NonNull String message, @Nullable String description,
                       @Nullable String newBranch, boolean openPullRequest, @Nullable String pullBase) {
            this.message = message;
            this.description = description;
            this.newBranch = newBranch;
            this.pullBase = newBranch == null ? pullBase : null;
            this.openPullRequest = openPullRequest && (newBranch != null || this.pullBase != null);
        }

        @NonNull
        String fullMessage() {
            return StringUtils.isBlank(description) ? message : message + "\n\n" + description.trim();
        }
    }

    public static final class Result {
        public final String branch;
        @Nullable public final String pullRequestUrl;

        Result(String branch, @Nullable String pullRequestUrl) {
            this.branch = branch;
            this.pullRequestUrl = pullRequestUrl;
        }
    }

    public interface Progress {
        /** Called on the worker thread. */
        void onFile(int done, int total);
    }

    @WorkerThread
    @NonNull
    public static Result commit(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                @NonNull String branch, @NonNull List<Change> changes,
                                @NonNull Options options, @Nullable Progress progress) throws IOException {
        GitDataService git = GitHubApi.service(GitDataService.class);
        String head;
        try {
            head = branchHead(git, owner, repo, branch);
        } catch (GitHubApi.ApiException e) {
            // 409 "Git Repository is empty": the Git Data API cannot commit there yet
            if (e.code == 409) {
                return commitToEmptyRepository(context, owner, repo, branch, changes, options, progress);
            }
            // never fall back to the contents API here: without the branch and the file's
            // sha it would write to the default branch or fail with "sha wasn't supplied"
            if (e.code == 404) {
                throw new GitHubApi.ApiException(404, "GitHub does not show the branch " + branch
                        + " yet. If it was just created, wait a few seconds and try again.");
            }
            throw e;
        }
        JsonObject commit = GitHubApi.call(git.getCommit(owner, repo, head));
        String baseTree = commit.getAsJsonObject("tree").get("sha").getAsString();

        Map<String, Map<String, String>> modes = new HashMap<>();
        JsonArray entries = new JsonArray();
        int done = 0;
        for (Change change : changes) {
            JsonObject entry = new JsonObject();
            entry.addProperty("path", change.path);
            entry.addProperty("type", "blob");
            if (change.delete) {
                entry.addProperty("mode", modeOf(git, owner, repo, head, baseTree, change.path, modes));
                entry.add("sha", JsonNull.INSTANCE);
            } else if (change.blobSha != null) {
                entry.addProperty("mode", modeOf(git, owner, repo, head, baseTree, change.modeFrom, modes));
                entry.addProperty("sha", change.blobSha);
            } else if (change.text != null) {
                String from = change.modeFrom != null ? change.modeFrom : change.path;
                entry.addProperty("mode", modeOf(git, owner, repo, head, baseTree, from, modes));
                entry.addProperty("content", change.text);
            } else if (change.file != null) {
                Map<String, Object> blob = new HashMap<>();
                blob.put("content", Base64.encodeToString(read(context, change.file), Base64.NO_WRAP));
                blob.put("encoding", "base64");
                JsonObject created = GitHubApi.call(git.createBlob(owner, repo, blob));
                entry.addProperty("mode", modeOf(git, owner, repo, head, baseTree, change.path, modes));
                entry.addProperty("sha", created.get("sha").getAsString());
            }
            entries.add(entry);
            done++;
            if (progress != null) progress.onFile(done, changes.size());
        }

        JsonObject treeBody = new JsonObject();
        treeBody.addProperty("base_tree", baseTree);
        treeBody.add("tree", entries);
        String json = new GsonBuilder().serializeNulls().create().toJson(treeBody);
        JsonObject tree = GitHubApi.call(git.createTree(owner, repo,
                RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"))));

        Map<String, Object> commitBody = new HashMap<>();
        commitBody.put("message", options.fullMessage());
        commitBody.put("tree", tree.get("sha").getAsString());
        List<String> parents = new ArrayList<>();
        parents.add(head);
        commitBody.put("parents", parents);
        String newCommit = GitHubApi.call(git.createCommit(owner, repo, commitBody)).get("sha").getAsString();

        String target = targetBranch(branch, options);
        if (options.newBranch != null) {
            Map<String, Object> ref = new HashMap<>();
            ref.put("ref", "refs/heads/" + options.newBranch);
            ref.put("sha", newCommit);
            GitHubApi.call(git.createRef(owner, repo, ref));
        } else {
            Map<String, Object> ref = new HashMap<>();
            ref.put("sha", newCommit);
            ref.put("force", false);
            try {
                GitHubApi.call(git.updateBranchRef(owner, repo, encode(branch), ref));
            } catch (GitHubApi.ApiException e) {
                if (e.code == 422) throw new GitHubApi.ApiException(422, "The branch " + branch
                        + " changed meanwhile. Reload and try again. (" + e.getMessage() + ")");
                throw e;
            }
        }

        String pullUrl = null;
        String[] headAndBase = pullHeadAndBase(branch, options);
        if (headAndBase != null) {
            String pullHead = headAndBase[0];
            String pullBase = headAndBase[1];
            Map<String, Object> pull = new HashMap<>();
            pull.put("title", options.message);
            if (!StringUtils.isBlank(options.description)) pull.put("body", options.description.trim());
            pull.put("head", pullHead);
            pull.put("base", pullBase);
            try {
                JsonObject created = GitHubApi.call(git.createPull(owner, repo, pull));
                JsonElement url = created.get("html_url");
                pullUrl = url == null || url.isJsonNull() ? null : url.getAsString();
            } catch (GitHubApi.ApiException e) {
                // the branch already has an open pull request: the commit is in it, open that one
                if (e.code != 422) throw e;
                JsonArray open = GitHubApi.call(GitHubApi.service(PullService.class)
                        .findPulls(owner, repo, owner + ":" + pullHead, pullBase, "open"));
                if (open == null || open.size() == 0) throw e;
                JsonElement url = open.get(0).getAsJsonObject().get("html_url");
                pullUrl = url == null || url.isJsonNull() ? null : url.getAsString();
            }
            PullChanges.markChanged();
        }
        return new Result(target, pullUrl);
    }

    /**
     * The commit a branch points to. GitHub answers 404 / 409 for a few seconds after a
     * branch or repository was created (its replicas are not in sync yet), so ask again.
     */
    @NonNull
    private static String branchHead(@NonNull GitDataService git, @NonNull String owner,
                                     @NonNull String repo, @NonNull String branch) throws IOException {
        GitHubApi.ApiException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(1500L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                JsonObject ref = GitHubApi.call(git.getBranchRef(owner, repo, encode(branch)));
                return ref.getAsJsonObject("object").get("sha").getAsString();
            } catch (GitHubApi.ApiException e) {
                if (e.code != 404 && e.code != 409) throw e;
                last = e;
            }
        }
        throw last != null ? last : new IOException("Interrupted");
    }

    /** First commits of a repository without history: one Contents API call per file. */
    @NonNull
    private static Result commitToEmptyRepository(@NonNull Context context, @NonNull String owner,
                                                  @NonNull String repo, @NonNull String branch,
                                                  @NonNull List<Change> changes, @NonNull Options options,
                                                  @Nullable Progress progress) throws IOException {
        if (options.newBranch != null) {
            throw new GitHubApi.ApiException(409,
                    "This repository is empty. Commit the first files directly, then create branches.");
        }
        RepoAdminService service = GitHubApi.service(RepoAdminService.class);
        int done = 0;
        for (Change change : changes) {
            if (change.delete || change.blobSha != null) continue;
            byte[] bytes = change.text != null ? change.text.getBytes(StandardCharsets.UTF_8)
                    : read(context, change.file);
            Map<String, Object> body = new HashMap<>();
            body.put("message", options.fullMessage());
            body.put("content", Base64.encodeToString(bytes, Base64.NO_WRAP));
            GitHubApi.call(service.putFile(owner, repo, encode(change.path), body));
            done++;
            if (progress != null) progress.onFile(done, changes.size());
        }
        return new Result(branch, null);
    }

    /** Mode of an existing path at the head commit, "100644" for a new file. */
    @NonNull
    private static String modeOf(@NonNull GitDataService git, @NonNull String owner, @NonNull String repo,
                                 @NonNull String head, @NonNull String baseTree, @Nullable String path,
                                 @NonNull Map<String, Map<String, String>> cache) {
        if (path == null) return MODE_FILE;
        String[] dirAndName = splitPath(path);
        String dir = dirAndName[0];
        String name = dirAndName[1];
        Map<String, String> entries = cache.get(dir);
        if (entries == null) {
            entries = new HashMap<>();
            try {
                String treeish = dir.isEmpty() ? baseTree : encode(head + ":" + dir);
                JsonObject tree = GitHubApi.call(git.getTree(owner, repo, treeish));
                for (JsonElement element : tree.getAsJsonArray("tree")) {
                    JsonObject item = element.getAsJsonObject();
                    entries.put(item.get("path").getAsString(), item.get("mode").getAsString());
                }
            } catch (Exception ignored) {
                // a new folder: nothing to keep
            }
            cache.put(dir, entries);
        }
        return keptMode(entries.get(name));
    }

    // ---- decisions (plain Java, unit tested) ----------------------------------------

    /** The branch that gets the commit: the new branch, or the current one. */
    @NonNull
    static String targetBranch(@NonNull String branch, @NonNull Options options) {
        return options.newBranch != null ? options.newBranch : branch;
    }

    /**
     * {head, base} of the pull request the commit opens, or null for none. From a new
     * branch into the current one; from the current branch into options.pullBase.
     */
    @Nullable
    static String[] pullHeadAndBase(@NonNull String branch, @NonNull Options options) {
        if (!options.openPullRequest) return null;
        return options.newBranch != null
                ? new String[]{options.newBranch, branch}
                : new String[]{branch, options.pullBase};
    }

    /**
     * The mode a changed file keeps: an executable stays executable (100755); a new path,
     * a symlink or a submodule entry becomes a normal file (100644).
     */
    @NonNull
    static String keptMode(@Nullable String mode) {
        return mode == null || !mode.startsWith("100") ? MODE_FILE : mode;
    }

    /**
     * A folder typed by the user as a repository path: no leading, trailing or double
     * slashes, "" for the top folder; null when it contains "." or ".." parts.
     */
    @Nullable
    public static String cleanFolder(@Nullable String folder) {
        if (folder == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : folder.trim().replace('\\', '/').split("/")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.equals(".") || p.equals("..")) return null;
            if (out.length() > 0) out.append('/');
            out.append(p);
        }
        return out.toString();
    }

    /** {folder, name} of a path; the folder is "" at the top. */
    @NonNull
    static String[] splitPath(@NonNull String path) {
        int slash = path.lastIndexOf('/');
        return new String[]{slash < 0 ? "" : path.substring(0, slash),
                slash < 0 ? path : path.substring(slash + 1)};
    }

    @NonNull
    private static byte[] read(@NonNull Context context, @Nullable Uri uri) throws IOException {
        if (uri == null) throw new IOException("No file");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot read " + uri.getLastPathSegment());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_SIZE) {
                    throw new IOException("A file is larger than "
                            + StringUtils.getSizeString(MAX_FILE_SIZE) + ". Put big files in a release.");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (SecurityException e) {
            throw new IOException("Cannot read the file. Pick it again.");
        }
    }

    /** Path segment encoding that keeps "/" (branch names and file paths). */
    @NonNull
    public static String encode(@NonNull String path) {
        return Uri.encode(path, "/");
    }
}
