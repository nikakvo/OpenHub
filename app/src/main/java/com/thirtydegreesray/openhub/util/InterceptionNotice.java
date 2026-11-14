package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.AboutActivity;

/**
 * Tells the user, on the screen in front, that {@link TlsGuard} stopped requests because the
 * connection to GitHub is intercepted. One dialog at a time, however many requests fail
 * together; after Close it stays quiet for a minute (screens retry by themselves).
 * Nothing is shown while no screen of OpenHub is in front (background checks just fail).
 */
public final class InterceptionNotice {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long QUIET_AFTER_CLOSE_MS = 60_000;

    private static boolean showing;
    private static long closedAt;

    private InterceptionNotice() {
    }

    /** Any thread. */
    public static void report(@NonNull String host) {
        MAIN.post(() -> show(host));
    }

    private static void show(@NonNull String host) {
        Activity activity = SignInWatch.resumedActivity();
        if (activity == null || showing || activity.isFinishing() || activity.isDestroyed()) return;
        if (closedAt != 0 && SystemClock.elapsedRealtime() - closedAt < QUIET_AFTER_CLOSE_MS) return;
        showing = true;
        // the user's theme, also over the start screen (its own theme is a plain light one)
        Context themed = new ContextThemeWrapper(activity,
                ThemeHelper.getTheme(PrefUtils.getTheme(), PrefUtils.getAccentColor()));
        AlertDialog.Builder builder = new AlertDialog.Builder(themed)
                .setTitle(R.string.tls_intercepted_title)
                .setMessage(activity.getString(R.string.tls_intercepted_message, host))
                .setPositiveButton(R.string.close, (d, w) -> closedAt = SystemClock.elapsedRealtime())
                .setOnCancelListener(d -> closedAt = SystemClock.elapsedRealtime())
                .setOnDismissListener(d -> showing = false);
        if (!(activity instanceof AboutActivity)) {
            builder.setNeutralButton(R.string.tls_intercepted_details, (d, w) -> {
                closedAt = SystemClock.elapsedRealtime();
                AboutActivity.show(activity);
            });
        }
        builder.show();
    }
}
