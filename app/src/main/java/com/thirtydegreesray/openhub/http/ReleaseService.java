package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.GeneratedNotes;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.ReleaseAsset;

import java.util.ArrayList;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Writing side of GitHub releases: create, edit, publish and delete releases and
 * manage their assets. Uploading asset files goes to uploads.github.com and is done
 * by {@link com.thirtydegreesray.openhub.service.UploadService}.
 *
 * Calls that read a release for editing pass forceNetWork=true, so an edit never
 * starts from a cached copy.
 */
public interface ReleaseService {

    /** Raw Markdown body (for editing) plus rendered HTML (for display). Drafts included. */
    @NonNull @GET("repos/{owner}/{repo}/releases/{id}")
    @Headers("Accept: application/vnd.github.full+json")
    Observable<Response<Release>> getRelease(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") String id
    );

    /** Newest first; drafts included for users with push access. */
    @NonNull @GET("repos/{owner}/{repo}/releases?per_page=50")
    Observable<Response<ArrayList<Release>>> getReleases(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    @NonNull @GET("repos/{owner}/{repo}/releases/latest")
    Observable<Response<Release>> getLatestRelease(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    @NonNull @POST("repos/{owner}/{repo}/releases")
    Observable<Response<Release>> createRelease(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    @NonNull @PATCH("repos/{owner}/{repo}/releases/{id}")
    Observable<Response<Release>> updateRelease(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") String id,
            @Body Map<String, Object> body
    );

    /** GitHub answers 204. */
    @NonNull @DELETE("repos/{owner}/{repo}/releases/{id}")
    Observable<Response<ResponseBody>> deleteRelease(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") String id
    );

    /** Body: tag_name, optional target_commitish and previous_tag_name. */
    @NonNull @POST("repos/{owner}/{repo}/releases/generate-notes")
    Observable<Response<GeneratedNotes>> generateNotes(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );

    @NonNull @GET("repos/{owner}/{repo}/releases/{id}/assets?per_page=100")
    Observable<Response<ArrayList<ReleaseAsset>>> getAssets(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") String id
    );

    /** Body: name and/or label. */
    @NonNull @PATCH("repos/{owner}/{repo}/releases/assets/{assetId}")
    Observable<Response<ReleaseAsset>> updateAsset(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("assetId") String assetId,
            @Body Map<String, Object> body
    );

    /** GitHub answers 204. */
    @NonNull @DELETE("repos/{owner}/{repo}/releases/assets/{assetId}")
    Observable<Response<ResponseBody>> deleteAsset(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("assetId") String assetId
    );

    /**
     * Deletes a tag. {@code tag} must be path-encoded by the caller with "/" kept,
     * because a tag such as "release/1.0" is the ref "tags/release/1.0".
     */
    @NonNull @DELETE("repos/{owner}/{repo}/git/refs/tags/{tag}")
    Observable<Response<ResponseBody>> deleteTag(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "tag", encoded = true) String tag
    );

    @NonNull @GET("repos/{owner}/{repo}/tags?per_page=100")
    Observable<Response<ArrayList<Branch>>> getTags(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("page") int page
    );

    @NonNull @GET("repos/{owner}/{repo}/branches?per_page=100")
    Observable<Response<ArrayList<Branch>>> getBranches(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("page") int page
    );
}
