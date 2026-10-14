package com.thirtydegreesray.openhub.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.util.GitHubNotifier;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * "Mark as read" on a GitHub notification posted by {@link NotificationCheckWorker}:
 * marks the thread read on GitHub and removes the Android notification.
 * "Download" on a finished workflow run starts the artifact download.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    static final String ACTION_MARK_READ = "io.github.nikakvo.openhub.MARK_NOTIFICATION_READ";
    static final String EXTRA_THREAD = "thread";
    /** "Download" on a finished workflow run ({@link ActionsCheckWorker}). */
    static final String ACTION_DOWNLOAD_ARTIFACT = "io.github.nikakvo.openhub.DOWNLOAD_ARTIFACT";
    static final String EXTRA_URL = "url";
    static final String EXTRA_FILE_NAME = "file_name";
    static final String EXTRA_NOTIFICATION_ID = "notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_DOWNLOAD_ARTIFACT.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            if (StringUtils.isBlank(url) || StringUtils.isBlank(fileName)) return;
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(ActionsCheckWorker.TAG, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
            }
            // the download has its own progress notification
            DownloadService.start(context, url, fileName);
            return;
        }
        if (!ACTION_MARK_READ.equals(intent.getAction())) return;
        String thread = intent.getStringExtra(EXTRA_THREAD);
        if (StringUtils.isBlank(thread)) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(GitHubNotifier.TAG, thread.hashCode());

        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                String token = ReleaseCheckWorker.loadToken();
                if (StringUtils.isBlank(token)) return;
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .url(AppConfig.GITHUB_API_BASE_URL + "notifications/threads/" + thread)
                        .header("Accept", "application/vnd.github+json")
                        .header("Authorization", token.startsWith("Basic") ? token : "token " + token)
                        .patch(RequestBody.create("", MediaType.get("application/json")))
                        .build();
                try (Response ignored = client.newCall(request).execute()) {
                    // 205 Reset Content on success; nothing else to do
                }
            } catch (Exception ignored) {
            } finally {
                pending.finish();
            }
        }).start();
    }
}
