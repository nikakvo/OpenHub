package com.thirtydegreesray.openhub.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.BuildConfig;

/**
 * The app's log lines (Android's Log under the tag "OpenHub"). Debug and info lines are
 * written only in debug builds; warnings and errors always.
 */
public final class AppLog {

    private static final String TAG = "OpenHub";

    private AppLog() {
    }

    public static void d(@NonNull String where, @NonNull String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, where + ": " + message);
    }

    public static void i(@NonNull String where, @NonNull String message) {
        if (BuildConfig.DEBUG) Log.i(TAG, where + ": " + message);
    }

    public static void w(@NonNull String where, @NonNull String message) {
        Log.w(TAG, where + ": " + message);
    }

    public static void e(@NonNull String where, @NonNull String message, @Nullable Throwable error) {
        Log.e(TAG, where + ": " + message, error);
    }
}
