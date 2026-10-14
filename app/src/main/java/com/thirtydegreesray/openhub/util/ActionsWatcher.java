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
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.service.ActionsCheckWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Repositories whose finished GitHub Actions runs are announced with a notification.
 *
 * Two kinds: a repository the user switched on ("Notify when runs finish", kept until
 * switched off) and a temporary one, added when the user starts a workflow in the app;
 * it only announces runs created after that and ends when they are done (or after 6 h).
 *
 * Stored as JSON "owner/repo" -> {"since": millis, "temp": bool, "until": millis,
 * "done": [run ids already announced or recorded]}.
 *
 * A periodic check (every 15 min, the WorkManager minimum) runs while a repository is
 * switched on; while a run is queued or in progress, or a temporary watch is active,
 * a follow-up check runs every 90 seconds, so the notification comes right after the end.
 */
public final class ActionsWatcher {

    private static final String PREFS = "actions_watch";
    private static final String KEY_REPOS = "repos";
    private static final String PERIODIC_WORK = "actions-watch";
    private static final String FOLLOW_WORK = "actions-watch-follow";
    private static final int PERIODIC_MINUTES = 15;
    public static final long FOLLOW_DELAY_SECONDS = 90;
    private static final long TEMP_HOURS = 6;
    /** Run ids remembered per repository. */
    private static final int MAX_DONE = 60;

    private ActionsWatcher() {
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    private static JsonObject load(@NonNull Context context) {
        try {
            JsonElement element = JsonParser.parseString(prefs(context).getString(KEY_REPOS, "{}"));
            if (element.isJsonObject()) return element.getAsJsonObject();
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static void save(@NonNull Context context, @NonNull JsonObject repos) {
        prefs(context).edit().putString(KEY_REPOS, repos.toString()).apply();
    }

    /** Switched on by the user (not only a temporary watch). */
    public static synchronized boolean isWatched(@NonNull Context context, @NonNull String fullName) {
        // not Json.obj(): repository names may contain dots
        JsonElement repo = load(context).get(fullName);
        return repo != null && repo.isJsonObject() && !Json.bool(repo.getAsJsonObject(), "temp");
    }

    public static synchronized void setWatched(@NonNull Context context, @NonNull String fullName,
                                               boolean watched) {
        JsonObject repos = load(context);
        if (watched) {
            JsonObject repo = new JsonObject();
            repo.addProperty("since", System.currentTimeMillis());
            repo.addProperty("temp", false);
            repo.add("done", existingDone(repos, fullName));
            repos.add(fullName, repo);
        } else {
            repos.remove(fullName);
        }
        save(context, repos);
        schedule(context);
        if (watched) followSoon(context, 5);
    }

    /**
     * The user started a workflow in the app: announce the runs it creates, even if the
     * repository is not switched on. A switched-on repository is announced anyway.
     */
    public static synchronized void followOnce(@NonNull Context context, @NonNull String fullName) {
        JsonObject repos = load(context);
        if (!(repos.has(fullName) && repos.get(fullName).isJsonObject()
                && !Json.bool(repos.getAsJsonObject(fullName), "temp"))) {
            JsonObject repo = new JsonObject();
            // a little earlier: the run is created by GitHub a moment after the request
            repo.addProperty("since", System.currentTimeMillis() - 60_000);
            repo.addProperty("temp", true);
            repo.addProperty("until", System.currentTimeMillis() + TimeUnit.HOURS.toMillis(TEMP_HOURS));
            repo.add("done", existingDone(repos, fullName));
            repos.add(fullName, repo);
            save(context, repos);
        }
        followSoon(context, 20);
    }

    @NonNull
    private static JsonArray existingDone(@NonNull JsonObject repos, @NonNull String fullName) {
        JsonElement old = repos.get(fullName);
        if (old != null && old.isJsonObject()) {
            JsonElement done = old.getAsJsonObject().get("done");
            if (done != null && done.isJsonArray()) return done.getAsJsonArray();
        }
        return new JsonArray();
    }

    /** Sorted "owner/repo" names, temporary watches included. */
    @NonNull
    public static synchronized List<String> getAll(@NonNull Context context) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : load(context).entrySet()) list.add(entry.getKey());
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    /** Names switched on by the user. */
    @NonNull
    public static synchronized List<String> getWatched(@NonNull Context context) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : load(context).entrySet()) {
            if (entry.getValue().isJsonObject() && !Json.bool(entry.getValue().getAsJsonObject(), "temp")) {
                list.add(entry.getKey());
            }
        }
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    /** A copy of the stored state of one repository, null when it is not watched. */
    @Nullable
    public static synchronized JsonObject get(@NonNull Context context, @NonNull String fullName) {
        JsonElement repo = load(context).get(fullName);
        return repo != null && repo.isJsonObject() ? repo.getAsJsonObject().deepCopy() : null;
    }

    /** Remembers that a run was announced (or was finished before the watch began). */
    public static synchronized void markDone(@NonNull Context context, @NonNull String fullName,
                                             @NonNull List<Long> runIds) {
        if (runIds.isEmpty()) return;
        JsonObject repos = load(context);
        JsonElement element = repos.get(fullName);
        if (element == null || !element.isJsonObject()) return; // switched off meanwhile
        JsonObject repo = element.getAsJsonObject();
        JsonArray done = existingDone(repos, fullName);
        for (Long id : runIds) done.add(id);
        while (done.size() > MAX_DONE) done.remove(0);
        repo.add("done", done);
        save(context, repos);
    }

    /** Ends a temporary watch (its runs are finished or it expired). */
    public static synchronized void endTemporary(@NonNull Context context, @NonNull String fullName) {
        JsonObject repos = load(context);
        JsonElement element = repos.get(fullName);
        if (element != null && element.isJsonObject() && Json.bool(element.getAsJsonObject(), "temp")) {
            repos.remove(fullName);
            save(context, repos);
        }
    }

    /** Starts or stops the periodic check depending on whether a repository is switched on. */
    public static void schedule(@NonNull Context context) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        if (getWatched(context).isEmpty()) {
            workManager.cancelUniqueWork(PERIODIC_WORK);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ActionsCheckWorker.class,
                PERIODIC_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraint())
                .build();
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** One check after {@code delaySeconds}, unless one is already waiting. */
    public static void followSoon(@NonNull Context context, long delaySeconds) {
        followSoon(context, delaySeconds, null);
    }

    /**
     * @param self the id of the worker asking (it is still running, so it does not count
     *             as waiting)
     */
    public static void followSoon(@NonNull Context context, long delaySeconds, @Nullable UUID self) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        try {
            for (WorkInfo info : workManager.getWorkInfosForUniqueWork(FOLLOW_WORK).get()) {
                if (info.getState() == WorkInfo.State.ENQUEUED && !info.getId().equals(self)) return;
            }
        } catch (Exception ignored) {
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ActionsCheckWorker.class)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(networkConstraint())
                .build();
        // APPEND: never cancels a check that is running right now
        workManager.enqueueUniqueWork(FOLLOW_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }
}
