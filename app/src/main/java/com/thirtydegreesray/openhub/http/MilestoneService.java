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
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Milestones of a repository. */
public interface MilestoneService {

    /** @param state "open" or "closed"; sorted by due date */
    @NonNull @GET("repos/{owner}/{repo}/milestones?sort=due_on&direction=asc")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getMilestones(@Path("owner") String owner,
            @Path("repo") String repo, @Query("state") String state,
            @Query("per_page") int perPage, @Query("page") int page);

    /** Body: title, description, due_on (or null), state */
    @NonNull @POST("repos/{owner}/{repo}/milestones")
    Observable<Response<JsonObject>> createMilestone(@Path("owner") String owner,
            @Path("repo") String repo, @Body RequestBody body);

    @NonNull @PATCH("repos/{owner}/{repo}/milestones/{number}")
    Observable<Response<JsonObject>> updateMilestone(@Path("owner") String owner,
            @Path("repo") String repo, @Path("number") int number, @Body RequestBody body);

    @NonNull @DELETE("repos/{owner}/{repo}/milestones/{number}")
    Observable<Response<ResponseBody>> deleteMilestone(@Path("owner") String owner,
            @Path("repo") String repo, @Path("number") int number);
}
