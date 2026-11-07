package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Suggestions collected with "Add to batch" (per pull request, kept while the app runs, like
 * the batch on github.com), and the commit that applies one or more suggestions to the pull
 * request's branch.
 */
public final class SuggestionBatch {

    private static final Map<String, LinkedHashMap<Long, Suggestions.Edit>> BATCHES = new HashMap<>();

    private SuggestionBatch() {
    }

    private static String key(String owner, String repo, int number) {
        return (owner + "/" + repo + "#" + number).toLowerCase(Locale.ROOT);
    }

    @NonNull
    public static synchronized List<Suggestions.Edit> list(String owner, String repo, int number) {
        LinkedHashMap<Long, Suggestions.Edit> map = BATCHES.get(key(owner, repo, number));
        return map == null ? new ArrayList<>() : new ArrayList<>(map.values());
    }

    public static synchronized int count(String owner, String repo, int number) {
        LinkedHashMap<Long, Suggestions.Edit> map = BATCHES.get(key(owner, repo, number));
        return map == null ? 0 : map.size();
    }

    public static synchronized boolean contains(String owner, String repo, int number, long commentId) {
        LinkedHashMap<Long, Suggestions.Edit> map = BATCHES.get(key(owner, repo, number));
        return map != null && map.containsKey(commentId);
    }

    public static synchronized void add(String owner, String repo, int number, @NonNull Suggestions.Edit edit) {
        String k = key(owner, repo, number);
        LinkedHashMap<Long, Suggestions.Edit> map = BATCHES.get(k);
        if (map == null) {
            map = new LinkedHashMap<>();
            BATCHES.put(k, map);
        }
        map.put(edit.commentId, edit);
    }

    public static synchronized void remove(String owner, String repo, int number, long commentId) {
        LinkedHashMap<Long, Suggestions.Edit> map = BATCHES.get(key(owner, repo, number));
        if (map != null) map.remove(commentId);
    }

    public static synchronized void clear(String owner, String repo, int number) {
        BATCHES.remove(key(owner, repo, number));
    }

    /**
     * Applies the suggestions in one commit on the pull request's branch (also a fork's branch
     * when the author allows edits by maintainers). The files are read at the branch's current
     * commit; nothing is written when a commented line changed or the branch moves meanwhile.
     */
    @WorkerThread
    public static void commit(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                              int number, @NonNull List<Suggestions.Edit> edits, @NonNull String message,
                              @Nullable String description, @Nullable String self) throws IOException {
        JsonObject pull = GitHubApi.call(GitHubApi.service(PullService.class).getPull(owner, repo, number));
        if (pull == null) throw new IOException("GitHub did not answer");
        if (!"open".equals(Json.str(pull, "state"))) {
            throw new IOException("Suggestions can only be applied to an open pull request.");
        }
        String headRepo = Json.str(pull, "head.repo.full_name");
        String headRef = Json.str(pull, "head.ref");
        String headSha = Json.str(pull, "head.sha");
        int slash = headRepo.indexOf('/');
        if (slash <= 0 || headRef.isEmpty() || headSha.isEmpty()) {
            throw new IOException("The branch of this pull request no longer exists.");
        }
        String headOwner = headRepo.substring(0, slash);
        String headName = headRepo.substring(slash + 1);

        // the suggestions as GitHub has them now: a comment edited, deleted or outdated meanwhile
        // must not be committed with the text the screen showed before
        PullService pulls = GitHubApi.service(PullService.class);
        for (Suggestions.Edit edit : edits) {
            JsonObject comment;
            try {
                comment = GitHubApi.call(pulls.getReviewComment(owner, repo, edit.commentId));
            } catch (GitHubApi.ApiException e) {
                if (e.code == 404) throw new IOException("A suggestion was deleted on GitHub. Reload the pull request.");
                throw e;
            }
            Suggestions.Edit now = comment == null ? null : Suggestions.fromComment(comment);
            if (now == null) {
                throw new IOException("A suggestion is outdated now. Reload the pull request.");
            }
            if (!Suggestions.sameChange(now, edit)) {
                throw new IOException("A suggestion was changed on GitHub meanwhile. Reload and check it again.");
            }
        }

        List<RepoCommitter.Change> changes = new ArrayList<>();
        for (Map.Entry<String, List<Suggestions.Edit>> file : Suggestions.byFile(edits).entrySet()) {
            String text = readText(headOwner, headName, file.getKey(), headSha);
            String changed;
            try {
                changed = Suggestions.apply(text, file.getValue());
            } catch (IllegalStateException e) {
                throw new IOException(e.getMessage() + ". Reload the pull request; the suggestion may be outdated.");
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
            changes.add(RepoCommitter.Change.text(file.getKey(), changed));
        }
        String fullDescription = Suggestions.description(description, Suggestions.coAuthors(edits, self));
        RepoCommitter.Options options = new RepoCommitter.Options(message, fullDescription, null, false);
        options.expectedHead = headSha;
        RepoCommitter.commit(context, headOwner, headName, headRef, changes, options, null);
        PullChanges.markChanged();
    }

    private static String readText(String owner, String repo, String path, String ref) throws IOException {
        JsonObject file = GitHubApi.call(GitHubApi.service(GitDataService.class)
                .getContent(owner, repo, RepoCommitter.encode(path), ref));
        if (file == null || !"file".equals(Json.str(file, "type"))) {
            throw new IOException(path + " is not a file on the pull request's branch.");
        }
        if (!"base64".equals(Json.str(file, "encoding")) || Json.str(file, "content").isEmpty()) {
            throw new IOException(path + " is too large to change here.");
        }
        byte[] bytes = Base64.decode(Json.str(file, "content"), Base64.DEFAULT);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
