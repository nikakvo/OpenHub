package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * The web views of the app (a README, a comment opened alone, Help) share one HTTP cache
 * in cacheDir/WebView. WebView sizes it from the free space of the phone, so it can grow
 * to hundreds of MB between two "Clear cache" runs (20 README opens with animated
 * pictures: 22 MB to 43 MB). At app start it is measured in the background and emptied
 * when it is over {@link #LIMIT_BYTES}.
 */
public final class WebViewCache {

    /** Above this size the WebView HTTP cache is emptied at the next app start. */
    public static final long LIMIT_BYTES = 50L * 1024 * 1024;

    private WebViewCache() {
    }

    /** True when a cache of this size should be emptied. */
    public static boolean overLimit(long bytes) {
        return bytes > LIMIT_BYTES;
    }

    /** cacheDir/WebView: the HTTP cache of all web views of the app (and their small extras). */
    @NonNull
    public static File dir(@NonNull Context context) {
        return new File(context.getCacheDir(), "WebView");
    }

    /**
     * Measures the cache off the main thread; when it is over the limit it is emptied on
     * the main thread once the app is idle (a WebView may only be made there).
     */
    public static void trimInBackground(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            long bytes = folderSize(dir(app));
            if (!overLimit(bytes)) return;
            new android.os.Handler(Looper.getMainLooper()).post(() ->
                    Looper.myQueue().addIdleHandler(() -> {
                        clear(app);
                        return false;
                    }));
        }, "webview-cache");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** Empties the HTTP cache of all web views of the app. Main thread only. */
    public static void clear(@NonNull Context context) {
        try {
            WebView webView = new WebView(context);
            webView.clearCache(true);
            webView.destroy();
        } catch (RuntimeException e) {
            // no WebView on this phone (being updated or switched off): nothing to clear
        }
    }

    public static long folderSize(@Nullable File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += folderSize(child);
        return total;
    }
}
