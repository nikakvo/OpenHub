package com.thirtydegreesray.openhub.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import es.dmoral.toasty.Toasty;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Built-in downloader that replaces the system DownloadManager.
 *
 * The system DownloadManager was started with setAllowedOverRoaming(false), so every
 * download stalled on a roaming SIM until Wi-Fi came back, and some ROMs additionally
 * hold large files "until Wi-Fi". This service downloads over whatever network is
 * currently active (Wi-Fi, mobile, roaming, VPN, Ethernet) with no network policy,
 * survives flaky connections by resuming with HTTP Range requests, and saves into the
 * public Download folder.
 */
public class DownloadService extends Service {

    private static final String ACTION_START = "openhub.download.START";
    private static final String ACTION_CANCEL = "openhub.download.CANCEL";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_FILE_NAME = "fileName";
    private static final String EXTRA_ID = "id";

    private static final String CHANNEL_ID = "downloads";
    private static final int FOREGROUND_ID = 0x0D0;

    private static final int MAX_PARALLEL = 3;
    private static final int MAX_RETRIES = 10;
    private static final long MAX_BACKOFF_MS = 30_000;
    private static final long PROGRESS_INTERVAL_MS = 500;
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();
    private ExecutorService executor;
    private OkHttpClient client;
    private NotificationManager notificationManager;
    private Handler mainHandler;

