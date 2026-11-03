package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Repository rulesets (REST API 2026-03-10), the newer system that replaces classic
 * branch protection: rules for branches and tags, who may bypass them, and their history.
 * {@code includes_parents=true} also lists the rulesets of the organization that apply to
 * the repository (read-only here). Never cached: the screens show what a change just did.
 */
public interface RulesetService {

    @NonNull @GET("repos/{owner}/{repo}/rulesets?includes_parents=true&per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getRulesets(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("page") int page
    );

    @NonNull @GET("repos/{owner}/{repo}/rulesets/{id}?includes_parents=true")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getRuleset(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id
    );

    @NonNull @POST("repos/{owner}/{repo}/rulesets")
    Observable<Response<JsonObject>> createRuleset(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body JsonObject body
    );

    @NonNull @PUT("repos/{owner}/{repo}/rulesets/{id}")
    Observable<Response<JsonObject>> updateRuleset(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id,
            @Body JsonObject body
    );

    @NonNull @DELETE("repos/{owner}/{repo}/rulesets/{id}")
    Observable<Response<ResponseBody>> deleteRuleset(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id
    );

    @NonNull @GET("repos/{owner}/{repo}/rulesets/{id}/history?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getHistory(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id
    );

    @NonNull @GET("repos/{owner}/{repo}/rulesets/{id}/history/{version}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getVersion(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("id") long id,
            @Path("version") long version
    );

    /** Every active rule that applies to a branch, from all rulesets (with their ids). */
    @NonNull @GET("repos/{owner}/{repo}/rules/branches/{branch}?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getBranchRules(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );
}
