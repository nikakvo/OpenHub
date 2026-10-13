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

    /** Issue search; used with per_page=1 for total_count (e.g. "repo:o/r is:pr is:open"). */
    @NonNull @GET("search/issues?per_page=1")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> searchCount(
            @Query("q") String query
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
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getReviews(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Body: event (APPROVE, REQUEST_CHANGES, COMMENT) and body. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/reviews")
    Observable<Response<JsonObject>> createReview(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/comments?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getComments(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Body: body. */
    @NonNull @POST("repos/{owner}/{repo}/issues/{number}/comments")
    Observable<Response<JsonObject>> createComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /** Review comments: comments on lines of the diff (path, line, side, in_reply_to_id). */
    @NonNull @GET("repos/{owner}/{repo}/pulls/{number}/comments?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getReviewComments(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("page") int page
    );

    /** Body: body, commit_id, path, line, side, optional start_line and start_side. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/comments")
    Observable<Response<JsonObject>> createReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body Map<String, Object> body
    );

    /** Body: body. Answers a line comment thread. */
    @NonNull @POST("repos/{owner}/{repo}/pulls/{number}/comments/{id}/replies")
    Observable<Response<JsonObject>> replyReviewComment(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("id") long id,
            @Body Map<String, Object> body
    );

    /** Body: body. */
    @NonNull @PATCH("repos/{owner}/{repo}/pulls/comments/{id}")
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
