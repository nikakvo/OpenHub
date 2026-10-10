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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AuthUserDao;
import com.thirtydegreesray.openhub.ui.activity.BrowserFilterActivity;
import com.thirtydegreesray.openhub.util.ReleaseWatcher;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Checks the latest release of every watched repository and posts a notification
 * when its tag changed. The first check of a repository only records the tag.
 */
public class ReleaseCheckWorker extends Worker {

    private static final String CHANNEL_ID = "release_updates";

    public ReleaseCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        List<String> repos = ReleaseWatcher.getWatched(context);
        if (repos.isEmpty()) return Result.success();

        String token = loadToken();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        boolean networkError = false;
        for (String fullName : repos) {
            if (isStopped()) break;
            Request.Builder request = new Request.Builder()
                    .url(AppConfig.GITHUB_API_BASE_URL + "repos/" + fullName + "/releases/latest")
                    .header("Accept", "application/vnd.github+json");
            if (!StringUtils.isBlank(token)) {
                request.header("Authorization", token.startsWith("Basic") ? token : "token " + token);
            }
            try (Response response = client.newCall(request.build()).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) continue; // 404: no release yet
                JsonObject release = JsonParser.parseString(body.string()).getAsJsonObject();
                String tag = string(release, "tag_name");
                if (StringUtils.isBlank(tag)) continue;
                String lastTag = ReleaseWatcher.getLastTag(context, fullName);
                if (lastTag == null) continue; // un-watched meanwhile
                if (!lastTag.isEmpty() && !lastTag.equals(tag)) {
                    notifyRelease(context, fullName, tag, string(release, "name"),
                            string(release, "html_url"));
                }
                if (!tag.equals(lastTag)) ReleaseWatcher.setLastTag(context, fullName, tag);
            } catch (Exception e) {
                networkError = true;
            }
        }
        return networkError ? Result.retry() : Result.success();
    }

    @Nullable
    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    /** Token of the selected account, read from the database (the app may not be running). */
    @Nullable
    private static String loadToken() {
        try {
            AuthUserDao dao = AppApplication.get().getAppComponent().getDaoSession().getAuthUserDao();
            List<AuthUser> users = dao.queryBuilder()
                    .where(AuthUserDao.Properties.Selected.eq(true)).limit(1).list();
            if (users.isEmpty()) users = dao.queryBuilder().limit(1).list();
            return users.isEmpty() ? null : users.get(0).getAccessToken();
        } catch (Exception e) {
            return null;
        }
    }

    private static void notifyRelease(Context context, String fullName, String tag,
                                      @Nullable String name, @Nullable String htmlUrl) {
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
                    context.getString(R.string.release_updates_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        String text = StringUtils.isBlank(name) || name.equals(tag) ? tag : tag + " · " + name;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.release_new_title, fullName))
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        String url = StringUtils.isBlank(htmlUrl)
                ? AppConfig.GITHUB_BASE_URL + fullName + "/releases/tag/" + Uri.encode(tag) : htmlUrl;
        // opens the release inside the app (through the login check if needed)
        Intent open = new Intent(context, BrowserFilterActivity.class)
                .setData(Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.setContentIntent(PendingIntent.getActivity(context, fullName.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.notify(fullName.hashCode(), builder.build());
    }
}
