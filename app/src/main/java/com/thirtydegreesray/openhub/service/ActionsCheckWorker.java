package com.thirtydegreesray.openhub.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.ui.activity.BrowserFilterActivity;
import com.thirtydegreesray.openhub.util.ActionsWatcher;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Looks at the newest workflow runs of every repository in {@link ActionsWatcher} and
 * posts a notification for each run that finished since the last check, with buttons to
 * download its artifacts. Runs that were already finished when the watch began are only
 * recorded. While a run is still going, the next check comes after 90 seconds.
 */
public class ActionsCheckWorker extends Worker {

    static final String CHANNEL_ID = "workflow_runs";
    public static final String TAG = "workflow_run";
    private static final int MAX_DOWNLOAD_BUTTONS = 2;

    private OkHttpClient client;
    @Nullable private String token;

    public ActionsCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        List<String> repos = ActionsWatcher.getAll(context);
        if (repos.isEmpty()) return Result.success();

        token = ReleaseCheckWorker.loadToken();
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        boolean followUp = false;
        for (String fullName : repos) {
            if (isStopped()) break;
            try {
                followUp |= check(context, fullName);
            } catch (Exception e) {
                // network trouble: try again soon if something is being followed
                JsonObject state = ActionsWatcher.get(context, fullName);
                if (state != null && Json.bool(state, "temp")) followUp = true;
            }
        }
        if (followUp && !isStopped()) {
            ActionsWatcher.followSoon(context, ActionsWatcher.FOLLOW_DELAY_SECONDS, getId());
        }
        return Result.success();
    }

    /** @return true when the repository needs another look soon */
    private boolean check(@NonNull Context context, @NonNull String fullName) throws Exception {
        JsonObject state = ActionsWatcher.get(context, fullName);
        if (state == null) return false;
        long since = state.has("since") ? state.get("since").getAsLong() : 0;
        boolean temp = Json.bool(state, "temp");
        long until = state.has("until") ? state.get("until").getAsLong() : 0;
        Set<Long> done = new HashSet<>();
        JsonElement doneArray = state.get("done");
        if (doneArray != null && doneArray.isJsonArray()) {
            for (JsonElement id : doneArray.getAsJsonArray()) done.add(id.getAsLong());
        }

        JsonObject answer = get("repos/" + fullName + "/actions/runs?per_page=20");
        if (answer == null) return temp;
        JsonElement runs = answer.get("workflow_runs");
        if (runs == null || !runs.isJsonArray()) return temp;

        boolean running = false;
        boolean ownRunSeen = false;
        List<Long> finished = new ArrayList<>();
        for (JsonElement element : runs.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject run = element.getAsJsonObject();
            long id = run.get("id").getAsLong();
            long created = time(run, "created_at");
            if (temp && created < since) continue; // not started by the user
            if (temp) ownRunSeen = true;
            if (!"completed".equals(Json.str(run, "status"))) {
                running = true;
                continue;
            }
            if (done.contains(id)) continue;
            finished.add(id);
            boolean finishedBefore = time(run, "updated_at") < since;
            String conclusion = Json.str(run, "conclusion");
            if (!finishedBefore && !"skipped".equals(conclusion) && !"neutral".equals(conclusion)) {
                notifyRun(context, fullName, run);
            }
        }
        ActionsWatcher.markDone(context, fullName, finished);

        if (temp) {
            boolean expired = System.currentTimeMillis() > until;
            if (expired || (ownRunSeen && !running)) {
                ActionsWatcher.endTemporary(context, fullName);
                return false;
            }
            return true; // waiting for the run to appear or to finish
        }
        return running;
    }

    @Nullable
    private JsonObject get(@NonNull String path) throws Exception {
        Request.Builder request = new Request.Builder()
                .url(AppConfig.GITHUB_API_BASE_URL + path)
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache");
        if (!StringUtils.isBlank(token)) {
            request.header("Authorization", token.startsWith("Basic") ? token : "token " + token);
        }
        try (Response response = client.newCall(request.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) return null;
            JsonElement element = JsonParser.parseString(body.string());
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    private static long time(@NonNull JsonObject run, @NonNull String key) {
        Date date = Json.date(run, key);
        return date == null ? 0 : date.getTime();
    }

    private void notifyRun(@NonNull Context context, @NonNull String fullName, @NonNull JsonObject run) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.actions_runs_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }

        long id = run.get("id").getAsLong();
        String workflow = Json.str(run, "name");
        if (workflow.isEmpty()) workflow = context.getString(R.string.actions);
        String label = workflow + " #" + Json.integer(run, "run_number");
        String conclusion = Json.str(run, "conclusion");
        String title;
        int color;
        if ("success".equals(conclusion)) {
            title = context.getString(R.string.actions_notify_succeeded, label);
            color = 0xFF3FB950;
        } else if ("failure".equals(conclusion) || "startup_failure".equals(conclusion)
                || "timed_out".equals(conclusion)) {
            title = context.getString(R.string.actions_notify_failed, label);
            color = 0xFFF85149;
        } else if ("cancelled".equals(conclusion)) {
            title = context.getString(R.string.actions_notify_cancelled, label);
            color = 0xFF8B949E;
        } else {
            title = context.getString(R.string.actions_notify_finished, label, conclusion.replace('_', ' '));
            color = 0xFFE3B341;
        }

        StringBuilder text = new StringBuilder(fullName);
        String branch = Json.str(run, "head_branch");
        if (!branch.isEmpty()) text.append(" · ").append(branch);
        long started = time(run, "run_started_at");
        long ended = time(run, "updated_at");
        if (started > 0 && ended > started) text.append(" · ").append(duration((ended - started) / 1000));

        int slash = fullName.indexOf('/');
        String owner = fullName.substring(0, slash);
        String repo = fullName.substring(slash + 1);
        // through BrowserFilterActivity: it loads the account first when the app was closed
        Intent open = new Intent(context, BrowserFilterActivity.class)
                .setData(Uri.parse(BrowserFilterActivity.actionsUri(fullName, canPush(fullName))))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int notificationId = Long.hashCode(id);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setColor(color)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(Json.str(run, "display_title"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(PendingIntent.getActivity(context, notificationId, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        if ("success".equals(conclusion)) addArtifactActions(context, builder, fullName, repo, run,
                notificationId, open);
        manager.notify(TAG, notificationId, builder.build());
    }

    private void addArtifactActions(@NonNull Context context, @NonNull NotificationCompat.Builder builder,
                                    @NonNull String fullName, @NonNull String repo,
                                    @NonNull JsonObject run, int notificationId, @NonNull Intent open) {
        JsonArray artifacts = new JsonArray();
        try {
            JsonObject answer = get("repos/" + fullName + "/actions/runs/" + run.get("id").getAsLong()
                    + "/artifacts?per_page=30");
            JsonElement list = answer == null ? null : answer.get("artifacts");
            if (list != null && list.isJsonArray()) {
                for (JsonElement element : list.getAsJsonArray()) {
                    if (element.isJsonObject() && !Json.bool(element.getAsJsonObject(), "expired")) {
                        artifacts.add(element);
                    }
                }
            }
        } catch (Exception ignored) {
            return;
        }
        if (artifacts.size() == 0) return;
        if (artifacts.size() > MAX_DOWNLOAD_BUTTONS) {
            builder.addAction(0, context.getResources().getQuantityString(R.plurals.actions_notify_artifacts,
                    artifacts.size(), artifacts.size()),
                    PendingIntent.getActivity(context, notificationId + 1, open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        int runNumber = Json.integer(run, "run_number");
        for (int i = 0; i < artifacts.size(); i++) {
            JsonObject artifact = artifacts.get(i).getAsJsonObject();
            String name = Json.str(artifact, "name");
            String fileName = repo + "-" + name + "-run" + runNumber + ".zip";
            Intent download = new Intent(context, NotificationActionReceiver.class)
                    .setAction(NotificationActionReceiver.ACTION_DOWNLOAD_ARTIFACT)
                    .putExtra(NotificationActionReceiver.EXTRA_URL, Json.str(artifact, "archive_download_url"))
                    .putExtra(NotificationActionReceiver.EXTRA_FILE_NAME, fileName)
                    .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId);
            String size = StringUtils.getSizeString(artifact.get("size_in_bytes").getAsLong());
            builder.addAction(0, context.getString(R.string.actions_notify_download, name, size),
                    PendingIntent.getBroadcast(context, notificationId * 4 + i + 2, download,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
    }

    /** Whether the user may start workflows here (push access), so the play button shows. */
    private boolean canPush(@NonNull String fullName) {
        try {
            JsonObject repository = get("repos/" + fullName);
            return repository != null && Json.bool(repository, "permissions.push");
        } catch (Exception e) {
            return false;
        }
    }

    /** "45 s", "3 min 12 s", "1 h 5 min". */
    @NonNull
    private static String duration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + " h " + m + " min";
        if (m > 0) return m + " min " + s + " s";
        return s + " s";
    }
}
