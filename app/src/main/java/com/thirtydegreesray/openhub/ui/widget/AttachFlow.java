package com.thirtydegreesray.openhub.ui.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.http.core.FreshTokenInterceptor;
import com.thirtydegreesray.openhub.mvp.model.UploadItem;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.Attachments;
import com.thirtydegreesray.openhub.util.ContentFiles;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import es.dmoral.toasty.Toasty;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * "Attach image or video" of the issue, comment and release notes editors: checks write
 * access to the repository (GitHub refuses the upload without it), lets the user pick a
 * picture or a video, uploads it to GitHub's user attachments (see {@link Attachments})
 * and hands the Markdown to the editor. Without write access, or when GitHub refuses
 * the upload, it offers the page on github.com, where files can be attached by hand.
 * <p>
 * Create it as a field of the activity (it registers the file picker, which must happen
 * before the activity starts), then call {@link #setRepository} once the repository is known.
 */
public final class AttachFlow {

    /** The editor that receives the Markdown of an uploaded file. */
    public interface Host {
        void insertAttachment(@NonNull String markdown);
    }

    /** A screen that can attach files (the Markdown editor asks its activity). */
    public interface Owner {
        boolean canAttach();

        void attach();
    }

    private static final String[] TYPES = {"image/*", "video/*"};
    private static OkHttpClient client;

    private final BaseActivity<?> activity;
    private final Host host;
    private final ActivityResultLauncher<String[]> picker;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private String owner;
    private String repo;
    private String webUrl;
    /** Read from GitHub on the first tap; 0 = not read yet. */
    private long repositoryId;
    private boolean writeAccess;

    public AttachFlow(@NonNull BaseActivity<?> activity, @NonNull Host host) {
        this.activity = activity;
        this.host = host;
        this.picker = activity.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onPicked);
    }

    /**
     * @param webUrl the page on github.com where the user can attach files by hand (the
     *               issue, or the "new issue" page); null = the repository's issues
     */
    public void setRepository(@Nullable String owner, @Nullable String repo, @Nullable String webUrl) {
        this.owner = owner;
        this.repo = repo;
        this.webUrl = webUrl;
        this.repositoryId = 0;
    }

    public boolean isAvailable() {
        return !StringUtils.isBlank(owner) && !StringUtils.isBlank(repo);
    }

    public void dispose() {
        disposables.clear();
    }

    /** The button: checks write access on the first tap, then opens the picker. */
    public void start() {
        if (!isAvailable()) return;
        if (repositoryId != 0) {
            pickOrExplain();
            return;
        }
        activity.showProgressDialog(activity.getString(R.string.attach_checking, owner + "/" + repo));
        disposables.add(Single.fromCallable(() -> GitHubApi.call(
                        GitHubApi.service(RepoSettingsService.class).getRepo(owner, repo)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(json -> {
                    activity.dismissProgressDialog();
                    repositoryId = Json.lng(json, "id");
                    writeAccess = Attachments.canUpload(Json.bool(json, "permissions.admin"),
                            Json.bool(json, "permissions.maintain"), Json.bool(json, "permissions.push"));
                    if (repositoryId == 0) {
                        showProblem(activity.getString(R.string.attach_failed_title),
                                activity.getString(R.string.attach_no_repo_id), false);
                        return;
                    }
                    pickOrExplain();
                }, error -> {
                    activity.dismissProgressDialog();
                    showProblem(activity.getString(R.string.attach_failed_title), error.getMessage(), false);
                }));
    }

    private void pickOrExplain() {
        if (writeAccess) {
            picker.launch(TYPES);
            return;
        }
        showProblem(activity.getString(R.string.attach_no_access_title),
                activity.getString(R.string.attach_no_access, owner + "/" + repo), true);
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = activity;
        UploadItem item = ContentFiles.describe(ctx, uri);
        String mime = ctx.getContentResolver().getType(uri);
        String type = Attachments.contentType(item.getName(), mime);
        if (type == null) {
            showProblem(activity.getString(R.string.attach_failed_title),
                    activity.getString(R.string.attach_wrong_type, item.getName()), false);
            return;
        }
        if (Attachments.tooBig(type, item.getSize())) {
            showProblem(activity.getString(R.string.attach_failed_title),
                    activity.getString(Attachments.isVideo(type) ? R.string.attach_too_big_video
                            : R.string.attach_too_big_image, item.getName()), false);
            return;
        }
        String name = Attachments.fileName(item.getName(), type);
        activity.showProgressDialog(activity.getString(R.string.attach_uploading, name));
        disposables.add(Single.fromCallable(() -> upload(uri, name, type, item.getSize()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(url -> {
                    activity.dismissProgressDialog();
                    ContentFiles.keepReadAccess(activity, uri);
                    Attachments.rememberLocalCopy(url, uri.toString(), type);
                    host.insertAttachment(Attachments.markdown(name, url, type));
                    Toasty.success(activity, activity.getString(R.string.attach_done, name)).show();
                }, error -> {
                    activity.dismissProgressDialog();
                    if (error instanceof UploadRefused) {
                        UploadRefused refused = (UploadRefused) error;
                        showRefused(refused.code, refused.getMessage());
                    } else {
                        showProblem(activity.getString(R.string.attach_failed_title),
                                error.getMessage(), false);
                    }
                }));
    }

    /** The upload; returns GitHub's URL of the file. Runs on a background thread. */
    @NonNull
    private String upload(@NonNull Uri uri, @NonNull String name, @NonNull String type, long size)
            throws IOException {
        String token = AppData.INSTANCE.getAccessToken();
        if (StringUtils.isBlank(token)) throw new IOException(activity.getString(R.string.attach_signed_out));
        ContentResolver resolver = activity.getContentResolver();
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public void writeTo(@NonNull BufferedSink sink) throws IOException {
                try (InputStream in = resolver.openInputStream(uri)) {
                    if (in == null) throw new IOException(activity.getString(R.string.upload_cannot_read));
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) sink.write(buffer, 0, read);
                } catch (SecurityException e) {
                    throw new IOException(activity.getString(R.string.upload_cannot_read));
                }
            }
        };
        Request request = new Request.Builder()
                .url(Attachments.uploadUrl(repositoryId, name, type))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenHub")
                .header(AppConfig.GITHUB_API_VERSION_HEADER, AppConfig.GITHUB_API_VERSION)
                .header("Authorization", token.startsWith("Basic") ? token : "token " + token)
                .post(body)
                .build();
        try (Response response = client().newCall(request).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String message = GitHubApi.errorMessage(text);
                String retry = response.header("Retry-After");
                throw new UploadRefused(response.code(),
                        response.code() == 429 && retry != null ? retry : message);
            }
            String url = "";
            try {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                url = Json.str(json, "url");
            } catch (RuntimeException ignored) {
            }
            if (StringUtils.isBlank(url)) throw new IOException(activity.getString(R.string.attach_no_url));
            return url;
        }
    }

    private void showRefused(int code, @Nullable String message) {
        String title = activity.getString(R.string.attach_failed_title);
        switch (Attachments.problem(code, message)) {
            case NO_WRITE_ACCESS:
                showProblem(title, activity.getString(R.string.attach_refused_404, owner + "/" + repo), true);
                break;
            case NOT_ACCEPTED:
                showProblem(title, activity.getString(R.string.attach_refused_auth, code,
                        StringUtils.isBlank(message) ? "-" : message), true);
                break;
            case RATE_LIMITED:
                showProblem(title, activity.getString(R.string.attach_rate_limited,
                        StringUtils.isBlank(message) ? "-" : message), false);
                break;
            case GITHUB_MESSAGE:
                showProblem(title, activity.getString(R.string.attach_github_said, code, message), false);
                break;
            default:
                showProblem(title, "HTTP " + code, false);
                break;
        }
    }

    private void showProblem(@NonNull String title, @Nullable String message, boolean offerWeb) {
        if (activity.isFinishing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(StringUtils.isBlank(message) ? "-" : message);
        if (offerWeb) {
            builder.setPositiveButton(R.string.attach_open_web, (d, w) -> AppOpener.openInBrowser(activity,
                    StringUtils.isBlank(webUrl) ? "https://github.com/" + owner + "/" + repo + "/issues" : webUrl))
                    .setNegativeButton(R.string.close, null);
        } else {
            builder.setPositiveButton(R.string.close, null);
        }
        builder.show();
    }

    @NonNull
    private static synchronized OkHttpClient client() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    // the token of an expiring sign-in is renewed like everywhere else
                    .addInterceptor(FreshTokenInterceptor.INSTANCE)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(300, TimeUnit.SECONDS)
                    // GitHub processes the file before it answers
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    private static final class UploadRefused extends IOException {
        final int code;

        UploadRefused(int code, @Nullable String message) {
            super(message);
            this.code = code;
        }
    }
}
