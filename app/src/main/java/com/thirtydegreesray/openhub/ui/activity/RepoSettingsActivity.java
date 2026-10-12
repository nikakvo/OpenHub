package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Settings of a repository (admin access), like its Settings page on github.com:
 * name, description, website, topics, template and default branch; features;
 * pull request merge options; commit sign-off; release immutability; branches;
 * and the danger zone (visibility, archive, transfer, delete).
 */
public class RepoSettingsActivity extends BaseActivity<IBaseContract.Presenter> {

    /** Result: the repository moved (renamed); extras "owner" and "repo" hold the new name. */
    public static final int RESULT_MOVED = Activity.RESULT_FIRST_USER + 1;
    /** Result: the repository was deleted or transferred away. */
    public static final int RESULT_GONE = Activity.RESULT_FIRST_USER + 2;

    @NonNull
    public static Intent createIntent(@NonNull Activity activity, @NonNull String owner, @NonNull String repo) {
        return new Intent(activity, RepoSettingsActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;

    private NestedScrollView scroll;
    private TextInputEditText nameEdit;
    private TextInputLayout nameLayout;
    private TextInputEditText descriptionEdit;
    private TextInputEditText websiteEdit;
    private TextInputEditText topicsEdit;
    private TextInputLayout topicsLayout;
    private LinearLayout rows;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private JsonObject repoJson;
    /** null: not supported for this repository / account */
    @Nullable private Boolean immutableReleases;
    /** false when the token is known to lack the delete_repo scope */
    private boolean canDeleteWithToken = true;
    /** RESULT_CANCELED until something changed, then RESULT_OK or RESULT_MOVED */
    private int resultCode = RESULT_CANCELED;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_repo_settings;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.settings), owner + "/" + repo);
        load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private RepoSettingsService service() {
        return GitHubApi.service(RepoSettingsService.class);
    }

    // ---------------------------------------------------------------------------------
    // Load and show
    // ---------------------------------------------------------------------------------

