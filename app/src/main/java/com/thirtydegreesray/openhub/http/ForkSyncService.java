package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * "Sync fork": brings a fork branch up to date with its upstream repository.
 */
public interface ForkSyncService {

    /**
     * Body: branch. 200 {message, merge_type: merge | fast-forward | none, base_branch},
     * 409 = merge conflict, 422 = could not sync for another reason (GitHub's message).
     */
    @NonNull @POST("repos/{owner}/{repo}/merge-upstream")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<JsonObject>> mergeUpstream(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Body Map<String, Object> body
    );
}
