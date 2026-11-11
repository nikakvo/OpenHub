package com.thirtydegreesray.openhub.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.ActionsFragment;
import com.thirtydegreesray.openhub.util.ActionsWatcher;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

/**
 * GitHub Actions of a repository: workflow runs, their build artifacts, a filter by
 * workflow, and (with push access) starting a workflow by hand.
 */
public class ActionsActivity extends SingleFragmentActivity<IBaseContract.Presenter, ActionsFragment> {

    /**
     * @param canRun the user has push access, so workflow_dispatch is allowed
     */
    public static void show(@NonNull Activity activity, @NonNull String owner, @NonNull String repo,
                            @Nullable String defaultBranch, boolean canRun) {
        activity.startActivity(new Intent(activity, ActionsActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo)
                        .put("defaultBranch", defaultBranch).put("canRun", canRun).build()));
    }

    /**
     * From a github.com/owner/repo/actions link: GitHub is asked whether the user may run
     * workflows (the button appears only when GitHub says so).
     */
    @NonNull
    public static Intent createLinkIntent(@NonNull android.content.Context context, @NonNull String owner,
                                          @NonNull String repo) {
        return new Intent(context, ActionsActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo)
                        .put("lookUpAccess", true).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String defaultBranch;
    @AutoAccess boolean canRun;
    @AutoAccess boolean lookUpAccess;
    @Nullable private io.reactivex.rxjava3.disposables.Disposable access;

    private FloatingActionButton runButton;

    /** Workflows screen: "Show runs" comes back with the workflow to filter by. */
    private final ActivityResultLauncher<Intent> workflowsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null || getFragment() == null) return;
                long id = data.getLongExtra(WorkflowsActivity.RESULT_WORKFLOW_ID, 0);
                if (id != 0) {
                    getFragment().selectWorkflow(id, data.getStringExtra(WorkflowsActivity.RESULT_WORKFLOW_NAME));
                }
            });

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarTitle(getString(R.string.actions), owner.concat("/").concat(repo));
        setToolbarScrollAble(true);
        showRunButton();
        if (lookUpAccess) lookUpAccess();
    }

    private void showRunButton() {
        if (runButton != null && canRun) {
            runButton.setImageResource(R.drawable.ic_play_arrow);
            runButton.setContentDescription(getString(R.string.actions_run_workflow));
            runButton.setVisibility(View.VISIBLE);
        }
    }

    /** Push or admin access on GitHub allows "Run workflow"; nothing changes without an answer. */
    private void lookUpAccess() {
        access = io.reactivex.rxjava3.core.Single.fromCallable(() -> com.thirtydegreesray.openhub.http.GitHubApi.call(
                        com.thirtydegreesray.openhub.http.GitHubApi.service(com.thirtydegreesray.openhub.http.RepoService.class)
                                .getRepoInfo(true, owner, repo)))
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(repository -> {
                    com.thirtydegreesray.openhub.mvp.model.RepositoryPermissions p = repository.getPermissions();
                    canRun = p != null && (p.isPush() || p.isAdmin());
                    if (repository.getDefaultBranch() != null) defaultBranch = repository.getDefaultBranch();
                    showRunButton();
                    invalidateOptionsMenu();
                }, error -> { });
    }

    @Override
    protected void onDestroy() {
        if (access != null) access.dispose();
        super.onDestroy();
    }

    @Override
    protected ActionsFragment createFragment() {
        return ActionsFragment.create(owner, repo, defaultBranch);
    }

    public void onWorkflowFilterChanged(@Nullable String workflowName) {
        String subTitle = owner.concat("/").concat(repo);
        if (workflowName != null) subTitle = subTitle.concat(" · ").concat(workflowName);
        setToolbarSubTitle(subTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_successful_only);
        if (item != null && getFragment() != null) item.setChecked(getFragment().isSuccessfulOnly());
        MenuItem run = menu.findItem(R.id.action_run_workflow);
        if (run != null) run.setVisible(canRun);
        MenuItem notify = menu.findItem(R.id.action_notify_runs);
        if (notify != null) notify.setChecked(ActionsWatcher.isWatched(this, owner + "/" + repo));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_filter_workflow) {
            if (getFragment() != null) getFragment().pickWorkflow(false);
            return true;
        } else if (itemId == R.id.action_run_workflow) {
            if (getFragment() != null) getFragment().pickWorkflow(true);
            return true;
        } else if (itemId == R.id.action_workflows || itemId == R.id.action_caches) {
            workflowsLauncher.launch(WorkflowsActivity.createIntent(this, owner, repo, canRun,
                    itemId == R.id.action_caches));
            return true;
        } else if (itemId == R.id.action_successful_only) {
            boolean checked = !item.isChecked();
            item.setChecked(checked);
            if (getFragment() != null) getFragment().setSuccessfulOnly(checked);
            return true;
        } else if (itemId == R.id.action_notify_runs) {
            boolean watch = !item.isChecked();
            item.setChecked(watch);
            ActionsWatcher.setWatched(this, owner + "/" + repo, watch);
            if (watch) {
                askNotificationPermission();
                showSuccessToast(getString(R.string.actions_notify_on, owner + "/" + repo));
            } else {
                showInfoToast(getString(R.string.actions_notify_off, owner + "/" + repo));
            }
            return true;
        } else if (itemId == R.id.action_open_in_browser) {
            AppOpener.openInCustomTabsOrBrowser(this,
                    AppConfig.GITHUB_BASE_URL + owner + "/" + repo + "/actions");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Android 13+: notifications need the permission (asked once, never required). */
    public void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        runButton = root.findViewById(R.id.float_action_bn);
        ViewUtils.setOnClick(root, R.id.float_action_bn, v -> {
            if (getFragment() != null) getFragment().pickWorkflow(true);
        });
    }

}
