package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.http.RepoService;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.CommitFile;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.fragment.CommitFilesFragment;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Compare two branches or tags of a repository, like github.com/owner/repo/compare:
 * how far the second is ahead of and behind the first, its commits, and the changed files
 * with their diffs. With write access, an open pull request can be started from here.
 */
public class CompareActivity extends BaseActivity<IBaseContract.Presenter> {

    /** @param base the branch or tag to compare against (null: the default branch) */
    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, @Nullable String base, @Nullable String head) {
        return new Intent(context, CompareActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("owner", owner)
                        .put("repo", repo)
                        .put("base", base)
                        .put("head", head)
                        .build());
    }

    /** GitHub lists at most this many changed files in a comparison. */
    private static final int MAX_FILES = 300;
    private static final String FILES_TAG = "compareFiles";

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String base;
    @AutoAccess String head;

    private ProgressBar loader;
    private View baseRow;
    private View headRow;
    private TextView statusText;
    private View commitsRow;
    private View pullRow;
    private TextView filesTitle;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private Disposable comparing;
    private final List<String> branches = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private boolean refsLoaded;
    private boolean canPush;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_compare;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.compare_title), owner + "/" + repo);
        ((TextView) baseRow.findViewById(R.id.setting_title)).setText(R.string.compare_base);
        ((TextView) headRow.findViewById(R.id.setting_title)).setText(R.string.compare_head);
        ((TextView) pullRow.findViewById(R.id.setting_title)).setText(R.string.compare_open_pull);
        commitsRow.setVisibility(View.GONE);
        pullRow.setVisibility(View.GONE);
        showChoices();
        loadRefs();
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        loader = root.findViewById(R.id.loader);
        baseRow = root.findViewById(R.id.base_row);
        headRow = root.findViewById(R.id.head_row);
        statusText = root.findViewById(R.id.status_text);
        commitsRow = root.findViewById(R.id.commits_row);
        pullRow = root.findViewById(R.id.pull_row);
        filesTitle = root.findViewById(R.id.files_title);
        baseRow.setOnClickListener(v -> pick(true));
        headRow.setOnClickListener(v -> pick(false));
        root.findViewById(R.id.swap_bn).setOnClickListener(v -> {
            String old = base;
            base = head;
            head = old;
            showChoices();
            compare();
        });
        commitsRow.setOnClickListener(v -> {
            if (base != null && head != null) {
                CommitsListActivity.showForCompare(this, owner, repo, base, head);
            }
        });
        pullRow.setOnClickListener(v -> startActivity(NewPullRequestActivity.createIntent(this, owner, repo)
                .putExtra("targetFullName", owner + "/" + repo)
                .putExtra("base", base)
                .putExtra("head", head)));
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        if (comparing != null) comparing.dispose();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------

    /** Branches, tags, the default branch and whether the user may push. */
    private void loadRefs() {
        loader.setVisibility(View.VISIBLE);
        disposables.add(Single.fromCallable(() -> {
                    JsonObject repoJson = GitHubApi.call(GitHubApi.service(RepoSettingsService.class)
                            .getRepo(owner, repo));
                    RepoService service = GitHubApi.service(RepoService.class);
                    List<Branch> branchList = GitHubApi.call(service.getBranches(owner, repo));
                    List<Branch> tagList;
                    try {
                        tagList = GitHubApi.call(service.getTags(owner, repo));
                    } catch (Exception e) {
                        tagList = new ArrayList<>();
                    }
                    return new Object[]{repoJson, branchList, tagList};
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loader.setVisibility(View.INVISIBLE);
                    JsonObject repoJson = (JsonObject) result[0];
                    canPush = Json.bool(repoJson, "permissions.push") || Json.bool(repoJson, "permissions.admin");
                    branches.clear();
                    tags.clear();
                    //noinspection unchecked
                    for (Branch branch : (List<Branch>) result[1]) branches.add(branch.getName());
                    //noinspection unchecked
                    for (Branch tag : (List<Branch>) result[2]) tags.add(tag.getName());
                    refsLoaded = true;
                    if (base == null) base = Json.str(repoJson, "default_branch");
                    if (head == null) head = firstOther(base);
                    showChoices();
                    compare();
                }, error -> {
                    loader.setVisibility(View.INVISIBLE);
                    showErrorToast(message(error));
                }));
    }

    /** The newest-looking ref that is not {@code base}: another branch, else the newest tag. */
    @Nullable
    private String firstOther(@Nullable String base) {
        for (String branch : branches) if (!branch.equals(base)) return branch;
        for (String tag : tags) if (!tag.equals(base)) return tag;
        return null;
    }

    private void showChoices() {
        ((TextView) baseRow.findViewById(R.id.setting_summary)).setText(label(base));
        ((TextView) headRow.findViewById(R.id.setting_summary)).setText(label(head));
    }

    @NonNull
    private String label(@Nullable String ref) {
        if (ref == null) return getString(R.string.compare_choose);
        if (tags.contains(ref) && !branches.contains(ref)) return getString(R.string.compare_tag, ref);
        return ref;
    }

    private void pick(boolean forBase) {
        if (!refsLoaded) return;
        List<String> names = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String branch : branches) {
            if (seen.add(branch)) {
                names.add(branch);
                labels.add(branch);
            }
        }
        for (String tag : tags) {
            if (seen.add(tag)) {
                names.add(tag);
                labels.add(getString(R.string.compare_tag, tag));
            }
        }
        if (names.isEmpty()) return;
        String current = forBase ? base : head;
        new AlertDialog.Builder(this)
                .setTitle(forBase ? R.string.compare_base : R.string.compare_head)
                .setSingleChoiceItems(labels.toArray(new String[0]), Math.max(-1, names.indexOf(current)),
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (forBase) base = names.get(which);
                            else head = names.get(which);
                            showChoices();
                            compare();
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void compare() {
        if (comparing != null) comparing.dispose();
        commitsRow.setVisibility(View.GONE);
        pullRow.setVisibility(View.GONE);
        filesTitle.setText("");
        showFiles(new ArrayList<>());
        if (base == null || head == null) {
            statusText.setText(R.string.compare_pick_two);
            return;
        }
        if (base.equals(head)) {
            statusText.setText(R.string.compare_same);
            return;
        }
        statusText.setText(R.string.compare_loading);
        loader.setVisibility(View.VISIBLE);
        String basehead = RepoCommitter.encode(base) + "..." + RepoCommitter.encode(head);
        String forBase = base;
        String forHead = head;
        comparing = Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(PullService.class)
                        .compare(owner, repo, basehead)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loader.setVisibility(View.INVISIBLE);
                    if (!forBase.equals(base) || !forHead.equals(head)) return;
                    showResult(result);
                }, error -> {
                    loader.setVisibility(View.INVISIBLE);
                    statusText.setText(message(error));
                });
    }

    private void showResult(@NonNull JsonObject result) {
        int ahead = Json.integer(result, "ahead_by");
        int behind = Json.integer(result, "behind_by");
        String status = Json.str(result, "status");
        if ("identical".equals(status)) {
            statusText.setText(getString(R.string.compare_identical, head, base));
        } else {
            String aheadText = getResources().getQuantityString(R.plurals.compare_ahead, ahead, ahead);
            String behindText = getResources().getQuantityString(R.plurals.compare_behind, behind, behind);
            if (ahead > 0 && behind > 0) {
                statusText.setText(getString(R.string.compare_status_both, head, aheadText, behindText, base));
            } else {
                statusText.setText(getString(R.string.compare_status_one, head,
                        ahead > 0 ? aheadText : behindText, base));
            }
        }

        int total = Json.integer(result, "total_commits");
        if (total > 0) {
            commitsRow.setVisibility(View.VISIBLE);
            ((TextView) commitsRow.findViewById(R.id.setting_title)).setText(
                    getResources().getQuantityString(R.plurals.compare_commits, total, total));
            ((TextView) commitsRow.findViewById(R.id.setting_summary)).setText(lastCommitLine(result));
        }
        // a pull request from one branch into another of this repository
        boolean branchesOnly = branches.contains(base) && branches.contains(head);
        if (canPush && ahead > 0 && branchesOnly) {
            pullRow.setVisibility(View.VISIBLE);
            ((TextView) pullRow.findViewById(R.id.setting_summary)).setText(
                    getString(R.string.compare_open_pull_summary, head, base));
        }

        ArrayList<CommitFile> files = files(result);
        if (files.isEmpty()) {
            filesTitle.setText(total > 0 ? getString(R.string.compare_no_files) : "");
        } else {
            int additions = 0;
            int deletions = 0;
            for (CommitFile file : files) {
                additions += file.getAdditions();
                deletions += file.getDeletions();
            }
            String title = getResources().getQuantityString(R.plurals.compare_files,
                    files.size(), files.size()) + "  +" + additions + "  -" + deletions;
            if (files.size() >= MAX_FILES) title += "  " + getString(R.string.compare_files_limit, MAX_FILES);
            filesTitle.setText(title);
        }
        showFiles(files);
    }

    /** First line of the newest commit's message. */
    @NonNull
    private static String lastCommitLine(@NonNull JsonObject result) {
        if (!result.has("commits") || !result.get("commits").isJsonArray()) return "";
        JsonArray commits = result.getAsJsonArray("commits");
        if (commits.size() == 0) return "";
        String message = Json.str(commits.get(commits.size() - 1).getAsJsonObject(), "commit.message");
        if (StringUtils.isBlank(message)) return "";
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    @NonNull
    private static ArrayList<CommitFile> files(@NonNull JsonObject result) {
        ArrayList<CommitFile> files = new ArrayList<>();
        if (!result.has("files") || !result.get("files").isJsonArray()) return files;
        Gson gson = new Gson();
        for (JsonElement element : result.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            // the app knows four kinds; "copied", "changed" and "unchanged" show as modified
            String status = Json.str(file, "status");
            if (!"added".equals(status) && !"removed".equals(status) && !"renamed".equals(status)) {
                file.addProperty("status", "modified");
            }
            files.add(gson.fromJson(file, CommitFile.class));
        }
        return files;
    }

    private void showFiles(@NonNull ArrayList<CommitFile> files) {
        if (isFinishing() || getSupportFragmentManager().isStateSaved()) return;
        androidx.fragment.app.Fragment old = getSupportFragmentManager().findFragmentByTag(FILES_TAG);
        if (files.isEmpty()) {
            if (old != null) getSupportFragmentManager().beginTransaction().remove(old).commit();
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.files_container, CommitFilesFragment.create(files), FILES_TAG)
                .commit();
    }

    @NonNull
    private static String message(@NonNull Throwable error) {
        return StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
    }
}
