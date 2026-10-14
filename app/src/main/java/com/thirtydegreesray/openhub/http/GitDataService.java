package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Low-level Git objects (refs, commits, trees, blobs) and pull requests: what
 * {@link com.thirtydegreesray.openhub.util.RepoCommitter} needs to put several file
 * changes into one commit, like the GitHub website does. Never cached: every answer
 * describes a state that a commit may just have changed.
 *
 * Paths marked encoded=true must be encoded by the caller with "/" kept.
 */
public interface GitDataService {

    @NonNull @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getBranchRef(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );

    @NonNull @GET("repos/{owner}/{repo}/git/commits/{sha}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getCommit(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("sha") String sha
    );

    /** {@code treeish}: a tree sha or "commitSha:dir/path". */
    @NonNull @GET("repos/{owner}/{repo}/git/trees/{treeish}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getTree(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "treeish", encoded = true) String treeish
    );

    /** Body: content (Base64) and encoding "base64". */
    @NonNull @POST("repos/{owner}/{repo}/git/blobs")
    Observable<Response<JsonObject>> createBlob(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** Raw JSON body: deleting an entry needs "sha": null, which the normal converter drops. */
    @NonNull @POST("repos/{owner}/{repo}/git/trees")
    Observable<Response<JsonObject>> createTree(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body RequestBody body
    );

    /** Body: message, tree, parents. */
    @NonNull @POST("repos/{owner}/{repo}/git/commits")
    Observable<Response<JsonObject>> createCommit(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** Body: ref ("refs/heads/name") and sha. */
    @NonNull @POST("repos/{owner}/{repo}/git/refs")
    Observable<Response<JsonObject>> createRef(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** Body: sha, force. */
    @NonNull @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    Observable<Response<JsonObject>> updateBranchRef(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch,
            @Body Map<String, Object> body
    );

    /** File with Base64 content and blob sha; content is empty for files over 1 MB. */
    @NonNull @GET("repos/{owner}/{repo}/contents/{path}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getContent(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "path", encoded = true) String path,
            @Query("ref") String ref
    );

    /** The README of a branch: path, sha, content. */
    @NonNull @GET("repos/{owner}/{repo}/readme")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getReadme(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("ref") String ref
    );

    /** Body: title, body, head, base. */
    @NonNull @POST("repos/{owner}/{repo}/pulls")
    Observable<Response<JsonObject>> createPull(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );
}
