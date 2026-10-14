package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Review threads of a pull request (GraphQL only: REST knows comments, not threads):
 * whether each one is resolved, and resolving / unresolving it.
 * Threads are keyed by the REST id of their first comment, which is what every reply's
 * in_reply_to_id points at.
 */
public final class ReviewThreads {

    public static final class Thread {
        public final String id;
        public final boolean resolved;
        @NonNull public final String resolvedBy;
        public final boolean canResolve;
        public final boolean canUnresolve;

        Thread(@NonNull JsonObject node) {
            id = Json.str(node, "id");
            resolved = Json.bool(node, "isResolved");
            resolvedBy = Json.str(node, "resolvedBy.login");
            canResolve = Json.bool(node, "viewerCanResolve");
            canUnresolve = Json.bool(node, "viewerCanUnresolve");
        }
    }

    private static final String QUERY = "query($owner:String!,$repo:String!,$number:Int!,$after:String){"
            + "repository(owner:$owner,name:$repo){pullRequest(number:$number){"
            + "reviewThreads(first:100,after:$after){pageInfo{hasNextPage endCursor}"
            + "nodes{id isResolved resolvedBy{login} viewerCanResolve viewerCanUnresolve"
            + " comments(first:1){nodes{databaseId}}}}}}}";

    private ReviewThreads() {
    }

    /** Blocking. Root comment id -> thread. Empty when GraphQL is not available. */
    @NonNull
    public static Map<Long, Thread> fetch(@NonNull String owner, @NonNull String repo, int number) {
        Map<Long, Thread> threads = new HashMap<>();
        String after = null;
        try {
            for (int page = 0; page < 10; page++) {
                Map<String, Object> variables = new HashMap<>();
                variables.put("owner", owner);
                variables.put("repo", repo);
                variables.put("number", number);
                variables.put("after", after);
                JsonObject answer = graphql(QUERY, variables);
                JsonObject list = Json.obj(answer, "data.repository.pullRequest.reviewThreads");
                if (list == null) break;
                JsonElement nodes = list.get("nodes");
                if (nodes != null && nodes.isJsonArray()) {
                    for (JsonElement element : nodes.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject node = element.getAsJsonObject();
                        JsonElement first = Json.get(node, "comments.nodes");
                        if (first == null || !first.isJsonArray() || first.getAsJsonArray().size() == 0) continue;
                        JsonElement id = Json.get(first.getAsJsonArray().get(0).getAsJsonObject(), "databaseId");
                        if (id != null) threads.put(id.getAsLong(), new Thread(node));
                    }
                }
                if (!Json.bool(list, "pageInfo.hasNextPage")) break;
                after = Json.str(list, "pageInfo.endCursor");
            }
        } catch (Exception ignored) {
            // without thread data the comments still show, only without Resolve
        }
        return threads;
    }

    /** Blocking. */
    public static void setResolved(@NonNull String threadId, boolean resolved) throws IOException {
        String mutation = resolved
                ? "mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}"
                : "mutation($id:ID!){unresolveReviewThread(input:{threadId:$id}){thread{isResolved}}}";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", threadId);
        graphql(mutation, variables);
    }

    @Nullable
    private static JsonObject graphql(@NonNull String query, @NonNull Map<String, Object> variables)
            throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        JsonObject answer = GitHubApi.call(GitHubApi.service(PullService.class).graphql(body));
        if (answer != null && answer.has("errors") && answer.get("errors").isJsonArray()
                && answer.getAsJsonArray("errors").size() > 0) {
            throw new GitHubApi.ApiException(422,
                    Json.str(answer.getAsJsonArray("errors").get(0).getAsJsonObject(), "message"));
        }
        return answer;
    }

    /** The thread a REST review comment belongs to (its own id or the one it replies to). */
    public static long rootOf(@NonNull JsonObject comment) {
        try {
            JsonElement reply = comment.get("in_reply_to_id");
            if (reply != null && !reply.isJsonNull()) return reply.getAsLong();
            return comment.get("id").getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
