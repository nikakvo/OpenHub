package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Issue types and issue fields (both are defined by the organization that owns the
 * repository). REST API 2026-03-10.
 */
public interface IssueFieldService {

    /** The enabled issue types of the repository's owner; 404 or empty for personal repositories. */
    @NonNull @GET("repos/{owner}/{repo}/issue-types")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getIssueTypes(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    /** Edit with a hand-built JSON body: {"type": "Bug"} or {"type": null}. */
    @NonNull @PATCH("repos/{owner}/{repo}/issues/{number}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> editIssue(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body okhttp3.RequestBody body
    );

    /** The field values set on an issue (same objects as the issue's "issue_field_values"). */
    @NonNull @GET("repos/{owner}/{repo}/issues/{number}/issue-field-values?per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getFieldValues(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number
    );

    /** Adds or changes the given values, keeps the others: {"issue_field_values":[{field_id, value}]}. */
    @NonNull @POST("repos/{owner}/{repo}/issues/{number}/issue-field-values")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonElement>> addFieldValues(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Body JsonObject body
    );

    /** Removes one value (404 when the issue has no value for that field). */
    @NonNull @DELETE("repos/{owner}/{repo}/issues/{number}/issue-field-values/{fieldId}")
    Observable<Response<JsonElement>> removeFieldValue(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("number") int number,
            @Path("fieldId") long fieldId
    );
}
