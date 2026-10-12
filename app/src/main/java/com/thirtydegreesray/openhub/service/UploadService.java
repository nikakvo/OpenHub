package com.thirtydegreesray.openhub.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.Formatter;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.UploadItem;
import com.thirtydegreesray.openhub.ui.activity.ReleaseInfoActivity;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ContentFiles;
import com.thirtydegreesray.openhub.util.ReleaseChanges;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import es.dmoral.toasty.Toasty;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/**
 * Uploads release assets to uploads.github.com in the background.
 *
 * A job is a set of files for one release. Files go up one after another with a
 * progress notification; network errors are retried with backoff, and a half-uploaded
 * asset left behind by a broken connection is removed before the next attempt
 * (GitHub refuses a second asset with the same name). With "publish" set, the draft
 * release is published only after every file arrived, which is the only order that
 * works with immutable releases (assets cannot change after publishing).
 */
public class UploadService extends Service {

    private static final String ACTION_START = "openhub.upload.START";
    private static final String ACTION_CANCEL = "openhub.upload.CANCEL";
    private static final String EXTRA_ID = "id";

    private static final String CHANNEL_ID = "uploads";
    private static final int FOREGROUND_ID = 0x0D1;
    private static final int MAX_RETRIES = 5;
    private static final long MAX_BACKOFF_MS = 30_000;
    private static final long PROGRESS_INTERVAL_MS = 500;
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final AtomicInteger NEXT_ID = new AtomicInteger(5000);

    private final Map<Integer, Job> jobs = new ConcurrentHashMap<>();
    private ExecutorService executor;
    private OkHttpClient client;
    private NotificationManager notificationManager;
    private Handler mainHandler;

