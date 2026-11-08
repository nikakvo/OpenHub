package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.DeployKeyService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ButtonStyles;
import com.thirtydegreesray.openhub.util.DeployKeys;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Deploy keys of a repository, like github.com/owner/repo/settings/keys (admins): SSH
 * keys that give one server or device access to this repository only. List with the
 * fingerprint, read-only or read and write, when added and last used; add a key (pasted
 * or from a .pub file; private keys are refused on the phone); delete a key. Every state
 * shown is GitHub's answer; after a change the list is read again.
 */
public class DeployKeysActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, DeployKeysActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 10;
    /** A public key is a few hundred bytes; anything bigger is not one. */
    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_BROWSER = 2;

    @AutoAccess String owner;
    @AutoAccess String repo;

    private LinearLayout list;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton addBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    /** the key box of the open Add dialog, filled by the file picker */
    @Nullable private EditText pendingKeyEdit;

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_deploy_keys;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.deploy_keys), owner + "/" + repo);
        PullRefresh.wrap(findViewById(R.id.keys_scroll), this::reload).follow(loader);
        reload();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private DeployKeyService service() {
        return GitHubApi.service(DeployKeyService.class);
    }

    // ---------------------------------------------------------------------------------
    // List
    // ---------------------------------------------------------------------------------

    private void reload() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        background(() -> {
            List<JsonObject> all = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonArray items = GitHubApi.call(service().getKeys(owner, repo, PER_PAGE, page));
                int count = 0;
                if (items != null) {
                    for (JsonElement e : items) {
                        if (!e.isJsonObject()) continue;
                        all.add(e.getAsJsonObject());
                        count++;
                    }
                }
                if (count < PER_PAGE) break;
            }
            return all;
        }, (List<JsonObject> keys) -> {
            loader.setVisibility(View.GONE);
            list.removeAllViews();
            for (JsonObject key : keys) list.addView(keyRow(key));
            if (keys.isEmpty()) {
                emptyText.setText(R.string.deploy_keys_none);
                emptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    @NonNull
    private static String keyTitle(@NonNull JsonObject key) {
        String title = Json.str(key, "title");
        return title.isEmpty() ? "#" + Json.lng(key, "id") : title;
    }

    @NonNull
    private String when(@Nullable Date date) {
        return date == null ? "-" : StringUtils.getNewsTimeStr(this, date);
    }

    @NonNull
    private View keyRow(@NonNull JsonObject key) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_actions_entry, list, false);
        ((TextView) row.findViewById(R.id.entry_title)).setText(keyTitle(key));
        String fingerprint = DeployKeys.fingerprintOf(Json.str(key, "key"));
        ((TextView) row.findViewById(R.id.entry_detail)).setText(fingerprint.isEmpty()
                ? Json.str(key, "key") : fingerprint);

        SpannableStringBuilder state = new SpannableStringBuilder();
        boolean readOnly = Json.bool(key, "read_only");
        state.append(getString(readOnly ? R.string.deploy_keys_read_only : R.string.deploy_keys_read_write));
        state.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this,
                        readOnly ? R.color.pull_open : R.color.material_amber_800)),
                0, state.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (Json.get(key, "verified") != null && !Json.bool(key, "verified")) {
            state.append("  ·  ").append(getString(R.string.deploy_keys_unverified));
        }
        if (Json.get(key, "enabled") != null && !Json.bool(key, "enabled")) {
            int start = state.length() + 5;
            state.append("  ·  ").append(getString(R.string.deploy_keys_disabled));
            state.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.pull_closed)),
                    start, state.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String addedBy = Json.str(key, "added_by");
        String added = when(Json.date(key, "created_at"));
        state.append("\n").append(addedBy.isEmpty() ? getString(R.string.deploy_keys_added_on, added)
                : getString(R.string.deploy_keys_added_by, added, addedBy));
        Date lastUsed = Json.date(key, "last_used");
        state.append(", ").append(lastUsed == null ? getString(R.string.deploy_keys_never_used)
                : getString(R.string.deploy_keys_last_used, when(lastUsed)));
        ((TextView) row.findViewById(R.id.entry_state)).setText(state);

        row.setOnClickListener(v -> showKeyActions(v, key, fingerprint));
        return row;
    }

    private void showKeyActions(@NonNull View anchor, @NonNull JsonObject key, @NonNull String fingerprint) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (!fingerprint.isEmpty()) popup.getMenu().add(0, 1, 0, R.string.deploy_keys_copy_fingerprint);
        popup.getMenu().add(0, 2, 1, R.string.deploy_keys_copy_key);
        popup.getMenu().add(0, 3, 2, R.string.deploy_keys_delete);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                AppUtils.copyToClipboard(this, fingerprint);
                showSuccessToast(getString(R.string.deploy_keys_copied));
                return true;
            } else if (item.getItemId() == 2) {
                AppUtils.copyToClipboard(this, Json.str(key, "key"));
                showSuccessToast(getString(R.string.deploy_keys_copied));
                return true;
            } else if (item.getItemId() == 3) {
                confirmDelete(key, fingerprint);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(@NonNull JsonObject key, @NonNull String fingerprint) {
        String title = keyTitle(key);
        new AlertDialog.Builder(this)
                .setTitle(R.string.deploy_keys_delete)
                .setMessage(getString(R.string.deploy_keys_delete_message, title,
                        fingerprint.isEmpty() ? "#" + Json.lng(key, "id") : fingerprint))
                .setPositiveButton(R.string.delete, (d, w) -> deleteKey(Json.lng(key, "id"), title))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Deletes, then asks GitHub for the key: only a 404 counts as gone. */
    private void deleteKey(long id, @NonNull String title) {
        showProgressDialog(getString(R.string.please_wait));
        disposables.add(Single.fromCallable(() -> {
                    GitHubApi.call(service().deleteKey(owner, repo, id));
                    try {
                        GitHubApi.call(service().getKey(owner, repo, id));
                        return false;
                    } catch (GitHubApi.ApiException e) {
                        if (e.code == 404) return true;
                        throw e;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(gone -> {
                    dismissProgressDialog();
                    if (gone) showSuccessToast(getString(R.string.deploy_keys_deleted, title));
                    else showWarningToast(getString(R.string.deploy_keys_still_there, title));
                    reload();
                }, error -> {
                    dismissProgressDialog();
                    showErrorToast(errorText(error));
                    reload();
                }));
    }

    // ---------------------------------------------------------------------------------
    // Add
    // ---------------------------------------------------------------------------------

    private void showAddDialog() {
        Context ctx = new AlertDialog.Builder(this).getContext();
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_large);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText titleEdit = new EditText(ctx);
        titleEdit.setHint(R.string.deploy_keys_title_hint);
        titleEdit.setSingleLine(true);
        box.addView(titleEdit);

        EditText keyEdit = new EditText(ctx);
        keyEdit.setHint(R.string.deploy_keys_key_hint);
        keyEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyEdit.setTypeface(Typeface.MONOSPACE);
        keyEdit.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_small));
        keyEdit.setMinLines(3);
        keyEdit.setMaxLines(6);
        box.addView(keyEdit);

        Button pickBn = new Button(ctx);
        pickBn.setText(R.string.deploy_keys_pick_file);
        ButtonStyles.tonal(pickBn);
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pickParams.topMargin = pad / 2;
        box.addView(pickBn, pickParams);

        TextView status = new TextView(ctx);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_small));
        status.setPadding(0, pad / 2, 0, 0);
        status.setVisibility(View.GONE);
        box.addView(status);

        CheckBox writeCheck = new CheckBox(ctx);
        writeCheck.setText(R.string.deploy_keys_write);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checkParams.topMargin = pad / 2;
        box.addView(writeCheck, checkParams);

        TextView writeNote = new TextView(ctx);
        writeNote.setText(R.string.deploy_keys_write_note);
        writeNote.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_small));
        writeNote.setTextColor(ContextCompat.getColor(this, R.color.material_amber_800));
        writeNote.setVisibility(View.GONE);
        box.addView(writeNote);

        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.deploy_keys_add)
                .setView(scroll)
                .setPositiveButton(R.string.deploy_keys_add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        final int normalColor = status.getCurrentTextColor();

        Runnable check = () -> {
            DeployKeys.Parsed parsed = DeployKeys.parse(keyEdit.getText().toString());
            Button add = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (add != null) add.setEnabled(parsed.ok());
            if (parsed.problem == DeployKeys.Problem.EMPTY) {
                status.setVisibility(View.GONE);
                return;
            }
            status.setVisibility(View.VISIBLE);
            if (parsed.ok()) {
                status.setTextColor(normalColor);
                status.setText(getString(R.string.deploy_keys_fingerprint, parsed.fingerprint()));
                if (titleEdit.getHint() != null) {
                    titleEdit.setHint(DeployKeys.defaultTitle(parsed, today()));
                }
            } else {
                status.setTextColor(ContextCompat.getColor(this, R.color.pull_closed));
                status.setText(problemText(parsed.problem));
                titleEdit.setHint(R.string.deploy_keys_title_hint);
            }
        };
        keyEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                check.run();
            }
        });
        writeCheck.setOnCheckedChangeListener((button, checked) ->
                writeNote.setVisibility(checked ? View.VISIBLE : View.GONE));
        pickBn.setOnClickListener(v -> {
            pendingKeyEdit = keyEdit;
            filePicker.launch(new String[]{"*/*"});
        });

        dialog.setOnShowListener(d -> {
            Button add = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            add.setEnabled(false);
            add.setOnClickListener(v -> {
                DeployKeys.Parsed parsed = DeployKeys.parse(keyEdit.getText().toString());
                if (!parsed.ok()) return;
                String title = titleEdit.getText().toString().trim();
                if (title.isEmpty()) title = DeployKeys.defaultTitle(parsed, today());
                dialog.dismiss();
                addKey(title, parsed, !writeCheck.isChecked());
            });
        });
        dialog.setOnDismissListener(d -> {
            if (pendingKeyEdit == keyEdit) pendingKeyEdit = null;
        });
        dialog.show();
    }

    @NonNull
    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    @NonNull
    private String problemText(@NonNull DeployKeys.Problem problem) {
        switch (problem) {
            case PRIVATE_KEY: return getString(R.string.deploy_keys_problem_private);
            case UNKNOWN_TYPE: return getString(R.string.deploy_keys_problem_type);
            case BROKEN: return getString(R.string.deploy_keys_problem_broken);
            default: return getString(R.string.deploy_keys_problem_not_key);
        }
    }

    private void onFilePicked(@Nullable Uri uri) {
        EditText target = pendingKeyEdit;
        if (uri == null || target == null) return;
        background(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return "";
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_FILE_BYTES) {
                        throw new GitHubApi.ApiException(413, getString(R.string.deploy_keys_file_too_big));
                    }
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }, (String text) -> {
            loader.setVisibility(View.GONE);
            // a private key is shown as refused, never sent: the check runs on the text
            target.setText(text.trim());
        });
    }

    /** Adds, then lists again: the new row is what GitHub stored. */
    private void addKey(@NonNull String title, @NonNull DeployKeys.Parsed parsed, boolean readOnly) {
        showProgressDialog(getString(R.string.please_wait));
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("key", parsed.keyLine());
        body.put("read_only", readOnly);
        disposables.add(Single.fromCallable(() -> GitHubApi.call(service().createKey(owner, repo, body)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(created -> {
                    dismissProgressDialog();
                    String stored = DeployKeys.fingerprintOf(Json.str(created, "key"));
                    showSuccessToast(getString(R.string.deploy_keys_added,
                            stored.isEmpty() ? Json.str(created, "title") : stored));
                    reload();
                }, error -> {
                    dismissProgressDialog();
                    showErrorToast(errorText(error));
                }));
    }

    // ---------------------------------------------------------------------------------

    @NonNull
    private static String errorText(@NonNull Throwable error) {
        return StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.refresh);
        menu.add(0, MENU_BROWSER, 1, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_REFRESH) {
            reload();
            return true;
        } else if (id == MENU_BROWSER) {
            AppOpener.openInCustomTabsOrBrowser(this, "https://github.com/" + owner + "/" + repo + "/settings/keys");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, error -> {
                    loader.setVisibility(View.GONE);
                    showErrorToast(errorText(error));
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        list = root.findViewById(R.id.keys_list);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        addBn = root.findViewById(R.id.add_key_bn);
        addBn.setOnClickListener(v -> showAddDialog());
    }
}
