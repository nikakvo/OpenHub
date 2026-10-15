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
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * The Security tab of a repository: Dependabot, code scanning and secret scanning
 * alerts, and the repository secrets of GitHub Actions.
 */
public interface SecurityService {

    // ---- Dependabot ------------------------------------------------------------------

    /** @param state "open" or "dismissed,fixed,auto_dismissed" */
    @NonNull @GET("repos/{owner}/{repo}/dependabot/alerts")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getDependabotAlerts(@Path("owner") String owner,
            @Path("repo") String repo, @Query("state") String state, @Query("per_page") int perPage);

    /** Body: state (open | dismissed), dismissed_reason, dismissed_comment. */
    @NonNull @PATCH("repos/{owner}/{repo}/dependabot/alerts/{number}")
    Observable<Response<JsonObject>> updateDependabotAlert(@Path("owner") String owner,
            @Path("repo") String repo, @Path("number") int number, @Body RequestBody body);

    /** Turns on Dependabot alerts (admin). */
    @NonNull @PUT("repos/{owner}/{repo}/vulnerability-alerts")
    Observable<Response<ResponseBody>> enableVulnerabilityAlerts(@Path("owner") String owner,
            @Path("repo") String repo);

    // ---- Code scanning ---------------------------------------------------------------

    /** @param state "open" or "closed" */
    @NonNull @GET("repos/{owner}/{repo}/code-scanning/alerts")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getCodeScanningAlerts(@Path("owner") String owner,
            @Path("repo") String repo, @Query("state") String state, @Query("per_page") int perPage);

    /** Body: state (open | dismissed), dismissed_reason, dismissed_comment. */
    @NonNull @PATCH("repos/{owner}/{repo}/code-scanning/alerts/{number}")
    Observable<Response<JsonObject>> updateCodeScanningAlert(@Path("owner") String owner,
            @Path("repo") String repo, @Path("number") int number, @Body RequestBody body);

    /** Body: state "configured" turns on CodeQL default setup (admin). */
    @NonNull @PATCH("repos/{owner}/{repo}/code-scanning/default-setup")
    Observable<Response<JsonObject>> updateCodeScanningSetup(@Path("owner") String owner,
            @Path("repo") String repo, @Body RequestBody body);

    // ---- Secret scanning -------------------------------------------------------------

    /** @param state "open" or "resolved" */
    @NonNull @GET("repos/{owner}/{repo}/secret-scanning/alerts")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getSecretScanningAlerts(@Path("owner") String owner,
            @Path("repo") String repo, @Query("state") String state, @Query("per_page") int perPage);

    /** Body: state (open | resolved), resolution, resolution_comment. */
    @NonNull @PATCH("repos/{owner}/{repo}/secret-scanning/alerts/{number}")
    Observable<Response<JsonObject>> updateSecretScanningAlert(@Path("owner") String owner,
            @Path("repo") String repo, @Path("number") int number, @Body RequestBody body);

    /** Where a secret was found: [{type: "commit", details: {path, start_line, commit_sha}}, ...]. */
    @NonNull @GET("repos/{owner}/{repo}/secret-scanning/alerts/{number}/locations")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonArray>> getSecretScanningLocations(@Path("owner") String owner,
            @Path("repo") String repo, @Path("number") int number);

    /** Body: security_and_analysis {secret_scanning {status}} (admin). */
    @NonNull @PATCH("repos/{owner}/{repo}")
    Observable<Response<JsonObject>> updateRepo(@Path("owner") String owner,
            @Path("repo") String repo, @Body RequestBody body);

    // ---- Actions secrets -------------------------------------------------------------

    /** {total_count, secrets: [{name, created_at, updated_at}]}; values are never returned. */
    @NonNull @GET("repos/{owner}/{repo}/actions/secrets")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getSecrets(@Path("owner") String owner,
            @Path("repo") String repo, @Query("per_page") int perPage, @Query("page") int page);

    /** {key_id, key}: the public key that secret values are sealed with. */
    @NonNull @GET("repos/{owner}/{repo}/actions/secrets/public-key")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getSecretsPublicKey(@Path("owner") String owner,
            @Path("repo") String repo);

    /** Body: encrypted_value (base64 sealed box), key_id. Creates or replaces. */
    @NonNull @PUT("repos/{owner}/{repo}/actions/secrets/{name}")
    Observable<Response<ResponseBody>> putSecret(@Path("owner") String owner,
            @Path("repo") String repo, @Path("name") String name, @Body RequestBody body);

    @NonNull @DELETE("repos/{owner}/{repo}/actions/secrets/{name}")
    Observable<Response<ResponseBody>> deleteSecret(@Path("owner") String owner,
            @Path("repo") String repo, @Path("name") String name);
}