    /**
     * @param publish    publish the (draft) release once all files are uploaded
     * @param makeLatest "true" / "false" for the publish call, null to leave it to GitHub
     */
    public static void start(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                             @NonNull Release release, @NonNull ArrayList<UploadItem> items,
                             boolean publish, @Nullable String makeLatest) {
        Intent intent = new Intent(context, UploadService.class)
                .setAction(ACTION_START)
                .putExtras(BundleHelper.builder()
                        .put("owner", owner)
                        .put("repo", repo)
                        .put("releaseId", release.getId())
                        .put("tag", release.getTagName())
                        .put("uploadUrl", release.getUploadUrl())
                        .putParcelableList("items", items)
                        .put("publish", publish)
                        .put("makeLatest", makeLatest)
                        .build());
        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // One upload at a time: parallel uploads only split the same uplink.
        executor = Executors.newSingleThreadExecutor();
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                // GitHub processes the file before it answers
                .readTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        createChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        enterForeground();
        if (intent == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            Job job = jobs.get(intent.getIntExtra(EXTRA_ID, -1));
            if (job != null) job.cancel();
            stopIfIdle();
        } else if (ACTION_START.equals(intent.getAction())) {
            Job job = Job.from(NEXT_ID.getAndIncrement(), intent);
            if (job == null) {
                stopIfIdle();
            } else {
                jobs.put(job.id, job);
                updateForeground();
                notifyQueued(job);
                executor.execute(() -> runJob(job));
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        for (Job job : jobs.values()) job.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------------------------
    // Job
    // ---------------------------------------------------------------------------------

    private static class Job {
        final int id;
        String owner;
        String repo;
        String releaseId;
        String tag;
        String uploadUrl;
        ArrayList<UploadItem> items;
        boolean publish;
        String makeLatest;

        volatile boolean cancelled;
        volatile Call call;
        int index;
        long written;
        long total = -1;
        long lastNotify;

        Job(int id) {
            this.id = id;
        }

        @Nullable
        @SuppressWarnings("deprecation")
        static Job from(int id, @NonNull Intent intent) {
            Job job = new Job(id);
            job.owner = intent.getStringExtra("owner");
            job.repo = intent.getStringExtra("repo");
            job.releaseId = intent.getStringExtra("releaseId");
            job.tag = intent.getStringExtra("tag");
            job.uploadUrl = intent.getStringExtra("uploadUrl");
            job.items = intent.getParcelableArrayListExtra("items");
            job.publish = intent.getBooleanExtra("publish", false);
            job.makeLatest = intent.getStringExtra("makeLatest");
            if (StringUtils.isBlank(job.owner) || StringUtils.isBlank(job.repo)
                    || StringUtils.isBlank(job.releaseId) || job.items == null) {
                return null;
            }
            if (StringUtils.isBlank(job.uploadUrl)) {
                job.uploadUrl = "https://uploads.github.com/repos/" + job.owner + "/" + job.repo
                        + "/releases/" + job.releaseId + "/assets";
            }
            return job;
        }

        String label() {
            return StringUtils.isBlank(tag) ? owner + "/" + repo : owner + "/" + repo + " " + tag;
        }

        void cancel() {
            cancelled = true;
            Call c = call;
            if (c != null) c.cancel();
        }
    }

    private static class CancelledException extends IOException {
        CancelledException() {
            super("cancelled");
        }
    }

    /** An error that a retry cannot fix (bad name, no permission, immutable release...). */
    private static class FatalUploadException extends IOException {
        FatalUploadException(String message) {
            super(message);
        }
    }

    private void runJob(@NonNull Job job) {
        List<String> failures = new ArrayList<>();
        int uploaded = 0;
        try {
            for (job.index = 0; job.index < job.items.size(); job.index++) {
                if (job.cancelled) throw new CancelledException();
                UploadItem item = job.items.get(job.index);
                try {
                    uploadWithRetry(job, item);
                    uploaded++;
                } catch (CancelledException e) {
                    throw e;
                } catch (Exception e) {
                    failures.add(item.getName() + ": " + reason(e));
                }
            }

            String published = null;
            if (job.publish) {
                if (failures.isEmpty()) {
                    try {
                        publish(job);
                        published = getString(R.string.upload_release_published);
                    } catch (Exception e) {
                        failures.add(getString(R.string.upload_publish_failed, reason(e)));
                    }
                } else {
                    published = getString(R.string.upload_kept_draft);
                }
            }
            notifyFinished(job, uploaded, failures, published);
        } catch (CancelledException e) {
            notificationManager.cancel(job.id);
            toast(getString(R.string.upload_cancelled), false);
        } finally {
            for (UploadItem item : job.items) {
                ContentFiles.releaseReadAccess(this, Uri.parse(item.getUri()));
            }
            if (uploaded > 0 || job.publish) ReleaseChanges.markChanged();
            jobs.remove(job.id);
            mainHandler.post(() -> {
                updateForeground();
                stopIfIdle();
            });
        }
    }

    private void uploadWithRetry(@NonNull Job job, @NonNull UploadItem item)
            throws IOException, InterruptedException {
        File temp = null;
        try {
            long size = item.getSize();
            if (size < 0) {
                // GitHub needs Content-Length; the provider did not say, so copy first.
                temp = copyToCache(job, item);
                size = temp.length();
            }
            int failures = 0;
            while (true) {
                if (job.cancelled) throw new CancelledException();
                try {
                    if (failures > 0) removeBrokenAsset(job, item.getName());
                    upload(job, item, size, temp);
                    return;
                } catch (CancelledException | FatalUploadException e) {
                    if (job.cancelled) removeBrokenAssetQuietly(job, item.getName());
                    throw e;
                } catch (IOException e) {
                    if (job.cancelled) {
                        removeBrokenAssetQuietly(job, item.getName());
                        throw new CancelledException();
                    }
                    failures++;
                    if (failures > MAX_RETRIES) {
                        removeBrokenAssetQuietly(job, item.getName());
                        throw e;
                    }
                    long backoff = Math.min(MAX_BACKOFF_MS, 1000L << Math.min(failures, 5));
                    notifyProgress(job, item, getString(R.string.upload_retrying,
                            failures, MAX_RETRIES, backoff / 1000));
                    long until = System.currentTimeMillis() + backoff;
                    while (System.currentTimeMillis() < until) {
                        if (job.cancelled) throw new CancelledException();
                        Thread.sleep(250);
                    }
                }
            }
        } finally {
            if (temp != null) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private void upload(@NonNull Job job, @NonNull UploadItem item, long size, @Nullable File source)
            throws IOException {
        HttpUrl url = HttpUrl.parse(job.uploadUrl);
        if (url == null) throw new FatalUploadException("Bad upload URL");
        url = url.newBuilder().addQueryParameter("name", item.getName()).build();

        job.written = 0;
        job.total = size;
        notifyProgress(job, item, progressText(job));

        MediaType mediaType = MediaType.parse(mimeTypeOf(item.getName()));
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public void writeTo(@NonNull BufferedSink sink) throws IOException {
                job.written = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                try (InputStream in = open(item, source)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (job.cancelled) throw new CancelledException();
                        sink.write(buffer, 0, read);
                        job.written += read;
                        long now = System.currentTimeMillis();
                        if (now - job.lastNotify >= PROGRESS_INTERVAL_MS) {
                            job.lastNotify = now;
                            notifyProgress(job, item, progressText(job));
                        }
                    }
                }
                if (job.written != size) {
                    throw new FatalUploadException(getString(R.string.upload_size_changed));
                }
            }
        };

        Request request = authorized(new Request.Builder().url(url).post(body)).build();
        job.call = client.newCall(request);
        try (Response response = job.call.execute()) {
            int code = response.code();
            if (response.isSuccessful()) {
                notifyProgress(job, item, getString(R.string.upload_processing));
                return;
            }
            String message = errorText(response);
            if (code == 408 || code == 429 || code >= 500) throw new IOException(message);
            if (code == 422 && message.contains("already exists")) {
                if (isAlreadyUploaded(job, item.getName(), size)) return;
                throw new FatalUploadException(getString(R.string.upload_name_exists, item.getName()));
            }
            throw new FatalUploadException(message);
        } finally {
            job.call = null;
        }
    }

    @NonNull
    private InputStream open(@NonNull UploadItem item, @Nullable File source) throws IOException {
        if (source != null) return new FileInputStream(source);
        try {
            InputStream in = getContentResolver().openInputStream(Uri.parse(item.getUri()));
            if (in == null) throw new FatalUploadException(getString(R.string.upload_cannot_read));
            return in;
        } catch (SecurityException e) {
            throw new FatalUploadException(getString(R.string.upload_cannot_read));
        }
    }

    @NonNull
    private File copyToCache(@NonNull Job job, @NonNull UploadItem item) throws IOException {
        notifyProgress(job, item, getString(R.string.upload_preparing));
        File dir = new File(getCacheDir(), "uploads");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File file = new File(dir, job.id + "-" + job.index);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = open(item, null); OutputStream out = new FileOutputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (job.cancelled) throw new CancelledException();
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw e;
        }
        return file;
    }

    // ---------------------------------------------------------------------------------
    // GitHub API (plain OkHttp: the service may outlive the screens that started it)
    // ---------------------------------------------------------------------------------

    @NonNull
    private Request.Builder authorized(@NonNull Request.Builder builder) {
        String token = AppData.INSTANCE.getAccessToken();
        if (StringUtils.isBlank(token)) token = ReleaseCheckWorker.loadToken();
        builder.header("Accept", "application/vnd.github+json").header("User-Agent", "OpenHub");
        if (!StringUtils.isBlank(token)) {
            builder.header("Authorization", token.startsWith("Basic") ? token : "token " + token);
        }
        return builder;
    }

    @NonNull
    private String api(@NonNull Job job, @NonNull String path) {
        return AppConfig.GITHUB_API_BASE_URL + "repos/" + job.owner + "/" + job.repo + "/" + path;
    }

    /** Assets of the release by name; the upload may have half-created one. */
    @Nullable
    private JsonObject findAsset(@NonNull Job job, @NonNull String name) throws IOException {
        Request request = authorized(new Request.Builder()
                .url(api(job, "releases/" + job.releaseId + "/assets?per_page=100"))).build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) throw new IOException(errorText(response));
            JsonArray assets = JsonParser.parseString(body.string()).getAsJsonArray();
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                if (name.equals(asset.get("name").getAsString())) return asset;
            }
            return null;
        }
    }

    private boolean isAlreadyUploaded(@NonNull Job job, @NonNull String name, long size) {
        try {
            JsonObject asset = findAsset(job, name);
            return asset != null && "uploaded".equals(asset.get("state").getAsString())
                    && asset.get("size").getAsLong() == size;
        } catch (Exception e) {
            return false;
        }
    }

    /** Deletes an asset with this name that never finished uploading. */
    private void removeBrokenAsset(@NonNull Job job, @NonNull String name) throws IOException {
        JsonObject asset = findAsset(job, name);
        if (asset == null || "uploaded".equals(asset.get("state").getAsString())) return;
        Request request = authorized(new Request.Builder()
                .url(api(job, "releases/assets/" + asset.get("id").getAsString())).delete()).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IOException(errorText(response));
            }
        }
    }

    private void removeBrokenAssetQuietly(@NonNull Job job, @NonNull String name) {
        try {
            removeBrokenAsset(job, name);
        } catch (Exception ignored) {
        }
    }

    private void publish(@NonNull Job job) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("draft", false);
        if (job.makeLatest != null) body.addProperty("make_latest", job.makeLatest);
        Request request = authorized(new Request.Builder()
                .url(api(job, "releases/" + job.releaseId))
                .patch(RequestBody.create(body.toString(), MediaType.parse("application/json"))))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException(errorText(response));
        }
    }

    @NonNull
    private static String errorText(@NonNull Response response) {
        String detail = null;
        try {
            ResponseBody body = response.body();
            if (body != null) detail = GitHubApi.errorMessage(body.string());
        } catch (Exception ignored) {
        }
        return "HTTP " + response.code() + (StringUtils.isBlank(detail) ? "" : ": " + detail);
    }

    // ---------------------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.upload_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void enterForeground() {
        Notification notification = buildForegroundNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private Notification buildForegroundNotification() {
        int files = 0;
        for (Job job : jobs.values()) files += job.items.size() - job.index;
        files = Math.max(files, 1);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getResources().getQuantityString(R.plurals.uploads_active, files, files))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateForeground() {
        if (!jobs.isEmpty()) notificationManager.notify(FOREGROUND_ID, buildForegroundNotification());
    }

    private synchronized void stopIfIdle() {
        if (jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private PendingIntent cancelIntent(@NonNull Job job) {
        Intent cancel = new Intent(this, UploadService.class)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, job.id);
        return PendingIntent.getService(this, job.id, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openReleaseIntent(@NonNull Job job) {
        Intent open = new Intent(this, ReleaseInfoActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("owner", job.owner)
                        .put("repoName", job.repo)
                        .put("releaseId", job.releaseId)
                        .build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, job.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void notifyQueued(@NonNull Job job) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(job.label())
                .setContentText(getString(R.string.upload_waiting))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true)
                .addAction(0, getString(R.string.cancel), cancelIntent(job));
        notificationManager.notify(job.id, builder.build());
    }

    private void notifyProgress(@NonNull Job job, @NonNull UploadItem item, @NonNull String text) {
        String title = job.items.size() > 1
                ? getString(R.string.upload_progress_title, job.index + 1, job.items.size(), item.getName())
                : item.getName();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(job.label())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.cancel), cancelIntent(job));
        if (job.total > 0) {
            builder.setProgress(100, (int) (job.written * 100 / job.total), false);
        } else {
            builder.setProgress(0, 0, true);
        }
        notificationManager.notify(job.id, builder.build());
    }

    private void notifyFinished(@NonNull Job job, int uploaded, @NonNull List<String> failures,
                                @Nullable String published) {
        boolean success = failures.isEmpty();
        StringBuilder text = new StringBuilder(getResources()
                .getQuantityString(R.plurals.upload_done, uploaded, uploaded));
        if (published != null) text.append(" ").append(published);
        for (String failure : failures) text.append("\n").append(failure);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(success ? android.R.drawable.stat_sys_upload_done
                        : android.R.drawable.stat_notify_error)
                .setContentTitle(success ? getString(R.string.upload_complete_title, job.label())
                        : getString(R.string.upload_failed_title, job.label()))
                .setContentText(text.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text.toString()))
                .setAutoCancel(true)
                .setContentIntent(openReleaseIntent(job))
                .addAction(0, getString(R.string.open_release), openReleaseIntent(job))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationManager.notify(job.id, builder.build());
        toast(success ? getString(R.string.upload_complete_title, job.label())
                : getString(R.string.upload_failed_title, job.label()), success);
    }

    private void toast(final String text, final boolean success) {
        mainHandler.post(() -> {
            if (success) {
                Toasty.success(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            } else {
                Toasty.error(getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    @NonNull
    private String progressText(@NonNull Job job) {
        String done = Formatter.formatShortFileSize(this, job.written);
        if (job.total > 0) {
            int percent = (int) (job.written * 100 / job.total);
            return done + " / " + Formatter.formatShortFileSize(this, job.total) + "  (" + percent + "%)";
        }
        return done;
    }

    @NonNull
    private static String reason(@NonNull Exception e) {
        return StringUtils.isBlank(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
    }

    @NonNull
    static String mimeTypeOf(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return "application/gzip";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower.substring(dot + 1));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }
}
