package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import java.util.Map;

import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.PUT;
import retrofit2.http.Query;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

/**
 * One workflow run: its jobs and steps, logs, re-run and cancel. Never cached: runs
 * change all the time.
 */
public interface ActionsService {

    @NonNull @GET("repos/{owner}/{repo}/actions/runs/{runId}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getRun(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("runId") long runId
    );

    /** Jobs of the latest attempt, with their steps. */
    @NonNull @GET("repos/{owner}/{repo}/actions/runs/{runId}/jobs?filter=latest&per_page=100")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getJobs(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("runId") long runId
    );

    @NonNull @GET("repos/{owner}/{repo}/actions/jobs/{jobId}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getJob(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("jobId") long jobId
    );

    /**
     * Plain text log of a job (GitHub redirects to a storage address; the token is not sent
     * there). A plain Call, not Rx: Rx cancels the call after the first item, which closes
     * the socket while the body is still being read ("Socket is closed").
     */
    @NonNull @Streaming @GET("repos/{owner}/{repo}/actions/jobs/{jobId}/logs")
    @Headers("Cache-Control: no-cache, no-store")
    retrofit2.Call<ResponseBody> getJobLog(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("jobId") long jobId
    );

    /** Body: {"enable_debug_logging": true|false} (also for the other re-runs). */
    @NonNull @POST("repos/{owner}/{repo}/actions/runs/{runId}/rerun")
    Observable<Response<ResponseBody>> rerun(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("runId") long runId,
            @Body Map<String, Object> body
    );

    @NonNull @POST("repos/{owner}/{repo}/actions/runs/{runId}/rerun-failed-jobs")
    Observable<Response<ResponseBody>> rerunFailed(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("runId") long runId,
            @Body Map<String, Object> body
    );

    @NonNull @POST("repos/{owner}/{repo}/actions/jobs/{jobId}/rerun")
    Observable<Response<ResponseBody>> rerunJob(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("jobId") long jobId,
            @Body Map<String, Object> body
    );

    /** The artifact's zip (GitHub redirects to a storage address). A plain Call, see getJobLog. */
    @NonNull @Streaming @GET("repos/{owner}/{repo}/actions/artifacts/{artifactId}/zip")
    @Headers("Cache-Control: no-cache, no-store")
    retrofit2.Call<ResponseBody> getArtifactZip(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("artifactId") long artifactId
    );

    @NonNull @POST("repos/{owner}/{repo}/actions/runs/{runId}/cancel")
    Observable<Response<ResponseBody>> cancel(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("runId") long runId
    );

    /** For a run that does not stop with a normal cancel (e.g. stuck "always()" steps). */
    @NonNull @POST("repos/{owner}/{repo}/actions/runs/{runId}/force-cancel")
    Observable<Response<ResponseBody>> forceCancel(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("runId") long runId
    );

    // ---- workflows (Workflows screen) ----

    /** {"total_count", "workflows": [{id, name, path, state, html_url, ...}]} */
    @NonNull @GET("repos/{owner}/{repo}/actions/workflows")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> listWorkflows(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("per_page") int perPage,
            @Query("page") int page
    );

    @NonNull @GET("repos/{owner}/{repo}/actions/workflows/{workflowId}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getWorkflow(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("workflowId") long workflowId
    );

    /** GitHub answers 204. */
    @NonNull @PUT("repos/{owner}/{repo}/actions/workflows/{workflowId}/enable")
    Observable<Response<ResponseBody>> enableWorkflow(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("workflowId") long workflowId
    );

    /** GitHub answers 204; the state becomes disabled_manually. */
    @NonNull @PUT("repos/{owner}/{repo}/actions/workflows/{workflowId}/disable")
    Observable<Response<ResponseBody>> disableWorkflow(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("workflowId") long workflowId
    );

    // ---- caches ----

    /**
     * {"total_count", "actions_caches": [{id, ref, key, version, last_accessed_at,
     * created_at, size_in_bytes}]}. sort: last_accessed_at, created_at, size_in_bytes.
     */
    @NonNull @GET("repos/{owner}/{repo}/actions/caches")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getCaches(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Query("sort") String sort,
            @Query("direction") String direction,
            @Query("per_page") int perPage,
            @Query("page") int page
    );

    /**
     * {"full_name", "active_caches_size_in_bytes", "active_caches_count"}; GitHub refreshes
     * these numbers about every 5 minutes.
     */
    @NonNull @GET("repos/{owner}/{repo}/actions/cache/usage")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> getCacheUsage(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    /** GitHub answers 204. */
    @NonNull @DELETE("repos/{owner}/{repo}/actions/caches/{cacheId}")
    Observable<Response<ResponseBody>> deleteCache(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path("cacheId") long cacheId
    );
}
