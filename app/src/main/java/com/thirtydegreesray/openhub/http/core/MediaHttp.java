package com.thirtydegreesray.openhub.http.core;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * The one HTTP client for pictures and videos (Glide, the video player and its file type
 * check): OkHttp with the app's timeouts, HTTP/2 and connection reuse. It never sends the
 * account's token: pictures and attachments come from many hosts (avatars, camo, S3).
 */
public final class MediaHttp {

    private static volatile OkHttpClient client;

    private MediaHttp() {
    }

    @NonNull
    public static OkHttpClient client() {
        OkHttpClient c = client;
        if (c == null) {
            synchronized (MediaHttp.class) {
                c = client;
                if (c == null) {
                    c = new OkHttpClient.Builder()
                            // roaming and VPNs: Glide's own loader gave up after 2.5 s
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                                    .header("User-Agent", "OpenHub").build()))
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }
}
