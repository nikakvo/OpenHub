package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.mvp.model.LicenseTemplate;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.User;

import java.util.ArrayList;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
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

    @NonNull @GET("gitignore/templates")
    Observable<Response<ArrayList<String>>> getGitignoreTemplates(
            @Header("forceNetWork") boolean forceNetWork
    );

    @NonNull @GET("licenses?per_page=100")
    Observable<Response<ArrayList<LicenseTemplate>>> getLicenses(
            @Header("forceNetWork") boolean forceNetWork
    );

    /** 200 when the repository exists (and is visible), 404 when the name is free. */
    @NonNull @GET("repos/{owner}/{repo}")
    Observable<Response<Repository>> getRepo(
            @Header("forceNetWork") boolean forceNetWork,
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
}
