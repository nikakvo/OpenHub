

package com.thirtydegreesray.openhub.http;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.http.model.AuthRequestModel;
import com.thirtydegreesray.openhub.mvp.model.BasicToken;
import com.thirtydegreesray.openhub.mvp.model.OauthToken;

import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created on 2017/8/1.
 *
 * @author ThirtyDegreesRay
 */

public interface LoginService {

    @POST("authorizations")
    @Headers("Accept: application/json")
    Observable<Response<BasicToken>> authorizations(
            @NonNull @Body AuthRequestModel authRequestModel
    );

    /**
     * Code exchange of the browser sign-in. Everything goes in the POST body, never in the
     * URL (URLs end up in logs); code_verifier is the PKCE secret of this sign-in.
     */
    @FormUrlEncoded
    @POST("login/oauth/access_token")
    @Headers("Accept: application/json")
    Observable<Response<OauthToken>> getAccessToken(
            @Field("client_id") String clientId,
            @Field("client_secret") String clientSecret,
            @Field("code") String code,
            @Field("code_verifier") String codeVerifier
    );

}
