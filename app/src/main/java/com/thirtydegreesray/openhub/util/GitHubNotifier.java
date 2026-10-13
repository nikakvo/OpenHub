package com.thirtydegreesray.openhub.util;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.thirtydegreesray.openhub.service.NotificationCheckWorker;

import java.util.concurrent.TimeUnit;

/**
 * Settings and scheduling of the background check for GitHub notifications (the inbox
 * on github.com). Off by default; when on, WorkManager checks every 15 minutes to a few
 * hours and new unread threads become Android notifications.
 */
public final class GitHubNotifier {

    public static final String PREF_ENABLED = "githubNotifications";
    public static final String PREF_INTERVAL = "githubNotificationsInterval";
    /** Android notifications posted for threads carry this tag, so they can be removed. */
    public static final String TAG = "github_thread";

    public static final int[] PRESET_MINUTES = {15, 30, 60, 120, 240, 480};
    private static final int DEFAULT_INTERVAL_MINUTES = 30;

    private static final String PREFS = "github_notifications";
    private static final String KEY_INTERVAL = "interval_minutes";
    private static final String KEY_LAST_SEEN = "last_seen";
    private static final String KEY_LAST_MODIFIED = "last_modified";
    private static final String PERIODIC_WORK = "github_notifications_check";
    private static final String NOW_WORK = "github_notifications_check_now";

    private GitHubNotifier() {
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(@NonNull Context context) {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getBoolean(PREF_ENABLED, false);
    }

    public static int getIntervalMinutes(@NonNull Context context) {
        return prefs(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES);
    }

    public static void setIntervalMinutes(@NonNull Context context, int minutes) {
        prefs(context).edit().putInt(KEY_INTERVAL, Math.max(15, minutes)).apply();
        schedule(context, true);
    }

    /** updated_at of the newest thread already announced (ISO 8601), null before the first check. */
    @Nullable
    public static String getLastSeen(@NonNull Context context) {
        return prefs(context).getString(KEY_LAST_SEEN, null);
    }

    public static void setLastSeen(@NonNull Context context, @NonNull String value) {
        prefs(context).edit().putString(KEY_LAST_SEEN, value).apply();
    }

    @Nullable
    public static String getLastModified(@NonNull Context context) {
        return prefs(context).getString(KEY_LAST_MODIFIED, null);
    }

    public static void setLastModified(@NonNull Context context, @Nullable String value) {
        prefs(context).edit().putString(KEY_LAST_MODIFIED, value).apply();
    }

    /** Called when the switch changes: a fresh start announces only what comes after it. */
    public static void onEnabledChanged(@NonNull Context context, boolean enabled) {
        prefs(context).edit().remove(KEY_LAST_SEEN).remove(KEY_LAST_MODIFIED).apply();
        schedule(context, true);
        if (enabled) checkNow(context);
    }

    public static void schedule(@NonNull Context context) {
        schedule(context, false);
    }

    private static void schedule(@NonNull Context context, boolean changed) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        if (!isEnabled(context)) {
            workManager.cancelUniqueWork(PERIODIC_WORK);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(NotificationCheckWorker.class,
                getIntervalMinutes(context), TimeUnit.MINUTES)
                .setConstraints(networkConstraint())
                .build();
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, changed
                ? ExistingPeriodicWorkPolicy.UPDATE : ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void checkNow(@NonNull Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(NotificationCheckWorker.class)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Removes the Android notifications of GitHub threads, e.g. once the inbox was opened. */
    public static void clearPosted(@NonNull Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        try {
            for (StatusBarNotification posted : manager.getActiveNotifications()) {
                if (TAG.equals(posted.getTag())) manager.cancel(posted.getTag(), posted.getId());
            }
        } catch (Exception ignored) {
        }
    }

    @NonNull
    public static String formatInterval(int minutes) {
        return ReleaseWatcher.formatInterval(minutes);
    }
}
