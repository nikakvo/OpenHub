package com.thirtydegreesray.openhub.http.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.util.TokenRefreshRules;
import com.thirtydegreesray.openhub.util.TokenRefresher;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends every GitHub request with the account's newest token. Added to every OkHttp client
 * that talks to GitHub with a token (Retrofit and the workers' own clients), after the
 * interceptor that sets the Authorization header:
 * <ol>
 *   <li>an older token of an account is swapped for its newest one;</li>
 *   <li>a token that ends within 5 minutes is refreshed first;</li>
 *   <li>a 401 is refreshed once and the request is sent again with the new token (when its
 *   body can be sent again).</li>
 * </ol>
 * It never adds a token to a request that has none and only touches HTTPS requests to
 * api.github.com and uploads.github.com.
 */
public final class FreshTokenInterceptor implements Interceptor {

    public static final FreshTokenInterceptor INSTANCE = new FreshTokenInterceptor();

    private FreshTokenInterceptor() {
    }

    /** The hosts whose Authorization header may be renewed. */
    static boolean renewsTokenFor(@Nullable HttpUrl url) {
        if (url == null || !url.isHttps()) return false;
        String host = url.host();
        return host.equalsIgnoreCase("api.github.com") || host.equalsIgnoreCase("uploads.github.com");
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!renewsTokenFor(request.url())) return chain.proceed(request);
        String token = TokenRefreshRules.tokenOf(request.header("Authorization"));
        if (token == null) return chain.proceed(request);

        String fresh = TokenRefresher.freshToken(token);
        if (!fresh.equals(token)) request = withToken(request, fresh);

        Response response = chain.proceed(request);
        if (response.code() != 401) return response;

        RequestBody body = request.body();
        if (!TokenRefreshRules.canReplay(body != null, body != null && body.isOneShot(),
                body == null ? -1 : body.contentLength())) {
            return response;
        }
        String renewed = TokenRefresher.renewAfter401(fresh);
        if (renewed == null || renewed.equals(fresh)) return response;
        response.close();
        return chain.proceed(withToken(request, renewed));
    }

    @NonNull
    private static Request withToken(@NonNull Request request, @NonNull String token) {
        String old = request.header("Authorization");
        boolean bearer = old != null && old.trim().regionMatches(true, 0, "Bearer ", 0, 7);
        return request.newBuilder()
                .header("Authorization", (bearer ? "Bearer " : "token ") + token)
                .build();
    }
}
