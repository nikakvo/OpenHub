package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends a review. Without pending replies it is one POST /reviews (line comments, event and
 * text together). With replies to existing threads it works like github.com's "Start a
 * review": the review is created as pending with its line comments, the replies are added
 * to it through GraphQL (addPullRequestReviewThreadReply; the REST review has no field for
 * replies), then it is submitted. If anything fails after the pending review exists it is
 * deleted again, so nothing half-sent stays on GitHub and the drafts stay on the phone.
 */
public final class ReviewSender {

    private static final String REPLY = "mutation($review:ID!,$thread:ID!,$body:String!){"
            + "addPullRequestReviewThreadReply(input:{pullRequestReviewId:$review,"
            + "pullRequestReviewThreadId:$thread,body:$body}){comment{id}}}";

    private ReviewSender() {
    }

    /** @param data what ReviewDialog built: event, body, commit_id, comments */
    @WorkerThread
    public static void send(@NonNull String owner, @NonNull String repo, int number,
                            @NonNull Map<String, Object> data, @NonNull List<JsonObject> drafts) throws Exception {
        PullService pulls = GitHubApi.service(PullService.class);
        List<JsonObject> replies = new ArrayList<>();
        for (JsonObject draft : drafts) if (ReviewDrafts.isReply(draft)) replies.add(draft);
        if (replies.isEmpty()) {
            GitHubApi.call(pulls.createReview(owner, repo, number, data));
            return;
        }

        Map<String, Object> pending = new HashMap<>(data);
        // without an event the review stays pending
        pending.remove("event");
        pending.remove("body");
        Object comments = pending.get("comments");
        if (comments instanceof List && ((List<?>) comments).isEmpty()) pending.remove("comments");
        JsonObject review = GitHubApi.call(pulls.createReview(owner, repo, number, pending));
        long reviewId = review.get("id").getAsLong();
        String reviewNode = Json.str(review, "node_id");
        try {
            for (JsonObject reply : replies) {
                GraphQl.run(REPLY, GraphQl.vars("review", reviewNode, "thread", Json.str(reply, "thread"),
                        "body", Json.str(reply, "body")));
            }
            Map<String, Object> submit = new HashMap<>();
            submit.put("event", data.get("event"));
            if (data.containsKey("body")) submit.put("body", data.get("body"));
            GitHubApi.call(pulls.submitReview(owner, repo, number, reviewId, submit));
        } catch (Exception e) {
            try {
                GitHubApi.call(pulls.deletePendingReview(owner, repo, number, reviewId));
            } catch (Exception ignored) {
            }
            throw e;
        }
    }
}
