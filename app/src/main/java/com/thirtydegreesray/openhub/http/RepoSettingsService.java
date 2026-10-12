package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.RequestBody;
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
 * Repository settings, branches and branch protection, like the Settings and
 * Branches pages of a repository on github.com. Never cached: these screens show
 * what a change just did. Paths marked encoded=true are encoded by the caller with
 * "/" kept (branch names may contain "/").
 */
public interface RepoSettingsService {

    @NonNull @GET("repos/{owner}/{repo}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getRepo(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    /** name, description, homepage, private, archived, has_*, allow_*, default_branch, ... */
    @NonNull @PATCH("repos/{owner}/{repo}")
    Observable<Response<JsonObject>> updateRepo(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** Body: names (replaces all topics). */
    @NonNull @PUT("repos/{owner}/{repo}/topics")
    Observable<Response<JsonObject>> replaceTopics(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** 200 with "enabled" when known; 404 when immutable releases are off. */
    @NonNull @GET("repos/{owner}/{repo}/immutable-releases")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<ResponseBody>> getImmutableReleases(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    @NonNull @PUT("repos/{owner}/{repo}/immutable-releases")
    Observable<Response<ResponseBody>> enableImmutableReleases(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    @NonNull @DELETE("repos/{owner}/{repo}/immutable-releases")
    Observable<Response<ResponseBody>> disableImmutableReleases(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    /** Body: new_owner, optional new_name. */
    @NonNull @POST("repos/{owner}/{repo}/transfer")
    Observable<Response<JsonObject>> transfer(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    /** Needs the delete_repo scope. */
    @NonNull @DELETE("repos/{owner}/{repo}")
    Observable<Response<ResponseBody>> deleteRepo(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    // ---------------------------------------------------------------------------------
    // Branches
    // ---------------------------------------------------------------------------------

    @NonNull @GET("repos/{owner}/{repo}/branches?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getBranches(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("page") int page
    );

    /** Body: new_name. */
    @NonNull @POST("repos/{owner}/{repo}/branches/{branch}/rename")
    Observable<Response<JsonObject>> renameBranch(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch,
            @Body Map<String, Object> body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/git/refs/heads/{branch}")
    Observable<Response<ResponseBody>> deleteBranch(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );

    /** 404 when the branch is not protected. */
    @NonNull @GET("repos/{owner}/{repo}/branches/{branch}/protection")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getProtection(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );

    /** Raw JSON: the four required keys may be null, which the normal converter drops. */
    @NonNull @PUT("repos/{owner}/{repo}/branches/{branch}/protection")
    Observable<Response<JsonObject>> setProtection(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch,
            @Body RequestBody body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/branches/{branch}/protection")
    Observable<Response<ResponseBody>> removeProtection(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );
}
