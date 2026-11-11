package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Pull requests: list, details, comments, reviews, commits, files, merge, and the
 * GraphQL call for "Ready for review" / "Convert to draft" (no REST endpoint exists).
 * Never cached: a pull request changes while you look at it.
 */
public interface PullService {

    /** state: open, closed or all; newest first. */
    @NonNull @GET("repos/{owner}/{repo}/pulls?per_page=30")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getPulls(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("state") String state,
            @Query("page") int page
    );

    /** Pull requests from one branch ("owner:branch") into base. */
    @NonNull @GET("repos/{owner}/{repo}/pulls")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> findPulls(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("head") String head,
            @Query("base") String base,
            @Query("state") String state
    );

    /** Issue search; used with per_page=1 for total_count (e.g. "repo:o/r is:pr is:open"). */
    @NonNull @GET("search/issues?per_page=1")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> searchCount(
            @Query("q") String query
    );

    /** Issue search, newest first (e.g. "repo:o/r is:pr is:archived"); items are issues with a pull_request field. */
    @NonNull @GET("search/issues?per_page=50&sort=created&order=desc")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> searchIssues(
            @Query("q") String query,
            @Query("page") int page
    );

    /** Includes body_html, mergeable and mergeable_state. */
    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}")
    @Headers({"Accept: application/vnd.github.full+json", "Cache-Control: no-cache, no-store"})
    Observable<Response<JsonObject>> getPull(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Body: title, body, state ("open" / "closed"), base. */
    @NonNull @PATCH("repos/{owner}/{repo}/pulls/{number}")
    Observable<Response<JsonObject>> updatePull(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /** Body: title, head, base, body, draft. */
    @NonNull @POST("repos/{owner}/{repo}/pulls")
    Observable<Response<JsonObject>> createPull(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** Body: merge_method, commit_title, commit_message, sha (head the merge was checked against). */
    @NonNull @PUT("repos/{owner}/{repo}/pulls/{number}/merge")
    Observable<Response<JsonObject>> merge(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /**
     * The asynchronous merge, the only one that can merge stacked pull requests. Body:
     * merge_method, merge_action (default / direct_merge / merge_queue), sha, bypass_rules,
     * commit_title, commit_message. 202 = pending with a uuid, 200 = already merged or
     * queued, 409 = another request is pending (its uuid), 400 = not mergeable; all of them
     * answer {status, details}.
     */
    @NonNull @PUT("repos/{owner}/{repo}/pulls/{number}/merge-async")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> mergeAsync(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /** Result of an asynchronous merge: status pending / merged / enqueued / failed. */
    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}/merge-async/{uuid}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getMergeAsync(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("uuid") String uuid
    );

    /** A stack of pull requests: base.ref, open, pull_requests (number, title, state, draft, merged_at, head, base). */
    @NonNull @GET("repos/{owner}/{repo}/stacks/{stack}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getStack(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("stack") int stack
    );

    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}/commits?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getCommits(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("page") int page
    );

    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}/files?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getFiles(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("page") int page
    );

    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}/reviews?per_page=100")
    // body_html: GitHub's rendering of the text
    @Headers({"Accept: application/vnd.github.full+json", "Cache-Control: no-cache, no-store"})
    Observable<Response<JsonArray>> getReviews(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Body: event (APPROVE, REQUEST_CHANGES, COMMENT) and body. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/reviews")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> createReview(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/comments?per_page=100")
    // body_html: GitHub's rendering of the text
    @Headers({"Accept: application/vnd.github.full+json", "Cache-Control: no-cache, no-store"})
    Observable<Response<JsonArray>> getComments(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Body: body. */
    @NonNull @POST("repos/{owner}/{repo}/issues/{number}/comments")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> createComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /** Review comments: comments on lines of the diff (path, line, side, in_reply_to_id). */
    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}/comments?per_page=100")
    // body_html: GitHub's rendering of the text
    @Headers({"Accept: application/vnd.github.full+json", "Cache-Control: no-cache, no-store"})
    Observable<Response<JsonArray>> getReviewComments(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("page") int page
    );

    /** Body: body, commit_id, path, line, side, optional start_line and start_side. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/comments")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> createReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /** Submits a pending review. Body: event (APPROVE, REQUEST_CHANGES, COMMENT) and body. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/reviews/{id}/events")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> submitReview(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("id") long id,
            @Body Map<String, Object> body
    );

    /** Deletes a review that has not been submitted yet. */
    @NonNull @DELETE("repos/{owner}/{repo}/pulls/{number}/reviews/{id}")
    Observable<Response<JsonObject>> deletePendingReview(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("id") long id
    );

    /** Body: body. Answers a line comment thread. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/comments/{id}/replies")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> replyReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("id") long id,
            @Body Map<String, Object> body
    );

    /** One review comment as GitHub has it now (body, line, diff_hunk). */
    @NonNull @GET("repos/{owner}/{repo}/pulls/comments/{id}")
    // body_html: GitHub's rendering of the text
    @Headers({"Accept: application/vnd.github.full+json", "Cache-Control: no-cache, no-store"})
    Observable<Response<JsonObject>> getReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id
    );

    /** Body: body. */
    @NonNull @PATCH("repos/{owner}/{repo}/pulls/comments/{id}")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> updateReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id,
            @Body Map<String, Object> body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/pulls/comments/{id}")
    Observable<Response<ResponseBody>> deleteReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id
    );

    /** Body: body. */
    @NonNull @PATCH("repos/{owner}/{repo}/issues/comments/{id}")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<JsonObject>> updateComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id,
            @Body Map<String, Object> body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/issues/comments/{id}")
    Observable<Response<ResponseBody>> deleteComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id
    );

    /** Combined commit status (older CI integrations): state success / pending / failure. */
    @NonNull @GET("repos/{owner}/{repo}/commits/{sha}/status")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getCombinedStatus(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("sha") String sha
    );

    /** Check runs (GitHub Actions and apps) of a commit. */
    @NonNull @GET("repos/{owner}/{repo}/commits/{sha}/check-runs?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getCheckRuns(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("sha") String sha
    );

    /** Body: query, variables. */
    @NonNull @POST("graphql")
    Observable<Response<JsonObject>> graphql(@Body Map<String, Object> body);

    @NonNull @GET("repos/{owner}/{repo}/branches/{branch}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<ResponseBody>> getBranch(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );

    /** {@code basehead}: "base...head" or "base...owner:head", encoded by the caller. */
    @NonNull @GET("repos/{owner}/{repo}/compare/{basehead}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> compare(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "basehead", encoded = true) String basehead
    );
}
