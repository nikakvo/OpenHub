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
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Deploy keys of a repository (admins). Keys cannot be changed: delete and add again. */
public interface DeployKeyService {

    @NonNull @GET("repos/{owner}/{repo}/keys")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getKeys(@Path("owner") String owner, @Path("repo") String repo,
            @Query("per_page") int perPage, @Query("page") int page);

    @NonNull @GET("repos/{owner}/{repo}/keys/{id}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getKey(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id);

    /** Body: title, key ("type base64"), read_only */
    @NonNull @POST("repos/{owner}/{repo}/keys")
    Observable<Response<JsonObject>> createKey(@Path("owner") String owner, @Path("repo") String repo,
            @Body Map<String, Object> body);

    @NonNull @DELETE("repos/{owner}/{repo}/keys/{id}")
    Observable<Response<ResponseBody>> deleteKey(@Path("owner") String owner, @Path("repo") String repo,
            @Path("id") long id);
}
