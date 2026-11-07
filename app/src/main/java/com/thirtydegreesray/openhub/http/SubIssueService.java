package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Sub-issues (REST API 2026-03-10). Sub-issues are added and removed by the issue
 * "id" (a 64-bit number), not by the issue number.
 */
public interface SubIssueService {

    /** The sub-issues of an issue in GitHub's order (at most 100 per parent). */
    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/sub_issues")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getSubIssues(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("per_page") int perPage,
            @Query("page") int page
    );

    /** The parent issue; 404 when the issue is not a sub-issue. */
    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/parent")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getParent(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** One issue as raw JSON (its id, state and parent_issue_url). */
    @NonNull @GET("repos/{owner}/{repo}/issues/{number}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getIssue(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Open issues of a repository (pull requests are in the list too: they have "pull_request"). */
    @NonNull @GET("repos/{owner}/{repo}/issues?state=open&per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getOpenIssues(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    /** Body: sub_issue_id, optional replace_parent. Answers 201 with the parent issue. */
    @NonNull @POST("repos/{owner}/{repo}/issues/{number}/sub_issues")
    Observable<Response<JsonObject>> addSubIssue(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body JsonObject body
    );

    /** Body: sub_issue_id. Note the singular "sub_issue" in the path. */
    @NonNull @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/issues/{number}/sub_issue", hasBody = true)
    Observable<Response<JsonObject>> removeSubIssue(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body JsonObject body
    );

    /** Body: sub_issue_id and after_id or before_id. */
    @NonNull @PATCH("repos/{owner}/{repo}/issues/{number}/sub_issues/priority")
    Observable<Response<JsonObject>> reprioritize(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body JsonObject body
    );

    // ------------------------------------------------ dependencies and "relates to"

    /** Issues that block this one. */
    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/dependencies/blocked_by")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getBlockedBy(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("per_page") int perPage,
            @Query("page") int page
    );

    /** Issues this one blocks. There is no "add blocking": add "blocked by" on the other issue. */
    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/dependencies/blocking")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getBlocking(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("per_page") int perPage,
            @Query("page") int page
    );

    /** Body: issue_id of the blocking issue. */
    @NonNull @POST("repos/{owner}/{repo}/issues/{number}/dependencies/blocked_by")
    Observable<Response<JsonObject>> addBlockedBy(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body JsonObject body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/issues/{number}/dependencies/blocked_by/{issueId}")
    Observable<Response<JsonObject>> removeBlockedBy(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("issueId") long issueId
    );

    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/relates_to")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getRelatesTo(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Query("per_page") int perPage,
            @Query("page") int page
    );

    /** Body: issue_id of the related issue. */
    @NonNull @POST("repos/{owner}/{repo}/issues/{number}/relates_to")
    Observable<Response<JsonObject>> addRelatesTo(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body JsonObject body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/issues/{number}/relates_to/{issueId}")
    Observable<Response<JsonObject>> removeRelatesTo(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("issueId") long issueId
    );
}