    public static void start(@NonNull Context context, @NonNull String url, @NonNull String fileName) {
        Intent intent = new Intent(context, DownloadService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FILE_NAME, fileName);
        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        executor = Executors.newFixedThreadPool(MAX_PARALLEL);
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        createChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Every startForegroundService() call must be answered with startForeground().
        enterForeground();
        if (intent == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            Task task = tasks.get(intent.getIntExtra(EXTRA_ID, -1));
            if (task != null) task.cancel();
            stopIfIdle();
        } else if (ACTION_START.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            if (StringUtils.isBlank(url) || StringUtils.isBlank(fileName)) {
                toast(getString(R.string.download_empty_tip), false);
                stopIfIdle();
            } else {
                Task task = new Task(NEXT_ID.getAndIncrement(), url, sanitizeFileName(fileName));
                tasks.put(task.id, task);
                updateForeground();
                toast(getString(R.string.download_start), true);
                executor.execute(task);
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        for (Task task : tasks.values()) task.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------------------------
    // Foreground / notifications
    // ---------------------------------------------------------------------------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW);
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
        int active = tasks.size();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getResources().getQuantityString(R.plurals.downloads_active,
                        Math.max(active, 1), Math.max(active, 1)))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateForeground() {
        if (!tasks.isEmpty()) {
            notificationManager.notify(FOREGROUND_ID, buildForegroundNotification());
        }
    }

    private synchronized void stopIfIdle() {
        if (tasks.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        }
    }

    private int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void notifyProgress(Task task, String text) {
        Intent cancel = new Intent(this, DownloadService.class)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, task.id);
        PendingIntent cancelPi = PendingIntent.getService(this, task.id, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(task.displayName)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.cancel), cancelPi);
        if (task.total > 0) {
            int percent = (int) (task.written * 100 / task.total);
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        notificationManager.notify(task.id, builder.build());
    }

    private void notifyFinished(Task task, boolean success, String text, @Nullable Uri openUri) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(success ? android.R.drawable.stat_sys_download_done
                        : android.R.drawable.stat_notify_error)
                .setContentTitle(task.displayName)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (success && openUri != null) {
            Intent open = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(openUri, task.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openPi = PendingIntent.getActivity(this, task.id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
            builder.setContentIntent(openPi);
            boolean isApk = "application/vnd.android.package-archive".equals(task.mimeType);
            builder.addAction(0, getString(isApk ? R.string.install : R.string.open), openPi);

            // Share the file, e.g. a module zip to a root manager or a chat.
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(task.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, openUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri(task.displayName, openUri));
            Intent chooser = Intent.createChooser(send, task.displayName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            builder.addAction(0, getString(R.string.share), PendingIntent.getActivity(this,
                    task.id + 1_000_000, chooser, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()));
        }
        notificationManager.notify(task.id, builder.build());
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

    private void finishTask(Task task) {
        tasks.remove(task.id);
        mainHandler.post(() -> {
            updateForeground();
            stopIfIdle();
        });
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    @NonNull
    private static String sanitizeFileName(@NonNull String name) {
        String clean = name.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return clean.isEmpty() ? "download" : clean;
    }

    @NonNull
    private static String mimeTypeOf(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return "application/gzip";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower.substring(dot + 1));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private static boolean needsGitHubToken(@NonNull String url) {
        Uri uri = Uri.parse(url);
        return "https".equalsIgnoreCase(uri.getScheme())
                && "api.github.com".equalsIgnoreCase(uri.getHost());
    }

    private static class FatalDownloadException extends IOException {
        FatalDownloadException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------------------------
    // Output targets: MediaStore on Android 10+, plain file below
    // ---------------------------------------------------------------------------------

    private interface Target {
        OutputStream open(boolean append) throws IOException;
        void commit();
        void discard();
        Uri openUri();
        String name();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private class MediaStoreTarget implements Target {
        private final Uri uri;
        private final String name;

        MediaStoreTarget(String fileName, String mimeType) throws IOException {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri inserted = getContentResolver().insert(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (inserted == null) throw new FatalDownloadException("Cannot create file in Download");
            uri = inserted;
            // MediaStore renames on collision ("file (1).zip"), report the real name.
            String realName = fileName;
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && !StringUtils.isBlank(c.getString(0))) {
                    realName = c.getString(0);
                }
            } catch (Exception ignored) {
            }
            name = realName;
        }

        @Override
        public OutputStream open(boolean append) throws IOException {
            OutputStream out = getContentResolver().openOutputStream(uri, append ? "wa" : "wt");
            if (out == null) throw new IOException("Cannot open output stream");
            return out;
        }

        @Override
        public void commit() {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        }

        @Override
        public void discard() {
            try {
                getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
        }

        @Override
        public Uri openUri() {
            return uri;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private class LegacyFileTarget implements Target {
        private final File file;

        LegacyFileTarget(String fileName) throws IOException {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new FatalDownloadException("Cannot create " + dir);
            }
            File candidate = new File(dir, fileName);
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";
            for (int i = 1; candidate.exists(); i++) {
                candidate = new File(dir, base + " (" + i + ")" + ext);
            }
            file = candidate;
        }

        @Override
        public OutputStream open(boolean append) throws IOException {
            return new FileOutputStream(file, append);
        }

        @Override
        public void commit() {
            MediaScannerConnection.scanFile(getApplicationContext(),
                    new String[]{file.getAbsolutePath()}, null, null);
        }

        @Override
        public void discard() {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }

        @Override
        public Uri openUri() {
            try {
                return FileProvider.getUriForFile(getApplicationContext(),
                        getPackageName() + ".fileProvider", file);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String name() {
            return file.getName();
        }
    }

    // ---------------------------------------------------------------------------------
    // Download task
    // ---------------------------------------------------------------------------------

    private class Task implements Runnable {
        final int id;
        final String url;
        final String mimeType;
        volatile String displayName;
        volatile boolean cancelled;
        volatile Call call;
        long written;
        long total = -1;
        private long lastNotify;

        Task(int id, String url, String fileName) {
            this.id = id;
            this.url = url;
            this.displayName = fileName;
            this.mimeType = mimeTypeOf(fileName);
        }

        void cancel() {
            cancelled = true;
            Call c = call;
            if (c != null) c.cancel();
        }

        @Override
        public void run() {
            Target target = null;
            try {
                target = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? new MediaStoreTarget(displayName, mimeType)
                        : new LegacyFileTarget(displayName);
                displayName = target.name();
                notifyProgress(this, getString(R.string.downloading));
                download(target);
                target.commit();
                notifyFinished(this, true, getString(R.string.download_complete_size,
                        Formatter.formatFileSize(DownloadService.this, written)), target.openUri());
                toast(getString(R.string.download_complete) + "\nDownload/" + displayName, true);
            } catch (Exception e) {
                if (target != null) target.discard();
                if (cancelled) {
                    notificationManager.cancel(id);
                    toast(getString(R.string.download_cancel), false);
                } else {
                    String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    notifyFinished(this, false, getString(R.string.download_failed) + ": " + reason, null);
                    toast(getString(R.string.download_failed) + ": " + reason, false);
                }
            } finally {
                finishTask(this);
            }
        }

        private void download(Target target) throws IOException, InterruptedException {
            int failures = 0;
            while (true) {
                if (cancelled) throw new IOException("cancelled");
                long before = written;
                try {
                    transfer(target);
                    return;
                } catch (FatalDownloadException e) {
                    throw e;
                } catch (IOException e) {
                    if (cancelled) throw e;
                    // Any progress means the link works, so the retry budget starts over.
                    failures = written > before ? 1 : failures + 1;
                    if (failures > MAX_RETRIES) throw e;
                    long backoff = Math.min(MAX_BACKOFF_MS, 1000L << Math.min(failures, 5));
                    notifyProgress(this, getString(R.string.download_retrying,
                            failures, MAX_RETRIES, backoff / 1000));
                    long until = System.currentTimeMillis() + backoff;
                    while (System.currentTimeMillis() < until) {
                        if (cancelled) throw new IOException("cancelled");
                        Thread.sleep(250);
                    }
                }
            }
        }

        private void transfer(Target target) throws IOException {
            Request.Builder rb = new Request.Builder().url(url).header("User-Agent", "OpenHub");
            String token = AppData.INSTANCE.getAccessToken();
            // started from a notification while the app was not running
            if (StringUtils.isBlank(token)) token = ReleaseCheckWorker.loadToken();
            if (needsGitHubToken(url) && !StringUtils.isBlank(token)) {
                // OkHttp drops this header automatically on a redirect to another host.
                rb.header("Authorization", token.startsWith("Basic") ? token : "token " + token);
            }
            if (needsGitHubToken(url) && url.contains("/releases/assets/")) {
                // API address of a release asset (e.g. of a draft): ask for the file itself
                rb.header("Accept", "application/octet-stream");
            }
            if (written > 0) rb.header("Range", "bytes=" + written + "-");

            call = client.newCall(rb.build());
            try (Response response = call.execute()) {
                int code = response.code();
                if (code == 416 && written > 0 && total > 0 && written >= total) return;
                if (!response.isSuccessful()) {
                    if (code == 408 || code == 429 || code >= 500) {
                        throw new IOException("HTTP " + code);
                    }
                    throw new FatalDownloadException("HTTP " + code);
                }
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Empty response");

                boolean append = written > 0 && code == 206;
                if (!append) written = 0;
                long length = body.contentLength();
                total = length < 0 ? -1 : (append ? written + length : length);

                byte[] buffer = new byte[BUFFER_SIZE];
                try (InputStream in = body.byteStream(); OutputStream out = target.open(append)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (cancelled) throw new IOException("cancelled");
                        out.write(buffer, 0, read);
                        written += read;
                        long now = System.currentTimeMillis();
                        if (now - lastNotify >= PROGRESS_INTERVAL_MS) {
                            lastNotify = now;
                            notifyProgress(this, progressText());
                        }
                    }
                    out.flush();
                }
                if (total > 0 && written < total) {
                    throw new IOException("Connection closed at " + written + "/" + total);
                }
            } finally {
                call = null;
            }
        }

        private String progressText() {
            String done = Formatter.formatShortFileSize(DownloadService.this, written);
            if (total > 0) {
                return done + " / " + Formatter.formatShortFileSize(DownloadService.this, total);
            }
            return done;
        }
    }
}
