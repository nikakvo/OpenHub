package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
 * Gists: read, create, edit, delete, star and fork. Bodies are JSON built by hand
 * ({@code "file": null} deletes a file, and Gson would drop the null).
 */
public interface GistService {

    @NonNull @GET("gists/{id}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getGist(@Path("id") String id);

    /** Body: description, public, files {name: {content}}. */
    @NonNull @POST("gists")
    Observable<Response<JsonObject>> createGist(@Body RequestBody body);

    /** Body: description and/or files {name: {filename?, content?} | null}. */
    @NonNull @PATCH("gists/{id}")
    Observable<Response<JsonObject>> updateGist(@Path("id") String id, @Body RequestBody body);

    @NonNull @DELETE("gists/{id}")
    Observable<Response<ResponseBody>> deleteGist(@Path("id") String id);

    /** 204 starred, 404 not starred. */
    @NonNull @GET("gists/{id}/star")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<ResponseBody>> isStarred(@Path("id") String id);

    @NonNull @PUT("gists/{id}/star")
    Observable<Response<ResponseBody>> star(@Path("id") String id);

    @NonNull @DELETE("gists/{id}/star")
    Observable<Response<ResponseBody>> unstar(@Path("id") String id);

    // ---- comments ----------------------------------------------------------------

    /** Comments with body (Markdown) and body_html, oldest first. */
    @NonNull @GET("gists/{id}/comments")
    @Headers({"Cache-Control: no-cache, no-store", "Accept: application/vnd.github.full+json"})
    Observable<Response<JsonArray>> getComments(@Path("id") String id,
            @Query("per_page") int perPage, @Query("page") int page);

    /** Body: {"body": "..."} */
    @NonNull @POST("gists/{id}/comments")
    Observable<Response<JsonObject>> createComment(@Path("id") String id, @Body RequestBody body);

    @NonNull @PATCH("gists/{id}/comments/{comment}")
    Observable<Response<JsonObject>> updateComment(@Path("id") String id,
            @Path("comment") long comment, @Body RequestBody body);

    @NonNull @DELETE("gists/{id}/comments/{comment}")
    Observable<Response<ResponseBody>> deleteComment(@Path("id") String id,
            @Path("comment") long comment);

    @NonNull @POST("gists/{id}/forks")
    Observable<Response<JsonObject>> fork(@Path("id") String id);
}
