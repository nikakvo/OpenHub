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
import com.thirtydegreesray.openhub.util.GitHubNotifier;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.NotificationLinks;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Looks at the unread GitHub notifications and posts an Android notification for each
 * thread that changed since the last check (at most 10, grouped). The very first check
 * after turning the feature on only records where the inbox stands.
 */
public class NotificationCheckWorker extends Worker {

    static final String CHANNEL_ID = "github_notifications";
    private static final String GROUP = "github_notifications";
    private static final int SUMMARY_ID = 0x6e6f7469;
    private static final int MAX_POSTED = 10;

    public NotificationCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!GitHubNotifier.isEnabled(context)) return Result.success();
        String token = ReleaseCheckWorker.loadToken();
        if (StringUtils.isBlank(token)) return Result.success();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        Request.Builder request = new Request.Builder()
                .url(AppConfig.GITHUB_API_BASE_URL + "notifications?per_page=50")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache")
                .header("Authorization", token.startsWith("Basic") ? token : "token " + token);
        String lastModified = GitHubNotifier.getLastModified(context);
        if (lastModified != null) request.header("If-Modified-Since", lastModified);

        try (Response response = client.newCall(request.build()).execute()) {
            if (response.code() == 304) return Result.success(); // nothing new
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                return response.code() >= 500 ? Result.retry() : Result.success();
            }
            JsonArray threads = JsonParser.parseString(body.string()).getAsJsonArray();
            String lastSeen = GitHubNotifier.getLastSeen(context);
            String newest = lastSeen;
            List<JsonObject> fresh = new ArrayList<>();
            for (JsonElement e : threads) {
                JsonObject thread = e.getAsJsonObject();
                String updated = Json.str(thread, "updated_at");
                // ISO 8601 in UTC compares correctly as text
                if (newest == null || updated.compareTo(newest) > 0) newest = updated;
                if (lastSeen != null && updated.compareTo(lastSeen) > 0) fresh.add(thread);
            }
            if (newest != null) GitHubNotifier.setLastSeen(context, newest);
            GitHubNotifier.setLastModified(context, response.header("Last-Modified"));
            if (lastSeen == null) return Result.success(); // first check: only the starting point
            post(context, fresh, threads.size());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private static void post(@NonNull Context context, @NonNull List<JsonObject> fresh, int unread) {
        if (fresh.isEmpty()) return;
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
                    context.getString(R.string.github_notifications_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        int count = 0;
        for (JsonObject thread : fresh) {
            if (count++ >= MAX_POSTED) break;
            String id = Json.str(thread, "id");
            String repo = Json.str(thread, "repository.full_name");
            String title = Json.str(thread, "subject.title");
            String reason = NotificationLinks.reason(context, Json.str(thread, "reason"));
            String url = NotificationLinks.htmlUrl(Json.str(thread, "subject.type"),
                    Json.str(thread, "subject.url"), repo);

            Intent open = new Intent(context, BrowserFilterActivity.class)
                    .setData(Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent markRead = new Intent(context, NotificationActionReceiver.class)
                    .setAction(NotificationActionReceiver.ACTION_MARK_READ)
                    .putExtra(NotificationActionReceiver.EXTRA_THREAD, id);
            int requestCode = id.hashCode();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle(title)
                    .setContentText(reason == null ? repo : repo + " · " + reason)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(title))
                    .setSubText(repo)
                    .setGroup(GROUP)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(PendingIntent.getActivity(context, requestCode, open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .addAction(0, context.getString(R.string.notification_mark_read),
                            PendingIntent.getBroadcast(context, requestCode, markRead,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.notify(GitHubNotifier.TAG, requestCode, builder.build());
        }

        // through the login check, like the shortcuts
        Intent inbox = new Intent(context, BrowserFilterActivity.class)
                .setData(Uri.parse(BrowserFilterActivity.NOTIFICATIONS))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        NotificationCompat.Builder summary = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.notifications))
                .setContentText(context.getResources().getQuantityString(R.plurals.github_notifications_unread,
                        unread, unread))
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, SUMMARY_ID, inbox,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.notify(GitHubNotifier.TAG, SUMMARY_ID, summary.build());
    }
}
