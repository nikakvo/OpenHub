package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * Short messages at the bottom of the screen: Android's own toasts. Android draws them
 * (since Android 12 with the app icon) and shows them also while the app is in the
 * background, which custom toast views can no longer do. The kinds (info, success,
 * warning, error) are kept so callers say what a message is.
 */
public final class Toasts {

    private Toasts() {
    }

    @NonNull
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message) {
        return make(context, message, Toast.LENGTH_SHORT);
    }

    @NonNull
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return make(context, message, duration);
    }

    @NonNull
    public static Toast info(@NonNull Context context, @NonNull CharSequence message) {
        return make(context, message, Toast.LENGTH_SHORT);
    }

    @NonNull
    public static Toast info(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return make(context, message, duration);
    }

    @NonNull
    public static Toast success(@NonNull Context context, @NonNull CharSequence message) {
        return make(context, message, Toast.LENGTH_SHORT);
    }

    @NonNull
    public static Toast success(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return make(context, message, duration);
    }

    @NonNull
    public static Toast warning(@NonNull Context context, @NonNull CharSequence message) {
        return make(context, message, Toast.LENGTH_SHORT);
    }

    @NonNull
    public static Toast warning(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return make(context, message, duration);
    }

    /** Errors stay a little longer on the screen. */
    @NonNull
    public static Toast error(@NonNull Context context, @NonNull CharSequence message) {
        return make(context, message, Toast.LENGTH_LONG);
    }

    @NonNull
    public static Toast error(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return make(context, message, duration);
    }

    @NonNull
    private static Toast make(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return Toast.makeText(context.getApplicationContext(), message, duration);
    }
}
