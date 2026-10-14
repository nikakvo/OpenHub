

package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.SearchResult;
import com.thirtydegreesray.openhub.mvp.model.User;

import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/25 13:52:09
 */

public interface SearchService {

//    https://api.github.com/search/users?q=Ray&sort=followers&order=desc
    @NonNull @GET("search/users")
    Observable<Response<SearchResult<User>>> searchUsers(
            @Query(value = "q", encoded = true) String query,
            @Query("sort") String sort,
            @Query("order") String order,
            @Query("page") int page
    );

    @NonNull @GET("search/repositories")
    Observable<Response<SearchResult<Repository>>> searchRepos(
            @Query(value = "q", encoded = true) String query,
            @Query("sort") String sort,
            @Query("order") String order,
            @Query("page") int page
    );

//    https://api.github.com/search/issues?sort=created&page=1&q=user:ThirtyDegreesRay+state:open&order=desc
    @NonNull @GET("search/issues")
    @Headers("Accept: application/vnd.github.html,application/vnd.github.VERSION.raw")
    Observable<Response<SearchResult<Issue>>> searchIssues(
            @Header("forceNetWork") boolean forceNetWork,
            @Query(value = "q", encoded = true) String query,
            @Query("sort") String sort,
            @Query("order") String order,
            @Query("page") int page
    );

    /** Code search in the default branch; text matches give the found fragments. */
    @NonNull @GET("search/code")
    @Headers({"Accept: application/vnd.github.text-match+json", "Cache-Control: no-cache, no-store"})
    Observable<Response<com.google.gson.JsonObject>> searchCode(
            @Query("q") String query,
            @Query("page") int page,
            @Query("per_page") int perPage
    );

    /** All files of a branch (path search when code search finds nothing). */
    @NonNull @GET("repos/{owner}/{repo}/git/trees/{branch}?recursive=1")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<com.google.gson.JsonObject>> getFileTree(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "branch", encoded = true) String branch
    );

    /** default_branch and fork of a repository. */
    @NonNull @GET("repos/{owner}/{repo}")
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<com.google.gson.JsonObject>> getRepoInfo(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

}
