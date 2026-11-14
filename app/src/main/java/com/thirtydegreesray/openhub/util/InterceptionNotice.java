package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.AboutActivity;
import com.thirtydegreesray.openhub.ui.activity.SplashActivity;

import java.lang.ref.WeakReference;

/**
 * Tells the user, on the screen in front, that {@link TlsGuard} stopped requests because the
 * connection to GitHub is intercepted. One dialog at a time, however many requests fail
 * together; after Close it stays quiet for a minute (screens retry by themselves).
 *
 * A report waits up to {@link #PENDING_MS} for a screen that can show it: the start screen
 * hands over to the next screen at once (a dialog on it would vanish with it), so the dialog
 * goes to the next screen, or onto the start screen when it is still there after a moment.
 * Nothing is shown while no screen of OpenHub is in front (background checks just fail).
 * Main thread only, except {@link #report}.
 */
public final class InterceptionNotice {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long QUIET_AFTER_CLOSE_MS = 60_000;
    /** How long a report waits for a screen. */
    private static final long PENDING_MS = 30_000;
    /** How long the start screen gets to hand over before the dialog goes onto it. */
    private static final long SPLASH_WAIT_MS = 3_000;

    @Nullable private static WeakReference<AlertDialog> dialog;
    @Nullable private static WeakReference<Activity> dialogOwner;
    private static long closedAt;
    @Nullable private static String pendingHost;
    private static long pendingAt;

    private InterceptionNotice() {
    }

    /** Any thread. */
    public static void report(@NonNull String host) {
        MAIN.post(() -> {
            if (pendingHost == null) pendingAt = SystemClock.elapsedRealtime();
            pendingHost = host;
            tryShow();
        });
    }

    /** A screen came to the front ({@link SignInWatch}): a waiting report is shown there. */
    static void onResumed() {
        if (pendingHost != null) tryShow();
    }

    private static void tryShow() {
        if (pendingHost == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - pendingAt > PENDING_MS || isShowing()
                || (closedAt != 0 && now - closedAt < QUIET_AFTER_CLOSE_MS)) {
            pendingHost = null;
            return;
        }
        Activity activity = SignInWatch.resumedActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return; // waits
        if (activity instanceof SplashActivity && now - pendingAt < SPLASH_WAIT_MS) {
            MAIN.postDelayed(InterceptionNotice::tryShow, SPLASH_WAIT_MS - (now - pendingAt) + 50);
            return;
        }
        String host = pendingHost;
        pendingHost = null;
        // the user's theme, also over the start screen (its own theme is a plain light one)
        Context themed = new ContextThemeWrapper(activity,
                ThemeHelper.getTheme(PrefUtils.getTheme(), PrefUtils.getAccentColor()));
        AlertDialog.Builder builder = new AlertDialog.Builder(themed)
                .setTitle(R.string.tls_intercepted_title)
                .setMessage(activity.getString(R.string.tls_intercepted_message, host))
                .setPositiveButton(R.string.close, (d, w) -> closedAt = SystemClock.elapsedRealtime())
                .setOnCancelListener(d -> closedAt = SystemClock.elapsedRealtime());
        if (!(activity instanceof AboutActivity)) {
            builder.setNeutralButton(R.string.tls_intercepted_details, (d, w) -> {
                closedAt = SystemClock.elapsedRealtime();
                AboutActivity.show(activity);
            });
        }
        try {
            dialog = new WeakReference<>(builder.show());
            dialogOwner = new WeakReference<>(activity);
        } catch (Exception ignored) {
            // the screen went away in the same moment: the next failed request reports again
        }
    }

    /**
     * True while the dialog is really on a living screen. A dialog whose screen finished
     * without dismissing it (the start screen) does not count, so it can never block the
     * next one.
     */
    private static boolean isShowing() {
        AlertDialog d = dialog == null ? null : dialog.get();
        Activity owner = dialogOwner == null ? null : dialogOwner.get();
        if (d == null || owner == null) return false;
        return d.isShowing() && !owner.isFinishing() && !owner.isDestroyed();
    }
}