    private void load() {
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            Response<JsonObject> repoResponse = service().getRepo(owner, repo).blockingFirst();
            if (!repoResponse.isSuccessful()) {
                throw new GitHubApi.ApiException(repoResponse.code(), GitHubApi.errorMessage(repoResponse));
            }
            JsonObject json = repoResponse.body();
            // classic tokens list their scopes here; fine-grained tokens send nothing
            String scopes = repoResponse.headers().get("X-OAuth-Scopes");
            Boolean immutable;
            try {
                Response<ResponseBody> response = service().getImmutableReleases(owner, repo).blockingFirst();
                if (response.code() == 404) {
                    immutable = false;
                } else if (response.isSuccessful()) {
                    String body = response.body() == null ? "" : response.body().string();
                    immutable = !body.contains("\"enabled\":false") && !body.contains("\"enabled\": false");
                } else {
                    immutable = null;
                }
            } catch (Exception e) {
                immutable = null;
            }
            return new Object[]{json, immutable, scopes};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            repoJson = (JsonObject) result[0];
            String scopes = (String) result[2];
            canDeleteWithToken = scopes == null || scopes.contains("delete_repo");
            immutableReleases = (Boolean) result[1];
            show();
        }, error -> {
            loader.setVisibility(View.GONE);
            finish();
        });
    }

    private void show() {
        scroll.setVisibility(View.VISIBLE);
        nameEdit.setText(str("name"));
        descriptionEdit.setText(str("description"));
        websiteEdit.setText(str("homepage"));
        List<String> topics = new ArrayList<>();
        if (repoJson.has("topics") && repoJson.get("topics").isJsonArray()) {
            for (JsonElement topic : repoJson.getAsJsonArray("topics")) topics.add(topic.getAsString());
        }
        topicsEdit.setText(String.join(", ", topics));
        buildRows();
    }

    private void buildRows() {
        rows.removeAllViews();
        boolean archived = bool("archived");

        section(R.string.settings_repository);
        switchRow("is_template", R.string.settings_template, R.string.settings_template_summary);
        actionRow(getString(R.string.settings_default_branch), str("default_branch"), false,
                v -> chooseDefaultBranch());
        actionRow(getString(R.string.settings_branches), getString(R.string.settings_branches_summary),
                false, v -> startActivity(BranchesActivity.createIntent(this, owner, repo)));

        section(R.string.settings_features);
        switchRow("has_issues", R.string.settings_issues, R.string.settings_issues_summary);
        switchRow("has_wiki", R.string.settings_wikis, R.string.settings_wikis_summary);
        switchRow("has_projects", R.string.settings_projects, R.string.settings_projects_summary);
        switchRow("has_discussions", R.string.settings_discussions, R.string.settings_discussions_summary);

        section(R.string.settings_pull_requests);
        switchRow("allow_merge_commit", R.string.settings_allow_merge, R.string.settings_allow_merge_summary);
        if (bool("allow_merge_commit")) {
            actionRow(getString(R.string.settings_merge_message), mergeMessageLabel(), false,
                    v -> chooseMergeMessage());
        }
        switchRow("allow_squash_merge", R.string.settings_allow_squash, R.string.settings_allow_squash_summary);
        if (bool("allow_squash_merge")) {
            actionRow(getString(R.string.settings_squash_message), squashMessageLabel(), false,
                    v -> chooseSquashMessage());
        }
        switchRow("allow_rebase_merge", R.string.settings_allow_rebase, R.string.settings_allow_rebase_summary);
        switchRow("allow_update_branch", R.string.settings_update_branch, R.string.settings_update_branch_summary);
        switchRow("allow_auto_merge", R.string.settings_auto_merge, R.string.settings_auto_merge_summary);
        switchRow("delete_branch_on_merge", R.string.settings_delete_head, R.string.settings_delete_head_summary);

        section(R.string.settings_commits);
        switchRow("web_commit_signoff_required", R.string.settings_signoff, R.string.settings_signoff_summary);

        if (immutableReleases != null) {
            section(R.string.releases);
            View row = actionRow(getString(R.string.settings_immutable),
                    getString(R.string.settings_immutable_summary), false, null);
            SwitchCompat toggle = row.findViewById(R.id.setting_switch);
            toggle.setVisibility(View.VISIBLE);
            toggle.setChecked(immutableReleases);
            row.setOnClickListener(v -> setImmutable(!immutableReleases));
        }

        section(R.string.settings_danger_zone);
        boolean isPrivate = bool("private");
        actionRow(getString(R.string.settings_visibility), getString(isPrivate
                        ? R.string.settings_visibility_private : R.string.settings_visibility_public), true,
                v -> confirmVisibility(!isPrivate));
        actionRow(getString(archived ? R.string.settings_unarchive : R.string.settings_archive),
                getString(archived ? R.string.settings_unarchive_summary : R.string.settings_archive_summary),
                true, v -> confirmArchive(!archived));
        actionRow(getString(R.string.settings_transfer), getString(R.string.settings_transfer_summary), true,
                v -> showTransfer());
        actionRow(getString(R.string.settings_delete), getString(canDeleteWithToken
                        ? R.string.settings_delete_summary : R.string.settings_delete_needs_scope), true,
                v -> {
                    if (canDeleteWithToken) confirmDelete();
                    else explainDeleteScope();
                });
    }

    private void section(@StringRes int title) {
        TextView view = (TextView) LayoutInflater.from(this).inflate(R.layout.layout_setting_section, rows, false);
        view.setText(title);
        rows.addView(view);
    }

    @NonNull
    private View actionRow(@NonNull String title, @Nullable String summary, boolean danger,
                           @Nullable View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_setting_row, rows, false);
        TextView titleView = row.findViewById(R.id.setting_title);
        TextView summaryView = row.findViewById(R.id.setting_summary);
        titleView.setText(title);
        if (danger) titleView.setTextColor(getResources().getColor(R.color.material_red_400, getTheme()));
        summaryView.setText(summary);
        summaryView.setVisibility(StringUtils.isBlank(summary) ? View.GONE : View.VISIBLE);
        if (click != null) row.setOnClickListener(click);
        rows.addView(row);
        return row;
    }

    private void switchRow(@NonNull String key, @StringRes int title, @StringRes int summary) {
        if (!repoJson.has(key)) return; // not offered for this repository
        View row = actionRow(getString(title), getString(summary), false, null);
        SwitchCompat toggle = row.findViewById(R.id.setting_switch);
        toggle.setVisibility(View.VISIBLE);
        toggle.setChecked(bool(key));
        row.setOnClickListener(v -> {
            boolean value = !bool(key);
            if (!value && isLastMergeMethod(key)) {
                showWarningToast(getString(R.string.settings_merge_method_required));
                return;
            }
            Map<String, Object> body = new HashMap<>();
            body.put(key, value);
            patch(body);
        });
    }

    private boolean isLastMergeMethod(@NonNull String key) {
        if (!key.equals("allow_merge_commit") && !key.equals("allow_squash_merge")
                && !key.equals("allow_rebase_merge")) {
            return false;
        }
        int on = (bool("allow_merge_commit") ? 1 : 0) + (bool("allow_squash_merge") ? 1 : 0)
                + (bool("allow_rebase_merge") ? 1 : 0);
        return on <= 1;
    }

    // ---------------------------------------------------------------------------------
    // Changes
    // ---------------------------------------------------------------------------------

    /** PATCH the repository and show the answer; on failure the old state stays. */
    private void patch(@NonNull Map<String, Object> body) {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GitHubApi.call(service().updateRepo(owner, repo, body)), (JsonObject json) -> {
            dismissProgressDialog();
            markChanged();
            String newName = json.get("name").getAsString();
            String newOwner = json.getAsJsonObject("owner").get("login").getAsString();
            boolean moved = !newName.equals(repo) || !newOwner.equals(owner);
            repoJson = json;
            if (moved) {
                owner = newOwner;
                repo = newName;
                resultCode = RESULT_MOVED;
                setResult(RESULT_MOVED, new Intent().putExtra("owner", owner).putExtra("repo", repo));
                setToolbarSubTitle(owner + "/" + repo);
            }
            int y = scroll.getScrollY();
            show();
            scroll.post(() -> scroll.scrollTo(0, y));
        }, error -> {
            dismissProgressDialog();
            buildRows();
        });
    }

    private void saveGeneral() {
        String name = text(nameEdit);
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.field_required));
            return;
        }
        nameLayout.setErrorEnabled(false);
        topicsLayout.setErrorEnabled(false);
        String normalized = CreateRepositoryActivity.normalizeName(name);
        List<String> topics = new ArrayList<>();
        for (String raw : text(topicsEdit).split("[,\\s]+")) {
            String topic = raw.trim().toLowerCase(Locale.ROOT);
            if (topic.isEmpty()) continue;
            if (!topic.matches("[a-z0-9][a-z0-9-]{0,49}")) {
                topicsLayout.setError(getString(R.string.settings_topic_invalid, topic));
                return;
            }
            if (!topics.contains(topic)) topics.add(topic);
        }
        if (topics.size() > 20) {
            topicsLayout.setError(getString(R.string.settings_topics_too_many));
            return;
        }
        Runnable save = () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("name", normalized);
            body.put("description", text(descriptionEdit));
            body.put("homepage", text(websiteEdit));
            showProgressDialog(getString(R.string.please_wait));
            final String oldOwner = owner;
            final String oldRepo = repo;
            background(() -> {
                JsonObject json = GitHubApi.call(service().updateRepo(oldOwner, oldRepo, body));
                String newName = json.get("name").getAsString();
                Map<String, Object> names = new HashMap<>();
                names.put("names", topics);
                GitHubApi.call(service().replaceTopics(oldOwner, newName, names));
                return GitHubApi.call(service().getRepo(oldOwner, newName));
            }, (JsonObject json) -> {
                dismissProgressDialog();
                markChanged();
                repoJson = json;
                String newName = json.get("name").getAsString();
                if (!newName.equals(repo)) {
                    repo = newName;
                    resultCode = RESULT_MOVED;
                    setResult(RESULT_MOVED, new Intent().putExtra("owner", owner).putExtra("repo", repo));
                    setToolbarSubTitle(owner + "/" + repo);
                }
                showSuccessToast(getString(R.string.settings_saved));
                show();
            }, error -> dismissProgressDialog());
        };
        if (!normalized.equals(repo)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_rename_title)
                    .setMessage(getString(R.string.settings_rename_message, repo, normalized))
                    .setPositiveButton(R.string.rename, (d, w) -> save.run())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            save.run();
        }
    }

    private void chooseDefaultBranch() {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> {
            ArrayList<String> names = new ArrayList<>();
            JsonArray list = GitHubApi.call(service().getBranches(owner, repo, 1));
            if (list != null) for (JsonElement b : list) names.add(b.getAsJsonObject().get("name").getAsString());
            return names;
        }, (ArrayList<String> names) -> {
            dismissProgressDialog();
            String current = str("default_branch");
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_default_branch)
                    .setSingleChoiceItems(items, names.indexOf(current), (dialog, which) -> {
                        dialog.dismiss();
                        if (items[which].equals(current)) return;
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.settings_default_branch)
                                .setMessage(getString(R.string.settings_default_branch_confirm, items[which]))
                                .setPositiveButton(R.string.ok, (d, w) -> {
                                    Map<String, Object> body = new HashMap<>();
                                    body.put("default_branch", items[which]);
                                    patch(body);
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }, error -> dismissProgressDialog());
    }

    // merge commit message: title / message pairs as offered on github.com
    private static final String[][] MERGE_OPTIONS = {
            {"MERGE_MESSAGE", "PR_TITLE"}, {"PR_TITLE", "BLANK"}, {"PR_TITLE", "PR_BODY"}};
    private static final int[] MERGE_LABELS = {R.string.settings_msg_default, R.string.settings_msg_pr_title,
            R.string.settings_msg_pr_title_description};
    private static final String[][] SQUASH_OPTIONS = {
            {"COMMIT_OR_PR_TITLE", "COMMIT_MESSAGES"}, {"PR_TITLE", "BLANK"},
            {"PR_TITLE", "COMMIT_MESSAGES"}, {"PR_TITLE", "PR_BODY"}};
    private static final int[] SQUASH_LABELS = {R.string.settings_msg_default, R.string.settings_msg_pr_title,
            R.string.settings_msg_pr_title_commits, R.string.settings_msg_pr_title_description};

    @NonNull
    private String mergeMessageLabel() {
        int i = indexOf(MERGE_OPTIONS, str("merge_commit_title"), str("merge_commit_message"));
        return getString(MERGE_LABELS[Math.max(i, 0)]);
    }

    @NonNull
    private String squashMessageLabel() {
        int i = indexOf(SQUASH_OPTIONS, str("squash_merge_commit_title"), str("squash_merge_commit_message"));
        return getString(SQUASH_LABELS[Math.max(i, 0)]);
    }

    private static int indexOf(@NonNull String[][] options, @Nullable String title, @Nullable String message) {
        for (int i = 0; i < options.length; i++) {
            if (options[i][0].equals(title) && options[i][1].equals(message)) return i;
        }
        return -1;
    }

    private void chooseMergeMessage() {
        chooseMessage(R.string.settings_merge_message, MERGE_OPTIONS, MERGE_LABELS,
                "merge_commit_title", "merge_commit_message");
    }

    private void chooseSquashMessage() {
        chooseMessage(R.string.settings_squash_message, SQUASH_OPTIONS, SQUASH_LABELS,
                "squash_merge_commit_title", "squash_merge_commit_message");
    }

    private void chooseMessage(@StringRes int title, @NonNull String[][] options, @NonNull int[] labels,
                               @NonNull String titleKey, @NonNull String messageKey) {
        String[] items = new String[labels.length];
        for (int i = 0; i < labels.length; i++) items[i] = getString(labels[i]);
        int checked = indexOf(options, str(titleKey), str(messageKey));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    Map<String, Object> body = new HashMap<>();
                    body.put(titleKey, options[which][0]);
                    body.put(messageKey, options[which][1]);
                    patch(body);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setImmutable(boolean enable) {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> {
            GitHubApi.call(enable ? service().enableImmutableReleases(owner, repo)
                    : service().disableImmutableReleases(owner, repo));
            return Boolean.TRUE;
        }, done -> {
            dismissProgressDialog();
            markChanged();
            immutableReleases = enable;
            buildRows();
        }, error -> dismissProgressDialog());
    }

    // ---------------------------------------------------------------------------------
    // Danger zone
    // ---------------------------------------------------------------------------------

    private void confirmVisibility(boolean makePrivate) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_visibility)
                .setMessage(getString(makePrivate ? R.string.settings_make_private_message
                        : R.string.settings_make_public_message, owner + "/" + repo))
                .setPositiveButton(makePrivate ? R.string.settings_make_private : R.string.settings_make_public,
                        (d, w) -> {
                            Map<String, Object> body = new HashMap<>();
                            body.put("private", makePrivate);
                            patch(body);
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmArchive(boolean archive) {
        new AlertDialog.Builder(this)
                .setTitle(archive ? R.string.settings_archive : R.string.settings_unarchive)
                .setMessage(archive ? R.string.settings_archive_message : R.string.settings_unarchive_message)
                .setPositiveButton(archive ? R.string.settings_archive : R.string.settings_unarchive, (d, w) -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("archived", archive);
                    patch(body);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTransfer() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_x_large);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText newOwner = new EditText(this);
        newOwner.setHint(R.string.settings_transfer_new_owner);
        newOwner.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText confirm = new EditText(this);
        confirm.setHint(owner + "/" + repo);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.settings_type_to_confirm, owner + "/" + repo));
        form.addView(newOwner);
        form.addView(hint);
        form.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_transfer)
                .setView(form)
                .setPositiveButton(R.string.settings_transfer_button, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String target = newOwner.getText().toString().trim();
            if (target.isEmpty()) {
                newOwner.setError(getString(R.string.field_required));
                return;
            }
            if (!confirm.getText().toString().trim().equals(owner + "/" + repo)) {
                confirm.setError(getString(R.string.settings_confirm_mismatch));
                return;
            }
            dialog.dismiss();
            Map<String, Object> body = new HashMap<>();
            body.put("new_owner", target);
            showProgressDialog(getString(R.string.please_wait));
            background(() -> GitHubApi.call(service().transfer(owner, repo, body)), json -> {
                dismissProgressDialog();
                showSuccessToast(getString(R.string.settings_transferred, target));
                setResult(RESULT_GONE);
                finish();
            }, error -> dismissProgressDialog());
        }));
        dialog.show();
    }

    /** The token cannot delete repositories: say how to allow it. */
    private void explainDeleteScope() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_delete)
                .setMessage(R.string.settings_delete_scope_help)
                .setPositiveButton(R.string.settings_open_tokens, (d, w) ->
                        com.thirtydegreesray.openhub.util.AppOpener.openInCustomTabsOrBrowser(this,
                                "https://github.com/settings/tokens"))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        FrameLayout frame = new FrameLayout(this);
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_x_large);
        frame.setPadding(pad, 0, pad, 0);
        EditText confirm = new EditText(this);
        confirm.setHint(owner + "/" + repo);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        frame.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_delete)
                .setMessage(getString(R.string.settings_delete_message, owner + "/" + repo))
                .setView(frame)
                .setPositiveButton(R.string.settings_delete_button, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!confirm.getText().toString().trim().equals(owner + "/" + repo)) {
                confirm.setError(getString(R.string.settings_confirm_mismatch));
                return;
            }
            dialog.dismiss();
            showProgressDialog(getString(R.string.please_wait));
            background(() -> {
                try {
                    GitHubApi.call(service().deleteRepo(owner, repo));
                } catch (GitHubApi.ApiException e) {
                    if (e.code == 403) {
                        throw new GitHubApi.ApiException(403, getString(R.string.settings_delete_scope)
                                + "\n(" + e.getMessage() + ")");
                    }
                    throw e;
                }
                return Boolean.TRUE;
            }, done -> {
                dismissProgressDialog();
                showSuccessToast(getString(R.string.settings_deleted, owner + "/" + repo));
                setResult(RESULT_GONE);
                finish();
            }, error -> dismissProgressDialog());
        }));
        dialog.show();
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private void markChanged() {
        if (resultCode == RESULT_CANCELED) {
            resultCode = RESULT_OK;
            setResult(RESULT_OK);
        }
    }

    @NonNull
    private String str(@NonNull String key) {
        JsonElement value = repoJson == null ? null : repoJson.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private boolean bool(@NonNull String key) {
        JsonElement value = repoJson == null ? null : repoJson.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    @NonNull
    private static String text(@NonNull TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        scroll = root.findViewById(R.id.settings_scroll);
        nameEdit = root.findViewById(R.id.name_edit);
        nameLayout = root.findViewById(R.id.name_layout);
        descriptionEdit = root.findViewById(R.id.description_edit);
        websiteEdit = root.findViewById(R.id.website_edit);
        topicsEdit = root.findViewById(R.id.topics_edit);
        topicsLayout = root.findViewById(R.id.topics_layout);
        rows = root.findViewById(R.id.rows_container);
        loader = root.findViewById(R.id.loader);
        ViewUtils.setOnClick(root, R.id.save_general_bn, v -> saveGeneral());
    }
}
