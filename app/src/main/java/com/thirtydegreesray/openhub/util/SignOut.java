package com.thirtydegreesray.openhub.util;

import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.http.core.AppRetrofit;

import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * What happens on the phone and at GitHub when an account signs out:
 * no HTTP client keeps its token, the cached GitHub pages (private ones too) are deleted,
 * and a token from the browser sign-in is revoked at GitHub, so it stops working
 * everywhere. A personal access token cannot be revoked by an app: the user deletes it
 * on github.com (Settings, Developer settings, Personal access tokens).
 */
public final class SignOut {

    private SignOut() {
    }

    /** @param token the token of the account that signs out (may be null) */
    public static void cleanUp(@Nullable String token) {
        AppRetrofit.INSTANCE.clearClients();
        new Thread(() -> {
            AppRetrofit.INSTANCE.clearCache();
            revoke(token);
        }, "openhub-sign-out").start();
    }

    /** Only tokens of OpenHub's own OAuth app (gho_...) can be revoked with its client secret. */
    static boolean isRevocable(@Nullable String token) {
        return token != null && token.startsWith("gho_")
                && !StringUtils.isBlank(AppConfig.OPENHUB_CLIENT_ID)
                && !StringUtils.isBlank(AppConfig.OPENHUB_CLIENT_SECRET);
    }

    private static void revoke(@Nullable String token) {
        if (!isRevocable(token)) return;
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            String json = "{\"access_token\":\"" + token.replace("\"", "") + "\"}";
            Request request = new Request.Builder()
                    .url(AppConfig.GITHUB_API_BASE_URL + "applications/" + AppConfig.OPENHUB_CLIENT_ID + "/token")
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", Credentials.basic(AppConfig.OPENHUB_CLIENT_ID,
                            AppConfig.OPENHUB_CLIENT_SECRET))
                    .delete(RequestBody.create(json, MediaType.get("application/json")))
                    .build();
            try (Response ignored = client.newCall(request).execute()) {
                // 204 when revoked, 404 when it was already gone; nothing else to do
            }
        } catch (Exception ignored) {
            // offline: the token stays valid until the user revokes it on github.com
        }
    }
}
