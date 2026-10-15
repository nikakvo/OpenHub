package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GistService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.MarkdownWritePreview;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GistChanges;
import com.thirtydegreesray.openhub.util.GitHubHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Writes a gist: a new gist (description, public or secret, its first file), a new file
 * of a gist, or a changed file (content and name). Markdown files have a preview.
 */
public class GistEditActivity extends BaseActivity<IBaseContract.Presenter> {

    private static final String MODE_NEW = "new";
    private static final String MODE_ADD = "add";
    private static final String MODE_EDIT = "edit";

    @NonNull
    public static Intent newGistIntent(@NonNull Context context) {
        return new Intent(context, GistEditActivity.class)
                .putExtras(BundleHelper.builder().put("mode", MODE_NEW).build());
    }

    @NonNull
    public static Intent addFileIntent(@NonNull Context context, @NonNull String gistId,
                                       @NonNull List<String> names) {
        return new Intent(context, GistEditActivity.class)
                .putExtras(BundleHelper.builder().put("mode", MODE_ADD).put("gistId", gistId)
                        .put("names", names.toArray(new String[0])).build());
    }

    @NonNull
    public static Intent editFileIntent(@NonNull Context context, @NonNull String gistId,
                                        @NonNull String fileName, @NonNull List<String> names) {
        return new Intent(context, GistEditActivity.class)
                .putExtras(BundleHelper.builder().put("mode", MODE_EDIT).put("gistId", gistId)
                        .put("fileName", fileName).put("names", names.toArray(new String[0])).build());
    }

    @AutoAccess String mode;
    @AutoAccess String gistId;
    @AutoAccess String fileName;

