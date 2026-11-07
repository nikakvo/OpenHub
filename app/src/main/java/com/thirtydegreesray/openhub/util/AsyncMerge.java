package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.io.IOException;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Merges a pull request with GitHub's asynchronous merge API (PUT .../merge-async) and
 * waits for the result. It is the API GitHub recommends for every merge and the only one
 * that merges stacked pull requests (with every open pull request below them). Branch
 * rules run when the merge runs, so their failure only shows in the result.
 * Runs on a background thread.
 */
public final class AsyncMerge {

    public enum Outcome { MERGED, ENQUEUED, PENDING }

    /** What came out of the merge. */
    public static final class Result {
        @NonNull public final Outcome outcome;
        /** A merge request that was already pending was followed instead of a new one. */
        public final boolean earlierRequest;

        Result(@NonNull Outcome outcome, boolean earlierRequest) {
            this.outcome = outcome;
            this.earlierRequest = earlierRequest;
        }
    }

    /** Polls every 2 s for up to 2 minutes, then reports PENDING (GitHub keeps going). */
    private static final long POLL_MS = 2000;
    private static final long WAIT_MS = 120_000;

    private AsyncMerge() {
    }

    /**
     * @throws GitHubApi.ApiException with GitHub's message when the merge is refused or fails
     */
    @NonNull
    public static Result run(@NonNull String owner, @NonNull String repo, int number,
                             @NonNull Map<String, Object> body) throws IOException {
        PullService pulls = GitHubApi.service(PullService.class);
        Response<JsonObject> response;
        try {
            response = pulls.mergeAsync(owner, repo, number, body).blockingFirst();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw e;
        }
        int code = response.code();
        JsonObject answer;
        if (response.isSuccessful()) {
            answer = response.body();
        } else {
            String text = null;
            try (ResponseBody error = response.errorBody()) {
                if (error != null) text = error.string();
            } catch (Exception ignored) {
            }
            answer = parse(text);
            // 400 (not mergeable) and 409 (already requested) answer with a merge result
            if (answer == null || !answer.has("status") || (code != 400 && code != 409)) {
                String message = GitHubApi.errorMessage(text);
                throw new GitHubApi.ApiException(code, "HTTP " + code
                        + (StringUtils.isBlank(message) ? "" : ": " + message));
            }
        }
        boolean earlier = code == 409;
        long until = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            String status = Json.str(answer, "status");
            switch (status) {
                case "merged":
                    return new Result(Outcome.MERGED, earlier);
                case "enqueued":
                    return new Result(Outcome.ENQUEUED, earlier);
                case "failed":
                    throw new GitHubApi.ApiException(code == 200 ? 422 : code, message(answer));
                case "pending":
                    break;
                default:
                    throw new GitHubApi.ApiException(code, message(answer));
            }
            String uuid = Json.str(answer, "details.uuid");
            if (uuid.isEmpty() || System.currentTimeMillis() >= until) {
                return new Result(Outcome.PENDING, earlier);
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result(Outcome.PENDING, earlier);
            }
            JsonObject next = GitHubApi.call(pulls.getMergeAsync(owner, repo, number, uuid));
            if (next != null) answer = next;
            code = 200;
        }
    }

    @NonNull
    private static String message(@Nullable JsonObject answer) {
        String text = Json.str(answer, "details.message");
        return text.isEmpty() ? Json.str(answer, "status") : text;
    }

    @Nullable
    private static JsonObject parse(@Nullable String text) {
        if (StringUtils.isBlank(text)) return null;
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
