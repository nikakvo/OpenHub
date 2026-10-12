package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.MarkdownWritePreview;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullChanges;
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
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * "New pull request" like the Compare page on github.com: pick the base branch (of
 * this repository or, for a fork, of the repository it was forked from) and the
 * branch with the changes, see how many commits and files differ, then write the
 * title and description and create it, optionally as a draft.
 */
public class NewPullRequestActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, NewPullRequestActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    /** the repository the screen was opened from (where the changes are) */
    @AutoAccess String owner;
    @AutoAccess String repo;
    /** where the pull request goes: this repository or its parent */
    @AutoAccess String targetFullName;
    @AutoAccess String base;
    @AutoAccess String head;

    private View targetRow;
    private TextView targetText;
    private TextView baseText;
    private TextView headText;
    private TextView compareText;
    private TextInputLayout titleLayout;
    private TextInputEditText titleEdit;
    private MarkdownWritePreview description;
    private CheckBox draftCheck;
    private Button createBn;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private Disposable comparing;
    @Nullable private String parentFullName;
    @Nullable private String parentDefaultBranch;
    private String defaultBranch;
    private final List<String> headBranches = new ArrayList<>();
    private Button branchesBn;
    private long branchesStamp;
    private boolean canCreate;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_new_pull;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.pulls_new), owner + "/" + repo);
        description.setHint(getString(R.string.pull_description_hint));
        createBn.setEnabled(false);
        load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        description.dispose();
        super.onDestroy();
    }

    private String self() {
        return owner + "/" + repo;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (branchesStamp != 0 && RepoChanges.changedSince(branchesStamp)) {
            if (head != null && head.equals(base)) head = null;
            load();
        }
    }

    private boolean isCrossRepository() {
        return targetFullName != null && !targetFullName.equalsIgnoreCase(self());
    }

    // ---------------------------------------------------------------------------------
    // Branches
    // ---------------------------------------------------------------------------------

    private void load() {
        branchesStamp = RepoChanges.stamp();
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            RepoSettingsService service = GitHubApi.service(RepoSettingsService.class);
            JsonObject repoJson = GitHubApi.call(service.getRepo(owner, repo));
            List<String> branches = branchesOf(owner, repo);
            return new Object[]{repoJson, branches};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            JsonObject repoJson = (JsonObject) result[0];
            defaultBranch = Json.str(repoJson, "default_branch");
            if (Json.bool(repoJson, "fork") && Json.obj(repoJson, "parent") != null) {
                parentFullName = Json.str(repoJson, "parent.full_name");
                parentDefaultBranch = Json.str(repoJson, "parent.default_branch");
            }
            headBranches.clear();
            //noinspection unchecked
            headBranches.addAll((List<String>) result[1]);
            if (targetFullName == null) {
                // a fork usually sends its changes to the repository it came from
                targetFullName = parentFullName != null ? parentFullName : self();
            }
            if (base == null) base = isCrossRepository() ? parentDefaultBranch : defaultBranch;
            if (head == null) head = pickHead();
            targetRow.setVisibility(parentFullName != null ? View.VISIBLE : View.GONE);
            showChoices();
            compare();
        }, error -> loader.setVisibility(View.GONE));
    }

    /** The most likely branch with changes: for a fork the default branch, else another branch. */
    @NonNull
    private String pickHead() {
        if (isCrossRepository()) return defaultBranch;
        for (String branch : headBranches) if (!branch.equals(base)) return branch;
        return defaultBranch;
    }

    @NonNull
    private static List<String> branchesOf(@NonNull String owner, @NonNull String repo) throws java.io.IOException {
        List<String> names = new ArrayList<>();
        RepoSettingsService service = GitHubApi.service(RepoSettingsService.class);
        for (int page = 1; page <= 5; page++) {
            JsonArray array = GitHubApi.call(service.getBranches(owner, repo, page));
            if (array == null || array.size() == 0) break;
            for (JsonElement e : array) names.add(Json.str(e.getAsJsonObject(), "name"));
            if (array.size() < 100) break;
        }
        return names;
    }

    private void showChoices() {
        targetText.setText(targetFullName);
        baseText.setText(isCrossRepository() ? targetFullName + " : " + base : base);
        headText.setText(isCrossRepository() ? self() + " : " + head : head);
    }

    private void chooseTarget() {
        if (parentFullName == null) return;
        String[] items = {parentFullName, self()};
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_pull_target)
                .setSingleChoiceItems(items, isCrossRepository() ? 0 : 1, (dialog, which) -> {
                    dialog.dismiss();
                    targetFullName = items[which];
                    base = isCrossRepository() ? parentDefaultBranch : defaultBranch;
                    head = pickHead();
                    showChoices();
                    compare();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseBase() {
        String[] parts = targetFullName.split("/", 2);
        loader.setVisibility(View.VISIBLE);
        background(() -> branchesOf(parts[0], parts[1]), (List<String> names) -> {
            loader.setVisibility(View.GONE);
            pickFrom(R.string.new_pull_base, names, base, chosen -> {
                base = chosen;
                showChoices();
                compare();
            });
        }, error -> loader.setVisibility(View.GONE));
    }

    private void chooseHead() {
        pickFrom(R.string.new_pull_head, headBranches, head, chosen -> {
            head = chosen;
            showChoices();
            compare();
        });
    }

    private void pickFrom(int title, @NonNull List<String> names, @Nullable String current,
                          @NonNull Consumer<String> onPick) {
        String[] items = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, current == null ? -1 : names.indexOf(current), (dialog, which) -> {
                    dialog.dismiss();
                    onPick.accept(items[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // Compare and create
    // ---------------------------------------------------------------------------------

    private void compare() {
        canCreate = false;
        createBn.setEnabled(false);
        if (comparing != null) comparing.dispose();
        branchesBn.setVisibility(View.GONE);
        if (base == null || head == null) return;
        if (!isCrossRepository() && base.equals(head)) {
            compareText.setText(headBranches.size() <= 1
                    ? getString(R.string.new_pull_one_branch, base) : getString(R.string.new_pull_same_branch));
            branchesBn.setVisibility(View.VISIBLE);
            return;
        }
        compareText.setText(R.string.new_pull_comparing);
        String[] target = targetFullName.split("/", 2);
        String basehead = RepoCommitter.encode(base) + "..."
                + (isCrossRepository() ? owner + ":" : "") + RepoCommitter.encode(head);
        comparing = Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(PullService.class)
                        .compare(target[0], target[1], basehead)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    int ahead = Json.integer(result, "ahead_by");
                    int files = result.has("files") && result.get("files").isJsonArray()
                            ? result.getAsJsonArray("files").size() : 0;
                    if (ahead == 0) {
                        compareText.setText(getString(R.string.new_pull_nothing, base, head));
                        branchesBn.setVisibility(isCrossRepository() ? View.GONE : View.VISIBLE);
                        return;
                    }
                    compareText.setText(getResources().getQuantityString(R.plurals.new_pull_compare,
                            ahead, ahead, files));
                    if (titleEdit.getText() == null || titleEdit.getText().length() == 0) {
                        titleEdit.setText(suggestTitle(result, ahead));
                    }
                    canCreate = true;
                    createBn.setEnabled(true);
                }, error -> compareText.setText(StringUtils.isBlank(error.getMessage())
                        ? error.toString() : error.getMessage()));
        disposables.add(comparing);
    }

    /** One commit: its message, like github.com; more: the branch name made readable. */
    @NonNull
    private String suggestTitle(@NonNull JsonObject compare, int ahead) {
        if (ahead == 1 && compare.has("commits") && compare.getAsJsonArray("commits").size() == 1) {
            String message = Json.str(compare.getAsJsonArray("commits").get(0).getAsJsonObject(), "commit.message");
            int newline = message.indexOf('\n');
            return newline > 0 ? message.substring(0, newline) : message;
        }
        String name = head.replace('-', ' ').replace('_', ' ');
        return name.isEmpty() ? head : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void create() {
        if (!canCreate) return;
        String title = titleEdit.getText() == null ? "" : titleEdit.getText().toString().trim();
        if (title.isEmpty()) {
            titleLayout.setError(getString(R.string.field_required));
            return;
        }
        titleLayout.setErrorEnabled(false);
        String[] target = targetFullName.split("/", 2);
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("base", base);
        body.put("head", isCrossRepository() ? owner + ":" + head : head);
        String text = description.getText().trim();
        if (!text.isEmpty()) body.put("body", text);
        body.put("draft", draftCheck.isChecked());
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GitHubApi.call(GitHubApi.service(PullService.class).createPull(target[0], target[1], body)),
                (JsonObject created) -> {
                    dismissProgressDialog();
                    PullChanges.markChanged();
                    showSuccessToast(getString(R.string.new_pull_created, Json.integer(created, "number")));
                    startActivity(PullRequestActivity.createIntent(this, target[0], target[1],
                            Json.integer(created, "number")));
                    finish();
                }, error -> dismissProgressDialog());
    }

    // ---------------------------------------------------------------------------------

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
        targetRow = root.findViewById(R.id.target_row);
        targetText = root.findViewById(R.id.target_text);
        baseText = root.findViewById(R.id.base_text);
        headText = root.findViewById(R.id.head_text);
        compareText = root.findViewById(R.id.compare_text);
        branchesBn = root.findViewById(R.id.branches_bn);
        branchesBn.setOnClickListener(v -> startActivity(BranchesActivity.createIntent(this, owner, repo)));
        titleLayout = root.findViewById(R.id.title_layout);
        titleEdit = root.findViewById(R.id.title_edit);
        draftCheck = root.findViewById(R.id.draft_check);
        createBn = root.findViewById(R.id.create_bn);
        loader = root.findViewById(R.id.loader);
        description = new MarkdownWritePreview(root, owner + "/" + repo);
        targetRow.setOnClickListener(v -> chooseTarget());
        root.findViewById(R.id.base_row).setOnClickListener(v -> chooseBase());
        root.findViewById(R.id.head_row).setOnClickListener(v -> chooseHead());
        createBn.setOnClickListener(v -> create());
    }
}