    private TextInputLayout descriptionLayout;
    private TextInputEditText descriptionEdit;
    private TextInputLayout filenameLayout;
    private TextInputEditText filenameEdit;
    private SwitchCompat publicSwitch;
    private Button saveBn;
    private ProgressBar loader;
    private MarkdownWritePreview editor;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<String> names = new ArrayList<>();
    private boolean dirty;
    private boolean saving;
    private boolean loading;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_gist_edit;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        String[] given = getIntent().getStringArrayExtra("names");
        if (given != null) names.addAll(Arrays.asList(given));
        boolean isNew = MODE_NEW.equals(mode);
        setToolbarTitle(getString(isNew ? R.string.gist_new
                : MODE_ADD.equals(mode) ? R.string.gist_add_file : R.string.gist_edit_file));
        descriptionLayout.setVisibility(isNew ? View.VISIBLE : View.GONE);
        publicSwitch.setVisibility(isNew ? View.VISIBLE : View.INVISIBLE);
        saveBn.setText(isNew ? R.string.gist_create : R.string.gist_save);
        saveBn.setOnClickListener(v -> save());
        editor.setPreviewAvailable(false);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (loading) return;
                dirty = true;
                invalidateBack();
                editor.setPreviewAvailable(GitHubHelper.isMarkdown(text(filenameEdit)));
            }
        };
        filenameEdit.addTextChangedListener(watcher);
        editor.getEditText().addTextChangedListener(watcher);
        descriptionEdit.addTextChangedListener(watcher);

        if (MODE_EDIT.equals(mode)) loadFile();
        else filenameEdit.requestFocus();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        editor.dispose();
        super.onDestroy();
    }

    @NonNull
    private static String text(@NonNull TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }

    private GistService service() {
        return GitHubApi.service(GistService.class);
    }

    private void loadFile() {
        loader.setVisibility(View.VISIBLE);
        loading = true;
        filenameEdit.setText(fileName);
        disposables.add(Single.fromCallable(() -> GitHubApi.call(service().getGist(gistId)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(json -> {
                    loader.setVisibility(View.GONE);
                    JsonObject file = Json.obj(json, "files");
                    file = file == null || !file.has(fileName) || !file.get(fileName).isJsonObject()
                            ? null : file.getAsJsonObject(fileName);
                    String content = file == null ? null : Json.str(file, "content");
                    if (file == null || content == null || Json.bool(file, "truncated")
                            || content.length() > GistActivity.MAX_INLINE_CHARS) {
                        showErrorToast(getString(R.string.gist_file_too_big));
                        finish();
                        return;
                    }
                    editor.getEditText().setText(content);
                    editor.setPreviewAvailable(GitHubHelper.isMarkdown(fileName));
                    loading = false;
                }, error -> {
                    loader.setVisibility(View.GONE);
                    showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                    finish();
                }));
    }

    private void save() {
        if (saving || loading) return;
        String name = text(filenameEdit).trim();
        String content = editor.getText();
        filenameLayout.setError(null);
        if (name.isEmpty()) {
            filenameLayout.setError(getString(R.string.field_required));
            return;
        }
        if (name.contains("/")) {
            filenameLayout.setError(getString(R.string.gist_name_invalid));
            return;
        }
        boolean renamed = MODE_EDIT.equals(mode) && !name.equals(fileName);
        if ((MODE_ADD.equals(mode) || renamed) && names.contains(name)) {
            filenameLayout.setError(getString(R.string.gist_name_exists, name));
            return;
        }
        if (content.trim().isEmpty()) {
            showWarningToast(getString(R.string.gist_content_required));
            return;
        }

        JsonObject file = new JsonObject();
        file.addProperty("content", content);
        if (renamed) file.addProperty("filename", name);
        JsonObject files = new JsonObject();
        files.add(MODE_EDIT.equals(mode) ? fileName : name, file);
        JsonObject body = new JsonObject();
        body.add("files", files);
        if (MODE_NEW.equals(mode)) {
            body.addProperty("description", text(descriptionEdit).trim());
            body.addProperty("public", publicSwitch.isChecked());
        }

        saving = true;
        invalidateBack();
        loader.setVisibility(View.VISIBLE);
        saveBn.setEnabled(false);
        disposables.add(Single.fromCallable(() -> GitHubApi.call(MODE_NEW.equals(mode)
                        ? service().createGist(GistActivity.json(body))
                        : service().updateGist(gistId, GistActivity.json(body))))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(json -> {
                    GistChanges.markChanged();
                    dirty = false;
                    showSuccessToast(getString(MODE_NEW.equals(mode) ? R.string.gist_created : R.string.gist_saved));
                    String id = Json.str(json, "id");
                    if (MODE_NEW.equals(mode) && !StringUtils.isBlank(id)) {
                        startActivity(GistActivity.createIntent(this, id));
                    }
                    finish();
                }, error -> {
                    saving = false;
                    invalidateBack();
                    loader.setVisibility(View.GONE);
                    saveBn.setEnabled(true);
                    String message = StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
                    if (error instanceof GitHubApi.ApiException && ((GitHubApi.ApiException) error).code == 404) {
                        message = getString(R.string.gist_scope_missing) + "\n(" + message + ")";
                    }
                    showErrorToast(message);
                }));
    }

    // ---------------------------------------------------------------------------------
    // Back
    // ---------------------------------------------------------------------------------

    @Override
    protected boolean interceptsBack() {
        return super.interceptsBack() || (dirty && !saving);
    }

    @Override
    protected void handleBack() {
        super.handleBack();
        if (!dirty || saving) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.release_discard_title)
                .setMessage(R.string.gist_discard_message)
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
        descriptionLayout = root.findViewById(R.id.description_layout);
        descriptionEdit = root.findViewById(R.id.description_edit);
        filenameLayout = root.findViewById(R.id.filename_layout);
        filenameEdit = root.findViewById(R.id.filename_edit);
        publicSwitch = root.findViewById(R.id.public_switch);
        saveBn = root.findViewById(R.id.save_bn);
        loader = root.findViewById(R.id.loader);
        editor = new MarkdownWritePreview(root.findViewById(R.id.content_editor), null);
    }
}
