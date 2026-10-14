

package com.thirtydegreesray.openhub.http.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.BuildConfig;
import com.thirtydegreesray.openhub.util.FileUtil;
import com.thirtydegreesray.openhub.util.NetHelper;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

/**
 * Retrofit 网络请求
 * Created by ThirtyDegreesRay on 2016/7/15 11:39
 */
public enum  AppRetrofit {
    INSTANCE;

    private final String TAG = "AppRetrofit";

    /**
     * One Retrofit per base URL, response type and account token. Each one owns its token:
     * the old shared, changing token field could send a request without the token, or with
     * another account's token, when two requests ran at the same time.
     */
    private final HashMap<String, Retrofit> retrofitMap = new HashMap<>();
    /** One connection pool and one disk cache for all of them. */
    @Nullable private OkHttpClient baseClient;

    @NonNull
    private synchronized OkHttpClient baseClient() {
        if (baseClient == null) {
            int timeOut = AppConfig.HTTP_TIME_OUT;
            Cache cache = new Cache(FileUtil.getHttpImageCacheDir(AppApplication.get()),
                    AppConfig.HTTP_MAX_CACHE_SIZE);
            baseClient = new OkHttpClient.Builder()
                    .connectTimeout(timeOut, TimeUnit.MILLISECONDS)
                    .addNetworkInterceptor(new NetworkBaseInterceptor())
                    .cache(cache)
                    .build();
        }
        return baseClient;
    }

    @NonNull
    private Retrofit createRetrofit(@NonNull String baseUrl, @Nullable String token, boolean isJson) {
        OkHttpClient client = baseClient().newBuilder()
                // first, so the network interceptor and the cache see the final request
                .addInterceptor(new BaseInterceptor(token))
                .build();
        Retrofit.Builder builder = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .client(client);
        if (isJson) {
            builder.addConverterFactory(GsonConverterFactory.create());
        } else {
            builder.addConverterFactory(SimpleXmlConverterFactory.createNonStrict());
        }
        return builder.build();
    }

    public synchronized Retrofit getRetrofit(@NonNull String baseUrl, @Nullable String token, boolean isJson) {
        // the token itself is part of the key (in memory only): no two accounts share a client
        String key = baseUrl + "-" + isJson + "-" + (StringUtils.isBlank(token) ? "" : token);
        Retrofit retrofit = retrofitMap.get(key);
        if (retrofit == null) {
            retrofit = createRetrofit(baseUrl, token, isJson);
            retrofitMap.put(key, retrofit);
        }
        return retrofit;
    }

    public Retrofit getRetrofit(@NonNull String baseUrl, @Nullable String token) {
        return getRetrofit(baseUrl, token, true);
    }

    /** After logout or an account switch: no client keeps an old token. */
    public synchronized void clearClients() {
        retrofitMap.clear();
    }

    /** Deletes the cached GitHub pages (private ones too), e.g. after logout. */
    public synchronized void clearCache() {
        try {
            Cache cache = baseClient().cache();
            if (cache != null) cache.evictAll();
        } catch (IOException ignored) {
        }
    }

    /**
     * Whether a request to this host may carry the GitHub token. Only the API gets it:
     * file and image addresses can point anywhere, and github.com pages do not need it.
     */
    public static boolean isTokenHost(@Nullable String host) {
        return host != null && host.equalsIgnoreCase("api.github.com");
    }

    /** True when a request to {@code url} gets the token: HTTPS to api.github.com only. */
    public static boolean sendsTokenTo(@Nullable okhttp3.HttpUrl url) {
        return url != null && url.isHttps() && isTokenHost(url.host());
    }

    /** github.com and its API, where the login name may be added to keep accounts' caches apart. */
    static boolean isGitHubHost(@Nullable String host) {
        return host != null && (host.equalsIgnoreCase("api.github.com") || host.equalsIgnoreCase("github.com"));
    }

    /**
     * Adds the token of its own client (only for the GitHub API) and the cache rules.
     */
    private static class BaseInterceptor implements Interceptor {
        @Nullable private final String token;

        BaseInterceptor(@Nullable String token) {
            this.token = token;
        }

        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();
            String host = request.url().host();

            // the login name in the URL keeps the caches of several accounts apart
            if (isGitHubHost(host) && AppData.INSTANCE.getLoggedUser() != null
                    && !AppConfig.isCommonPageUrl(request.url().toString())) {
                HttpUrl url = request.url().newBuilder()
                        .addQueryParameter("uniqueLoginId",
                                AppData.INSTANCE.getLoggedUser().getLogin())
                        .build();
                request = request.newBuilder()
                        .url(url)
                        .build();
            }

            // only to the GitHub API and only encrypted (an http:// address never gets it)
            if (!StringUtils.isBlank(token) && sendsTokenTo(request.url())) {
                String auth = token.startsWith("Basic") ? token : "token " + token;
                request = request.newBuilder()
                        .header("Authorization", auth)
                        .build();
            }
            if (BuildConfig.DEBUG) Log.d("AppRetrofit", request.url().toString());

            // second request: force the network
            String forceNetWork = request.header("forceNetWork");
            // with forceNetWork and no network: read from the cache
            if (!StringUtils.isBlank(forceNetWork) &&
                    !NetHelper.INSTANCE.getNetEnabled()) {
                request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build();
            } else if("true".equals(forceNetWork)){
                request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .build();
            }

            return chain.proceed(request);
        }
    }

    /**
     * 网络请求拦截器
     */
    private class NetworkBaseInterceptor implements Interceptor {
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {

            Request request = chain.request();
            Response originalResponse = chain.proceed(request);

            // Never keep an error: a cached 404 ("not found yet") would hide a repository,
            // file or release that exists a moment later.
            if (!originalResponse.isSuccessful()) {
                return originalResponse.newBuilder()
                        .header("Cache-Control", "no-store")
                        .removeHeader("Pragma")
                        .build();
            }

//            String serverCacheControl = originalResponse.header("Cache-Control");
            String requestCacheControl = request.cacheControl().toString();

//            //若服务器端有缓存策略，则无需修改
//            if (StringUtil.isBlank(serverCacheControl)) {
//                return originalResponse;
//            }
//            //不设置缓存策略
//            else

            //有forceNetWork时，强制更改缓存策略
            String forceNetWork = request.header("forceNetWork");
            if(!StringUtils.isBlank(forceNetWork)){
                requestCacheControl = getCacheString();
            }

            if (StringUtils.isBlank(requestCacheControl)) {
                return originalResponse;
            }
            //设置缓存策略
            else {
                Response res = originalResponse.newBuilder()
                        .header("Cache-Control", requestCacheControl)
                        //纠正服务器时间，服务器时间出错时可能会导致缓存处理出错
//                        .header("Date", getGMTTime())
                        .removeHeader("Pragma")
                        .build();
                return res;
            }

        }
    }

    private static String getGMTTime(){
        Date date = new Date();
        DateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        format.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        String gmtTime = format.format(date);
        return gmtTime;
    }

    public static String getCacheString(){
        return "public, max-age=" + AppConfig.CACHE_MAX_AGE;
    }

}
