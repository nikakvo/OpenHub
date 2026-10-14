package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import java.util.Map;

import retrofit2.http.Body;
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
}
