package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.CommitDialog;
import com.thirtydegreesray.openhub.ui.widget.MarkdownWritePreview;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Creates or edits a text file in a repository and commits it, like "Add file /
 * Create new file" and the pencil on github.com. Changing the path renames or moves
 * the file in the same commit. Markdown files get Write / Preview.
 */
public class EditFileActivity extends BaseActivity<IBaseContract.Presenter> {

    /** Files above this size are not edited on the phone. */
    private static final int MAX_EDIT_BYTES = 512 * 1024;
    /** Longer texts are not put into the saved state (Android limits its size). */
    private static final int MAX_SAVED_CHARS = 100_000;

    /** New file in {@code dir} ("" for the top folder). */
    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      @NonNull String branch, @NonNull String dir) {
        return new Intent(context, EditFileActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("branch", branch)
                .put("dir", dir).build());
    }

    /** Edit the existing file {@code path}. */
    @NonNull
    public static Intent editIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                    @NonNull String branch, @NonNull String path) {
        return new Intent(context, EditFileActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("branch", branch)
                .put("path", path).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String branch;
    /** existing file, null for a new one */
    @AutoAccess String path;
    @AutoAccess String dir;
    /** hash and length of the content as loaded, to know whether anything changed */
    @AutoAccess int originalHash;
    @AutoAccess int originalLength;
    @AutoAccess boolean loaded;
    @AutoAccess boolean dirty;

    private TextInputLayout pathLayout;
    private TextInputEditText pathEdit;
    private TextView branchText;
    private Button commitBn;
    private ProgressBar loader;
    private MarkdownWritePreview editor;
    private TextInputEditText contentEdit;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private boolean committing;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_edit_file;
    }

    private boolean isNew() {
        return path == null;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(isNew() ? R.string.file_new : R.string.file_edit), owner + "/" + repo);
        branchText.setText(getString(R.string.file_on_branch, branch));
        contentEdit.setTypeface(Typeface.MONOSPACE);
        contentEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        contentEdit.setHorizontallyScrolling(false);
        editor.setHint(getString(R.string.file_content_hint));
        if (savedInstanceState == null) {
            if (isNew()) {
                String prefix = StringUtils.isBlank(dir) ? "" : dir + "/";
                pathEdit.setText(prefix);
                pathEdit.setSelection(prefix.length());
                pathEdit.requestFocus();
                originalHash = 0;
                originalLength = 0;
                loaded = true;
            } else {
                pathEdit.setText(path);
            }
        }
        updatePreviewTabs();
        if (!loaded) loadContent();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!loaded) return;
                pathLayout.setErrorEnabled(false);
                updatePreviewTabs();
                if (!dirty) {
                    dirty = true;
                    invalidateBack();
                }
            }
        };
        pathEdit.addTextChangedListener(watcher);
        contentEdit.addTextChangedListener(watcher);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        if (editor != null) editor.dispose();
        super.onDestroy();
    }

    @NonNull
    private String pathText() {
        String text = pathEdit.getText() == null ? "" : pathEdit.getText().toString().trim();
        while (text.startsWith("/")) text = text.substring(1);
        return text;
    }

    private void updatePreviewTabs() {
        String lower = pathText().toLowerCase(Locale.ROOT);
        editor.setPreviewAvailable(lower.endsWith(".md") || lower.endsWith(".markdown"));
    }

    // ---------------------------------------------------------------------------------
    // Load
    // ---------------------------------------------------------------------------------

    private void loadContent() {
        loader.setVisibility(View.VISIBLE);
        commitBn.setEnabled(false);
        disposables.add(Single.fromCallable(() -> {
            JsonObject file = GitHubApi.call(GitHubApi.service(GitDataService.class)
                    .getContent(owner, repo, RepoCommitter.encode(path), branch));
            if (file == null || !"file".equals(string(file, "type"))) {
                throw new java.io.IOException(getString(R.string.file_not_a_file));
            }
            if (file.get("size").getAsLong() > MAX_EDIT_BYTES || !"base64".equals(string(file, "encoding"))) {
                throw new java.io.IOException(getString(R.string.file_too_large));
            }
            byte[] bytes = Base64.decode(string(file, "content"), Base64.DEFAULT);
            for (byte b : bytes) {
                if (b == 0) throw new java.io.IOException(getString(R.string.file_binary));
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(text -> {
                    loader.setVisibility(View.GONE);
                    originalHash = text.hashCode();
                    originalLength = text.length();
                    contentEdit.setSaveEnabled(text.length() <= MAX_SAVED_CHARS);
                    contentEdit.setText(text);
                    loaded = true;
                    commitBn.setEnabled(true);
                }, error -> {
                    loader.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.file_cannot_edit)
                            .setMessage(StringUtils.isBlank(error.getMessage()) ? error.toString()
                                    : error.getMessage())
                            .setPositiveButton(R.string.ok, (d, w) -> finish())
                            .setOnCancelListener(d -> finish())
                            .show();
                }));
    }

    @Nullable
    private static String string(@NonNull JsonObject object, @NonNull String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    // ---------------------------------------------------------------------------------
    // Commit
    // ---------------------------------------------------------------------------------

    private void onCommitClick() {
        if (!loaded || committing) return;
        String newPath = pathText();
        if (newPath.isEmpty() || newPath.endsWith("/")) {
            pathLayout.setError(getString(R.string.file_name_required));
            pathEdit.requestFocus();
            return;
        }
        if (newPath.contains("//") || newPath.contains("\\") || newPath.startsWith(".git/")
                || newPath.equals(".git")) {
            pathLayout.setError(getString(R.string.file_name_invalid));
            return;
        }
        String text = contentEdit.getText() == null ? "" : contentEdit.getText().toString();
        boolean renamed = !isNew() && !newPath.equals(path);
        boolean unchanged = text.length() == originalLength && text.hashCode() == originalHash;
        if (!isNew() && !renamed && unchanged) {
            showInfoToast(getString(R.string.file_no_changes));
            return;
        }
        String message;
        if (isNew()) message = getString(R.string.commit_default_create, newPath);
        else if (renamed && unchanged) message = getString(R.string.commit_default_rename, path, newPath);
        else message = getString(R.string.commit_default_update, newPath);

        final List<RepoCommitter.Change> changes = new ArrayList<>();
        if (renamed) {
            changes.add(RepoCommitter.Change.textMoved(newPath, text, path));
            changes.add(RepoCommitter.Change.delete(path));
        } else {
            changes.add(RepoCommitter.Change.text(newPath, text));
        }
        CommitDialog.show(this, branch, message, options -> commit(changes, options));
    }

    private void commit(@NonNull List<RepoCommitter.Change> changes, @NonNull RepoCommitter.Options options) {
        committing = true;
        invalidateBack();
        commitBn.setEnabled(false);
        showProgressDialog(getString(R.string.commit_working));
        disposables.add(Single.fromCallable(() ->
                RepoCommitter.commit(getApplicationContext(), owner, repo, branch, changes, options, null))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    dismissProgressDialog();
                    RepoChanges.markChanged();
                    dirty = false;
                    showSuccessToast(getString(R.string.commit_done, result.branch));
                    if (result.pullRequestUrl != null) {
                        AppOpener.launchUrl(this, Uri.parse(result.pullRequestUrl));
                    }
                    setResult(RESULT_OK);
                    finish();
                }, error -> {
                    dismissProgressDialog();
                    committing = false;
                    commitBn.setEnabled(true);
                    invalidateBack();
                    showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString()
                            : error.getMessage());
                }));
    }

    // ---------------------------------------------------------------------------------
    // Back
    // ---------------------------------------------------------------------------------

    @Override
    protected boolean interceptsBack() {
        return super.interceptsBack() || (dirty && !committing);
    }

    @Override
    protected void handleBack() {
        super.handleBack();
        if (!dirty || committing) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.release_discard_title)
                .setMessage(R.string.file_discard_message)
                .setPositiveButton(R.string.release_discard, (d, w) -> {
                    dirty = false;
                    invalidateBack();
                    finish();
                })
                .setNegativeButton(R.string.release_keep_editing, null)
                .show();
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        pathLayout = root.findViewById(R.id.path_layout);
        pathEdit = root.findViewById(R.id.path_edit);
        branchText = root.findViewById(R.id.branch_text);
        commitBn = root.findViewById(R.id.commit_bn);
        loader = root.findViewById(R.id.loader);
        editor = new MarkdownWritePreview(root, owner + "/" + repo);
        contentEdit = editor.getEditText();
        ViewUtils.setOnClick(root, R.id.commit_bn, v -> onCommitClick());
    }
}
