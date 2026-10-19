package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

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
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * Secrets and variables of a repository: GitHub Actions, Dependabot and environments use
 * the same calls under different addresses (util/ActionsScopes builds them).
 */
public interface ActionsSettingsService {

    /** A page of a list: {"total_count":n,"secrets":[...]} / "variables" / "environments" */
    @NonNull @GET
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> list(@Url String url, @Query("per_page") int perPage,
                                          @Query("page") int page);

    @NonNull @GET
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> get(@Url String url);

    @NonNull @PUT
    Observable<Response<ResponseBody>> put(@Url String url, @Body RequestBody body);

    @NonNull @POST
    Observable<Response<ResponseBody>> post(@Url String url, @Body RequestBody body);

    @NonNull @PATCH
    Observable<Response<ResponseBody>> patch(@Url String url, @Body RequestBody body);

    @NonNull @DELETE
    Observable<Response<ResponseBody>> delete(@Url String url);
}
