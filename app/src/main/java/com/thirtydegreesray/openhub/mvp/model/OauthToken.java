

package com.thirtydegreesray.openhub.mvp.model;

import com.google.gson.annotations.SerializedName;

/**
 * Created on 2017/7/14.
 *
 * @author ThirtyDegreesRay
 */

public class OauthToken {

    @SerializedName("access_token")
    private String accessToken;

    private String scope;

    /** Seconds the access token lives (expiring sign-ins only, otherwise missing). */
    @SerializedName("expires_in")
    private Long expiresIn;

    /** Refresh token (ghr_...) of an expiring sign-in; missing for tokens that do not expire. */
    @SerializedName("refresh_token")
    private String refreshToken;

    /** Seconds the refresh token lives (about 6 months). */
    @SerializedName("refresh_token_expires_in")
    private Long refreshTokenExpiresIn;

    /** GitHub answers errors of this endpoint with 200 and these two fields. */
    private String error;

    @SerializedName("error_description")
    private String errorDescription;

    public OauthToken() {
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
