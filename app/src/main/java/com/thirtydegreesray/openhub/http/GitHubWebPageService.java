package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.WikiFeedModel;

import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/12/25 15:30:56
 */

public interface GitHubWebPageService {

    @NonNull @GET("{owner}/{repo}/wiki.atom")
    Observable<Response<WikiFeedModel>> getWiki(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    @NonNull @GET("trending/{language}")
    Observable<Response<ResponseBody>> getTrendingRepos(
            @Header("forceNetWork") boolean forceNetWork,
            @Path(value = "language", encoded = true) String language,
            @Query("since") String since
    );

    @Headers("Cache-Control: public, max-age=86400")
    @NonNull @GET("trending")
    Observable<Response<ResponseBody>> getTrendingLanguages();

}
