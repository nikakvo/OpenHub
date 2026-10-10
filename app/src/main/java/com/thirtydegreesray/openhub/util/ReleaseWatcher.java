package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.service.ReleaseCheckWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Repositories whose new releases are announced with a notification.
 *
 * Stored in SharedPreferences as a JSON object "owner/repo" -> last seen release tag
 * ("" until the first check records the current release without notifying).
 * A periodic WorkManager job (user-chosen interval, 6 hours by default, any network)
 * runs ReleaseCheckWorker while at least one repository is watched.
 */
public final class ReleaseWatcher {

    private static final String PREFS = "release_watch";
    private static final String KEY_REPOS = "repos";
    private static final String PERIODIC_WORK = "release-watch";
    private static final String NOW_WORK = "release-watch-now";
    private static final String KEY_INTERVAL = "interval_minutes";
    public static final int DEFAULT_INTERVAL_MINUTES = 6 * 60;
    /** WorkManager does not run periodic work more often than every 15 minutes. */
    public static final int MIN_INTERVAL_MINUTES = 15;
    public static final int MAX_INTERVAL_MINUTES = 7 * 24 * 60;
    /** 1, 2, 4, 6, 8, 12 hours and 1 day. */
    public static final int[] PRESET_MINUTES = {60, 120, 240, 360, 480, 720, 1440};

    private ReleaseWatcher() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JsonObject load(Context context) {
        String json = prefs(context).getString(KEY_REPOS, "{}");
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonObject()) return element.getAsJsonObject();
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static void save(Context context, JsonObject repos) {
        prefs(context).edit().putString(KEY_REPOS, repos.toString()).apply();
    }

    @NonNull
    public static String key(@NonNull String owner, @NonNull String repo) {
        return owner + "/" + repo;
    }

    public static synchronized boolean isWatched(@NonNull Context context, @NonNull String fullName) {
        return load(context).has(fullName);
    }

    /** Sorted "owner/repo" names. */
    @NonNull
    public static synchronized List<String> getWatched(@NonNull Context context) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : load(context).entrySet()) list.add(entry.getKey());
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    @Nullable
    public static synchronized String getLastTag(@NonNull Context context, @NonNull String fullName) {
        JsonObject repos = load(context);
        return repos.has(fullName) ? repos.get(fullName).getAsString() : null;
    }

    public static synchronized void setLastTag(@NonNull Context context, @NonNull String fullName,
                                               @NonNull String tag) {
        JsonObject repos = load(context);
        if (!repos.has(fullName)) return; // un-watched meanwhile
        repos.addProperty(fullName, tag);
        save(context, repos);
    }

    public static synchronized void setWatched(@NonNull Context context, @NonNull String fullName,
                                               boolean watched) {
        JsonObject repos = load(context);
        if (watched) {
            if (!repos.has(fullName)) repos.addProperty(fullName, "");
        } else {
            repos.remove(fullName);
        }
        save(context, repos);
        schedule(context);
        // record the current release right away, so only later ones are announced
        if (watched) checkNow(context);
    }

    public static int getIntervalMinutes(@NonNull Context context) {
        int minutes = prefs(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES);
        return Math.max(MIN_INTERVAL_MINUTES, Math.min(MAX_INTERVAL_MINUTES, minutes));
    }

    /** Stores the interval (clamped to 15 min .. 7 days) and reschedules the check. */
    public static void setIntervalMinutes(@NonNull Context context, int minutes) {
        minutes = Math.max(MIN_INTERVAL_MINUTES, Math.min(MAX_INTERVAL_MINUTES, minutes));
        prefs(context).edit().putInt(KEY_INTERVAL, minutes).apply();
        schedule(context, true);
    }

    /** "15 min", "2 h", "1 h 30 min", "1 day", "1 day 6 h". */
    @NonNull
    public static String formatInterval(int minutes) {
        int days = minutes / 1440;
        int hours = (minutes % 1440) / 60;
        int mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(days == 1 ? " day" : " days");
        if (hours > 0) sb.append(sb.length() > 0 ? " " : "").append(hours).append(" h");
        if (mins > 0) sb.append(sb.length() > 0 ? " " : "").append(mins).append(" min");
        return sb.toString();
    }

    /** Starts or stops the periodic check depending on whether anything is watched. */
    public static void schedule(@NonNull Context context) {
        schedule(context, false);
    }

    private static void schedule(@NonNull Context context, boolean intervalChanged) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        if (getWatched(context).isEmpty()) {
            workManager.cancelUniqueWork(PERIODIC_WORK);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ReleaseCheckWorker.class,
                getIntervalMinutes(context), TimeUnit.MINUTES)
                .setConstraints(networkConstraint())
                .build();
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, intervalChanged
                ? ExistingPeriodicWorkPolicy.UPDATE : ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void checkNow(@NonNull Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReleaseCheckWorker.class)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }
}
