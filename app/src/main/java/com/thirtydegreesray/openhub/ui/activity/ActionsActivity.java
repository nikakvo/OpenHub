package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.ActionsFragment;
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

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String defaultBranch;
    @AutoAccess boolean canRun;

    private FloatingActionButton runButton;

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarTitle(getString(R.string.actions), owner.concat("/").concat(repo));
        setToolbarScrollAble(true);
        if (runButton != null && canRun) {
            runButton.setImageResource(R.drawable.ic_play_arrow);
            runButton.setContentDescription(getString(R.string.actions_run_workflow));
            runButton.setVisibility(View.VISIBLE);
        }
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
        } else if (itemId == R.id.action_successful_only) {
            boolean checked = !item.isChecked();
            item.setChecked(checked);
            if (getFragment() != null) getFragment().setSuccessfulOnly(checked);
            return true;
        } else if (itemId == R.id.action_open_in_browser) {
            AppOpener.openInCustomTabsOrBrowser(this,
                    AppConfig.GITHUB_BASE_URL + owner + "/" + repo + "/actions");
            return true;
        }
        return super.onOptionsItemSelected(item);
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
