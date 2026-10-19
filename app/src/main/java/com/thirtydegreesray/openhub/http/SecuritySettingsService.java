package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PUT;
import retrofit2.http.Url;

/**
 * On/off switches of a repository's security features (admins). Most answer 204 / 404
 * or {"enabled": true}; PUT turns one on, DELETE turns it off:
 * vulnerability-alerts, automated-security-fixes, private-vulnerability-reporting.
 */
public interface SecuritySettingsService {

    @NonNull @GET
    @Headers("Cache-Control: no-cache, no-store")
    Observable<Response<ResponseBody>> get(@Url String url);

    @NonNull @PUT
    Observable<Response<ResponseBody>> put(@Url String url);

    @NonNull @DELETE
    Observable<Response<ResponseBody>> delete(@Url String url);
}
