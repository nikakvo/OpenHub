package com.thirtydegreesray.openhub.ui.widget;

import android.app.Activity;
import android.app.ProgressDialog;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.model.UploadItem;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.ContentFiles;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.dmoral.toasty.Toasty;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * File actions shared by the Files tab and the file viewer: rename / move, delete and
 * upload, each asking for the commit (message, branch, pull request) like github.com.
 */
public final class RepoFileActions {

    public interface Done {
        void onDone();
    }

    private RepoFileActions() {
    }

    public static void rename(@NonNull Activity activity, @NonNull String owner, @NonNull String repo,
                              @NonNull String branch, @NonNull String path, @NonNull String blobSha,
                              @NonNull Done done) {
        TextInputDialog.show(activity, R.string.file_rename_title, R.string.file_path, path, true, text -> {
            String newPath = text.trim();
            while (newPath.startsWith("/")) newPath = newPath.substring(1);
            if (newPath.isEmpty() || newPath.equals(path) || newPath.endsWith("/")) return;
            final String target = newPath;
            CommitDialog.show(activity, branch,
                    activity.getString(R.string.commit_default_rename, path, target), options -> {
                        List<RepoCommitter.Change> changes = new ArrayList<>();
                        changes.add(RepoCommitter.Change.moved(target, blobSha, path));
                        changes.add(RepoCommitter.Change.delete(path));
                        run(activity, owner, repo, branch, changes, options, done);
                    });
        });
    }

    public static void delete(@NonNull Activity activity, @NonNull String owner, @NonNull String repo,
                              @NonNull String branch, @NonNull String path, @NonNull Done done) {
        CommitDialog.show(activity, branch, activity.getString(R.string.commit_default_delete, path),
                options -> {
                    List<RepoCommitter.Change> changes = new ArrayList<>();
                    changes.add(RepoCommitter.Change.delete(path));
                    run(activity, owner, repo, branch, changes, options, done);
                });
    }

    /**
     * @param dir      folder the files go to ("" for the top folder)
     * @param existing names already in that folder (to ask before replacing)
     */
    public static void upload(@NonNull Activity activity, @NonNull String owner, @NonNull String repo,
                              @NonNull String branch, @NonNull String dir, @NonNull List<UploadItem> items,
                              @NonNull Set<String> existing, @NonNull Done done) {
        if (items.isEmpty()) return;
        Set<String> names = new HashSet<>();
        List<String> replaced = new ArrayList<>();
        for (UploadItem item : items) {
            if (item.getSize() > RepoCommitter.MAX_FILE_SIZE) {
                Toasty.error(activity, activity.getString(R.string.file_upload_too_big, item.getName(),
                        StringUtils.getSizeString(RepoCommitter.MAX_FILE_SIZE))).show();
                return;
            }
            if (!names.add(item.getName())) {
                Toasty.error(activity, activity.getString(R.string.upload_name_exists, item.getName())).show();
                return;
            }
            if (existing.contains(item.getName())) replaced.add(item.getName());
        }
        Runnable ask = () -> CommitDialog.show(activity, branch,
                activity.getResources().getQuantityString(R.plurals.commit_default_upload,
                        items.size(), items.size()), options -> {
                    List<RepoCommitter.Change> changes = new ArrayList<>();
                    String prefix = StringUtils.isBlank(dir) ? "" : dir + "/";
                    for (UploadItem item : items) {
                        changes.add(RepoCommitter.Change.file(prefix + item.getName(), Uri.parse(item.getUri())));
                    }
                    run(activity, owner, repo, branch, changes, options, () -> {
                        for (UploadItem item : items) {
                            ContentFiles.releaseReadAccess(activity, Uri.parse(item.getUri()));
                        }
                        done.onDone();
                    });
                });
        if (replaced.isEmpty()) {
            ask.run();
        } else {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.share_upload_replace_title)
                    .setMessage(activity.getString(R.string.file_upload_replace, String.join(", ", replaced)))
                    .setPositiveButton(R.string.share_upload_replace, (d, w) -> ask.run())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    /** Commits with a progress dialog; shows the result and opens a new pull request. */
    public static void run(@NonNull Activity activity, @NonNull String owner, @NonNull String repo,
                           @NonNull String branch, @NonNull List<RepoCommitter.Change> changes,
                           @NonNull RepoCommitter.Options options, @Nullable Done done) {
        ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setCancelable(false);
        dialog.setMessage(activity.getString(R.string.commit_working));
        dialog.show();
        //noinspection ResultOfMethodCallIgnored
        Single.fromCallable(() -> RepoCommitter.commit(activity.getApplicationContext(), owner, repo,
                branch, changes, options, (count, total) -> {
                    if (total > 1) {
                        activity.runOnUiThread(() -> dialog.setMessage(
                                activity.getString(R.string.commit_progress, count, total)));
                    }
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (!activity.isFinishing()) dialog.dismiss();
                    RepoChanges.markChanged();
                    Toasty.success(activity.getApplicationContext(),
                            activity.getString(R.string.commit_done, result.branch)).show();
                    if (result.pullRequestUrl != null && !activity.isFinishing()) {
                        AppOpener.launchUrl(activity, Uri.parse(result.pullRequestUrl));
                    }
                    if (done != null) done.onDone();
                }, error -> {
                    if (!activity.isFinishing()) dialog.dismiss();
                    Toasty.error(activity.getApplicationContext(), StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage()).show();
                });
    }
}
