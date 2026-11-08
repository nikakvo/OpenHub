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
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Webhooks of a repository and their deliveries (admins; the repo scope covers them). */
public interface WebhookService {

    @NonNull @GET("repos/{owner}/{repo}/hooks")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getHooks(@Path("owner") String owner, @Path("repo") String repo,
            @Query("per_page") int perPage, @Query("page") int page);

    @NonNull @GET("repos/{owner}/{repo}/hooks/{id}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getHook(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id);

    /** Body: active (and only what changes) */
    @NonNull @PATCH("repos/{owner}/{repo}/hooks/{id}")
    Observable<Response<JsonObject>> updateHook(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id, @Body Map<String, Object> body);

    @NonNull @DELETE("repos/{owner}/{repo}/hooks/{id}")
    Observable<Response<ResponseBody>> deleteHook(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id);

    @NonNull @POST("repos/{owner}/{repo}/hooks/{id}/pings")
    Observable<Response<ResponseBody>> ping(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id);

    /** Newest first. */
    @NonNull @GET("repos/{owner}/{repo}/hooks/{id}/deliveries")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getDeliveries(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id, @Query("per_page") int perPage);

    /** With the request (headers, payload) and the receiver's response. */
    @NonNull @GET("repos/{owner}/{repo}/hooks/{id}/deliveries/{delivery}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getDelivery(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id, @Path("delivery") long delivery);

    /** 202: GitHub sends the delivery again (it shows up as a new delivery with redelivery true). */
    @NonNull @POST("repos/{owner}/{repo}/hooks/{id}/deliveries/{delivery}/attempts")
    Observable<Response<ResponseBody>> redeliver(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id, @Path("delivery") long delivery);
}
