package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.TextInputDialog;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * Branches of a repository: create, rename and delete them (write access) and set
 * branch protection (admin), like the Branches page on github.com.
 */
public class BranchesActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, BranchesActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    private static final int MAX_PAGES = 5;

    @AutoAccess String owner;
    @AutoAccess String repo;

    private LinearLayout list;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton newBranchBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<JsonObject> branches = new ArrayList<>();
    private String defaultBranch;
    private boolean canPush;
    private boolean isAdmin;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_branches;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.settings_branches), owner + "/" + repo);
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

    private void load() {
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            JsonObject repoJson = GitHubApi.call(service().getRepo(owner, repo));
            List<JsonObject> all = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonArray array = GitHubApi.call(service().getBranches(owner, repo, page));
                if (array == null || array.size() == 0) break;
                for (JsonElement element : array) all.add(element.getAsJsonObject());
                if (array.size() < 100) break;
            }
            return new Object[]{repoJson, all};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            JsonObject repoJson = (JsonObject) result[0];
            defaultBranch = repoJson.get("default_branch").getAsString();
            JsonObject permissions = repoJson.has("permissions") ? repoJson.getAsJsonObject("permissions") : null;
            isAdmin = permissions != null && permissions.get("admin").getAsBoolean();
            canPush = isAdmin || (permissions != null && permissions.get("push").getAsBoolean());
            branches.clear();
            //noinspection unchecked
            branches.addAll((List<JsonObject>) result[1]);
            // default branch first, like on github.com
            for (int i = 0; i < branches.size(); i++) {
                if (name(branches.get(i)).equals(defaultBranch)) {
                    branches.add(0, branches.remove(i));
                    break;
                }
            }
            render();
        }, error -> loader.setVisibility(View.GONE));
    }

    private void render() {
        list.removeAllViews();
        emptyText.setVisibility(branches.isEmpty() ? View.VISIBLE : View.GONE);
        if (canPush) newBranchBn.show();
        else newBranchBn.hide();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (JsonObject branch : branches) {
            String name = name(branch);
            View row = inflater.inflate(R.layout.layout_setting_row, list, false);
            ((TextView) row.findViewById(R.id.setting_title)).setText(name);
            List<String> meta = new ArrayList<>();
            if (name.equals(defaultBranch)) meta.add(getString(R.string.branches_default));
            if (branch.has("protected") && branch.get("protected").getAsBoolean()) {
                meta.add(getString(R.string.branches_protected));
            }
            if (branch.has("commit")) {
                String sha = branch.getAsJsonObject("commit").get("sha").getAsString();
                meta.add(sha.substring(0, Math.min(7, sha.length())));
            }
            ((TextView) row.findViewById(R.id.setting_summary)).setText(String.join(" · ", meta));
            row.setOnClickListener(v -> showMenu(v, branch));
            list.addView(row);
        }
    }

    @NonNull
    private static String name(@NonNull JsonObject branch) {
        return branch.get("name").getAsString();
    }

    private void showMenu(@NonNull View anchor, @NonNull JsonObject branch) {
        String name = name(branch);
        boolean isDefault = name.equals(defaultBranch);
        boolean isProtected = branch.has("protected") && branch.get("protected").getAsBoolean();
        if (!isAdmin && !canPush) return;
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (isAdmin) popup.getMenu().add(0, 2, 1, R.string.branches_protection);
        if (canPush) popup.getMenu().add(0, 3, 2, R.string.rename);
        if (canPush && !isDefault && !isProtected) popup.getMenu().add(0, 4, 3, R.string.delete);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 2:
                    editProtection(name);
                    break;
                case 3:
                    rename(name);
                    break;
                case 4:
                    delete(name);
                    break;
            }
            return true;
        });
        popup.show();
    }

    // ---------------------------------------------------------------------------------
    // Create, rename, delete
    // ---------------------------------------------------------------------------------

    private void createBranch() {
        String[] sources = new String[branches.size()];
        for (int i = 0; i < branches.size(); i++) sources[i] = name(branches.get(i));
        if (sources.length == 0) return;
        if (sources.length == 1) {
            askNewBranchName(sources[0]);
            return;
        }
        final int[] source = {0};
        new AlertDialog.Builder(this)
                .setTitle(R.string.branches_new_source)
                .setSingleChoiceItems(sources, 0, (dialog, which) -> source[0] = which)
                .setPositiveButton(R.string.ok, (dialog, which) -> askNewBranchName(sources[source[0]]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askNewBranchName(@NonNull String from) {
        TextInputDialog.show(this, R.string.branches_new, R.string.commit_branch_name, "", false, newName -> {
            if (!isValidBranchName(newName)) {
                showErrorToast(getString(R.string.commit_branch_invalid));
                return;
            }
            run(() -> {
                GitDataService git = GitHubApi.service(GitDataService.class);
                JsonObject ref = GitHubApi.call(git.getBranchRef(owner, repo, RepoCommitter.encode(from)));
                Map<String, Object> body = new HashMap<>();
                body.put("ref", "refs/heads/" + newName);
                body.put("sha", ref.getAsJsonObject("object").get("sha").getAsString());
                GitHubApi.call(git.createRef(owner, repo, body));
                return Boolean.TRUE;
            }, getString(R.string.branches_created, newName, from));
        });
    }

    private void rename(@NonNull String branch) {
        TextInputDialog.show(this, R.string.rename, R.string.commit_branch_name, branch, false, newName -> {
            if (newName.equals(branch)) return;
            if (!isValidBranchName(newName)) {
                showErrorToast(getString(R.string.commit_branch_invalid));
                return;
            }
            run(() -> {
                Map<String, Object> body = new HashMap<>();
                body.put("new_name", newName);
                GitHubApi.call(service().renameBranch(owner, repo, RepoCommitter.encode(branch), body));
                return Boolean.TRUE;
            }, getString(R.string.branches_renamed, branch, newName));
        });
    }

    private void delete(@NonNull String branch) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.branches_delete_title)
                .setMessage(getString(R.string.branches_delete_message, branch))
                .setPositiveButton(R.string.delete, (d, w) -> run(() -> {
                    GitHubApi.call(service().deleteBranch(owner, repo, RepoCommitter.encode(branch)));
                    return Boolean.TRUE;
                }, getString(R.string.branches_deleted, branch)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static boolean isValidBranchName(@NonNull String name) {
        if (name.isEmpty() || name.startsWith("/") || name.endsWith("/") || name.endsWith(".")
                || name.endsWith(".lock") || name.contains("..") || name.contains("//")
                || name.contains("@{") || name.startsWith("-")) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (c <= ' ' || c == '~' || c == '^' || c == ':' || c == '?' || c == '*' || c == '['
                    || c == '\\' || c == 127) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------
    // Protection
    // ---------------------------------------------------------------------------------

    private void editProtection(@NonNull String branch) {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> {
            try {
                return GitHubApi.call(service().getProtection(owner, repo, RepoCommitter.encode(branch)));
            } catch (GitHubApi.ApiException e) {
                if (e.code == 404) return new JsonObject(); // not protected
                throw e;
            }
        }, (JsonObject protection) -> {
            dismissProgressDialog();
            showProtectionDialog(branch, protection);
        }, error -> dismissProgressDialog());
    }

    private void showProtectionDialog(@NonNull String branch, @NonNull JsonObject current) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_branch_protection, null);
        SwitchCompat protect = view.findViewById(R.id.protect_switch);
        View options = view.findViewById(R.id.protection_options);
        CheckBox requirePr = view.findViewById(R.id.require_pr_check);
        View prOptions = view.findViewById(R.id.pr_options);
        TextInputEditText approvals = view.findViewById(R.id.approvals_edit);
        CheckBox dismissStale = view.findViewById(R.id.dismiss_stale_check);
        CheckBox conversation = view.findViewById(R.id.conversation_check);
        CheckBox linear = view.findViewById(R.id.linear_check);
        CheckBox forcePush = view.findViewById(R.id.force_push_check);
        CheckBox deletions = view.findViewById(R.id.deletions_check);
        CheckBox enforceAdmins = view.findViewById(R.id.enforce_admins_check);

        boolean wasProtected = current.size() > 0;
        JsonObject reviews = current.has("required_pull_request_reviews")
                ? current.getAsJsonObject("required_pull_request_reviews") : null;
        protect.setChecked(wasProtected);
        requirePr.setChecked(!wasProtected || reviews != null);
        approvals.setText(String.valueOf(reviews != null && reviews.has("required_approving_review_count")
                ? reviews.get("required_approving_review_count").getAsInt() : 0));
        dismissStale.setChecked(reviews != null && reviews.has("dismiss_stale_reviews")
                && reviews.get("dismiss_stale_reviews").getAsBoolean());
        conversation.setChecked(enabled(current, "required_conversation_resolution"));
        linear.setChecked(enabled(current, "required_linear_history"));
        forcePush.setChecked(enabled(current, "allow_force_pushes"));
        deletions.setChecked(enabled(current, "allow_deletions"));
        enforceAdmins.setChecked(enabled(current, "enforce_admins"));

        Runnable refresh = () -> {
            options.setVisibility(protect.isChecked() ? View.VISIBLE : View.GONE);
            prOptions.setVisibility(requirePr.isChecked() ? View.VISIBLE : View.GONE);
        };
        protect.setOnCheckedChangeListener((v, c) -> refresh.run());
        requirePr.setOnCheckedChangeListener((v, c) -> refresh.run());
        refresh.run();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.protection_title, branch))
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    if (!protect.isChecked()) {
                        if (!wasProtected) return;
                        run(() -> {
                            GitHubApi.call(service().removeProtection(owner, repo, RepoCommitter.encode(branch)));
                            return Boolean.TRUE;
                        }, getString(R.string.protection_removed, branch));
                        return;
                    }
                    int count = 0;
                    try {
                        count = Math.max(0, Math.min(6, Integer.parseInt(String.valueOf(approvals.getText()))));
                    } catch (NumberFormatException ignored) {
                    }
                    JsonObject body = new JsonObject();
                    body.add("required_status_checks", statusChecks(current));
                    body.addProperty("enforce_admins", enforceAdmins.isChecked());
                    if (requirePr.isChecked()) {
                        JsonObject pr = new JsonObject();
                        pr.addProperty("required_approving_review_count", count);
                        pr.addProperty("dismiss_stale_reviews", dismissStale.isChecked());
                        body.add("required_pull_request_reviews", pr);
                    } else {
                        body.add("required_pull_request_reviews", JsonNull.INSTANCE);
                    }
                    body.add("restrictions", restrictions(current));
                    body.addProperty("required_linear_history", linear.isChecked());
                    body.addProperty("allow_force_pushes", forcePush.isChecked());
                    body.addProperty("allow_deletions", deletions.isChecked());
                    body.addProperty("required_conversation_resolution", conversation.isChecked());
                    String json = new GsonBuilder().serializeNulls().create().toJson(body);
                    run(() -> {
                        GitHubApi.call(service().setProtection(owner, repo, RepoCommitter.encode(branch),
                                RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"))));
                        return Boolean.TRUE;
                    }, getString(R.string.protection_saved, branch));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static boolean enabled(@NonNull JsonObject protection, @NonNull String key) {
        if (!protection.has(key) || !protection.get(key).isJsonObject()) return false;
        JsonObject value = protection.getAsJsonObject(key);
        return value.has("enabled") && value.get("enabled").getAsBoolean();
    }

    /** Keeps existing required status checks (set on the website) as they are. */
    @NonNull
    private static JsonElement statusChecks(@NonNull JsonObject protection) {
        if (!protection.has("required_status_checks") || !protection.get("required_status_checks").isJsonObject()) {
            return JsonNull.INSTANCE;
        }
        JsonObject checks = protection.getAsJsonObject("required_status_checks");
        JsonObject result = new JsonObject();
        result.addProperty("strict", checks.has("strict") && checks.get("strict").getAsBoolean());
        result.add("contexts", checks.has("contexts") ? checks.get("contexts") : new JsonArray());
        return result;
    }

    /** Keeps existing push restrictions (organization repositories) as they are. */
    @NonNull
    private static JsonElement restrictions(@NonNull JsonObject protection) {
        if (!protection.has("restrictions") || !protection.get("restrictions").isJsonObject()) {
            return JsonNull.INSTANCE;
        }
        JsonObject current = protection.getAsJsonObject("restrictions");
        JsonObject result = new JsonObject();
        result.add("users", names(current, "users", "login"));
        result.add("teams", names(current, "teams", "slug"));
        result.add("apps", names(current, "apps", "slug"));
        return result;
    }

    @NonNull
    private static JsonArray names(@NonNull JsonObject object, @NonNull String key, @NonNull String field) {
        JsonArray result = new JsonArray();
        if (object.has(key) && object.get(key).isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray(key)) {
                if (element.isJsonObject() && element.getAsJsonObject().has(field)) {
                    result.add(element.getAsJsonObject().get(field).getAsString());
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------------------

    /** Runs a change, then reloads the list. */
    private void run(@NonNull Callable<Boolean> work, @NonNull String successMessage) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            RepoChanges.markChanged();
            showSuccessToast(successMessage);
            load();
        }, error -> dismissProgressDialog());
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
        list = root.findViewById(R.id.branch_list);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        newBranchBn = root.findViewById(R.id.new_branch_bn);
        newBranchBn.setOnClickListener(v -> createBranch());
    }
}
