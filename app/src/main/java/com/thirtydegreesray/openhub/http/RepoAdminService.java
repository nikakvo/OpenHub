package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.GitignoreTemplate;
import com.thirtydegreesray.openhub.mvp.model.LicenseTemplate;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.User;

import java.util.ArrayList;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Creating repositories and the choices the "New repository" form offers.
 */
public interface RepoAdminService {

    /** Organizations of the logged-in user. */
    @NonNull @GET("user/orgs?per_page=100")
    Observable<Response<ArrayList<User>>> getMyOrgs(
            @Header("forceNetWork") boolean forceNetWork
    );

    /** Repositories you can push to: yours, as collaborator and in your organizations. */
    @NonNull @GET("user/repos?affiliation=owner,collaborator,organization_member&sort=pushed&per_page=100")
    Observable<Response<ArrayList<Repository>>> getMyRepos(
            @Header("forceNetWork") boolean forceNetWork,
            @retrofit2.http.Query("page") int page
    );

    @NonNull @GET("gitignore/templates")
    Observable<Response<ArrayList<String>>> getGitignoreTemplates(
            @Header("forceNetWork") boolean forceNetWork
    );

    @NonNull @GET("gitignore/templates/{name}")
    Observable<Response<GitignoreTemplate>> getGitignoreTemplate(
            @Header("forceNetWork") boolean forceNetWork,
            @Path("name") String name
    );

    @NonNull @GET("licenses?per_page=100")
    Observable<Response<ArrayList<LicenseTemplate>>> getLicenses(
            @Header("forceNetWork") boolean forceNetWork
    );

    /**
     * 200 when the repository exists (and is visible), 404 when the name is free.
     * Never cached: the answer changes the moment the repository is created.
     */
    @NonNull @GET("repos/{owner}/{repo}")
    @retrofit2.http.Headers("Cache-Control: no-cache, no-store")
    Observable<Response<Repository>> getRepo(
            @Path("owner") String owner,
            @Path("repo") String repo
    );

    /** Body: name, description, private, auto_init, gitignore_template, license_template. */
    @NonNull @POST("user/repos")
    Observable<Response<Repository>> createUserRepo(
            @Body Map<String, Object> body
    );

    @NonNull @POST("orgs/{org}/repos")
    Observable<Response<Repository>> createOrgRepo(
            @Path("org") String org,
            @Body Map<String, Object> body
    );

    /**
     * Creates (or, with "sha", replaces) one file with a commit. Body: message, content
     * (Base64), optional branch and sha. Also works in an empty repository, where it
     * makes the first commit.
     */
    @NonNull @PUT("repos/{owner}/{repo}/contents/{path}")
    Observable<Response<ResponseBody>> putFile(
            @Path("owner") String owner,
            @Path("repo") String repo,
            @Path(value = "path", encoded = true) String path,
            @Body Map<String, Object> body
    );
}
