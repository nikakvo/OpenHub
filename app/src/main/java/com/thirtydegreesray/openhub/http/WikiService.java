package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Url;

/**
 * Wiki pages as their Markdown files from raw.githubusercontent.com/wiki/owner/repo/
 * (GitHub's API has no wiki endpoints). The token is never sent there.
 */
public interface WikiService {

    @NonNull @GET
    Observable<Response<ResponseBody>> getRaw(
            @Header("forceNetWork") boolean forceNetWork,
            @Url String url
    );
}
