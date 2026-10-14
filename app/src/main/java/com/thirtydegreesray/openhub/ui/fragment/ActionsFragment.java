package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IActionsContract;
import com.thirtydegreesray.openhub.mvp.model.Artifact;
import com.thirtydegreesray.openhub.mvp.model.Workflow;
import com.thirtydegreesray.openhub.mvp.model.WorkflowRun;
import com.thirtydegreesray.openhub.mvp.presenter.ActionsPresenter;
import com.thirtydegreesray.openhub.ui.activity.ActionsActivity;
import com.thirtydegreesray.openhub.ui.activity.WorkflowRunActivity;
import com.thirtydegreesray.openhub.ui.adapter.ActionsAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.RunChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.WindowUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow runs of a repository. Tap a run to see and download its artifacts,
 * long tap to open it on GitHub.
 */
public class ActionsFragment extends ListFragment<ActionsPresenter, ActionsAdapter>
        implements IActionsContract.View {

    public static ActionsFragment create(@NonNull String owner, @NonNull String repo,
                                         @Nullable String defaultBranch) {
        ActionsFragment fragment = new ActionsFragment();
        fragment.setArguments(BundleHelper.builder().put("owner", owner).put("repo", repo)
                .put("defaultBranch", defaultBranch).build());
        return fragment;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_list;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {
        DaggerFragmentComponent.builder()
                .appComponent(appComponent)
                .fragmentModule(new FragmentModule(this))
                .build()
                .inject(this);
    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        super.initFragment(savedInstanceState);
        setLoadMoreEnable(true);
    }

    @Override
    public void showRuns(ArrayList<WorkflowRun> runs) {
        adapter.setData(runs);
        postNotifyDataSetChanged();
    }

    @Override
    protected void onReLoadData() {
        mPresenter.loadRuns(1, true);
    }

    @Override
    protected void onLoadMore(int page) {
        super.onLoadMore(page);
        mPresenter.loadRuns(page, false);
    }

    @Override
    protected String getEmptyTip() {
        return getString(R.string.actions_no_runs);
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        WorkflowRun run = adapter.getData().get(position);
        startActivity(WorkflowRunActivity.createIntent(requireContext(), mPresenter.getOwner(),
                mPresenter.getRepoName(), run.getId()));
    }

    @Override
    protected long changeStamp() {
        // re-run or cancelled on the run screen
        return RunChanges.stamp();
    }

    @Override
    protected long autoRefreshAfterMs() {
        return 60 * 1000L;
    }

    @Override
    public boolean onItemLongClick(int position, @NonNull View view) {
        AppOpener.openInCustomTabsOrBrowser(getActivity(), adapter.getData().get(position).getHtmlUrl());
        return true;
    }

    public void setSuccessfulOnly(boolean successfulOnly) {
        if (mPresenter != null) mPresenter.setSuccessfulOnly(successfulOnly);
    }

    public boolean isSuccessfulOnly() {
        return mPresenter != null && mPresenter.isSuccessfulOnly();
    }

    @Override
    public void showArtifacts(final WorkflowRun run, final ArrayList<Artifact> artifacts) {
        if (getActivity() == null) return;
        String title = StringUtils.isBlank(run.getName()) ? "#" + run.getRunNumber()
                : run.getName() + " #" + run.getRunNumber();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.open_in_browser, (dialog, which) ->
                        AppOpener.openInCustomTabsOrBrowser(getActivity(), run.getHtmlUrl()));
        if (artifacts.isEmpty()) {
            builder.setMessage(R.string.actions_no_artifacts);
        } else {
            builder.setAdapter(new ArtifactListAdapter(artifacts),
                    (dialog, which) -> downloadArtifact(run, artifacts.get(which)));
        }
        builder.show();
    }

    /** Artifact rows: name, size (or "expired") and a download arrow. */
    private class ArtifactListAdapter extends BaseAdapter {
        private final ArrayList<Artifact> artifacts;

        ArtifactListAdapter(ArrayList<Artifact> artifacts) {
            this.artifacts = artifacts;
        }

        @Override
        public int getCount() {
            return artifacts.size();
        }

        @Override
        public Object getItem(int position) {
            return artifacts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return artifacts.get(position).getId();
        }

        @Override
        public boolean isEnabled(int position) {
            return !artifacts.get(position).isExpired();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView : LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_item_artifact, parent, false);
            Artifact artifact = artifacts.get(position);
            ((TextView) row.findViewById(R.id.artifact_name)).setText(artifact.getName());
            ((TextView) row.findViewById(R.id.artifact_size)).setText(artifact.isExpired()
                    ? getString(R.string.actions_artifact_expired)
                    : StringUtils.getSizeString(artifact.getSizeInBytes()));
            ImageView download = row.findViewById(R.id.artifact_download);
            download.setVisibility(artifact.isExpired() ? View.INVISIBLE : View.VISIBLE);
            row.setAlpha(artifact.isExpired() ? 0.5f : 1f);
            return row;
        }
    }

    // ---- workflows ----

    public void pickWorkflow(boolean forRun) {
        if (mPresenter != null) mPresenter.pickWorkflow(forRun);
    }

    @Override
    public void showWorkflowPicker(final ArrayList<Workflow> workflows, final boolean forRun) {
        if (getActivity() == null) return;
        if (workflows.isEmpty()) {
            showInfoToast(getString(R.string.actions_no_workflows));
            return;
        }
        int offset = forRun ? 0 : 1;
        String[] items = new String[workflows.size() + offset];
        if (!forRun) items[0] = getString(R.string.actions_all_workflows);
        for (int i = 0; i < workflows.size(); i++) {
            Workflow workflow = workflows.get(i);
            String label = StringUtils.isBlank(workflow.getName()) ? workflow.getFileName() : workflow.getName();
            if (!workflow.isActive()) label += " (" + getString(R.string.actions_workflow_disabled) + ")";
            items[i + offset] = label;
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(forRun ? R.string.actions_run_workflow : R.string.actions_filter_workflow)
                .setItems(items, (dialog, which) -> {
                    if (!forRun && which == 0) {
                        mPresenter.selectWorkflow(null);
                    } else if (forRun) {
                        showRunDialog(workflows.get(which));
                    } else {
                        mPresenter.selectWorkflow(workflows.get(which - offset));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRunDialog(final Workflow workflow) {
        int padding = WindowUtil.dipToPx(getActivity(), 20);
        LinearLayout content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);

        final EditText refEt = new EditText(getActivity());
        refEt.setSingleLine(true);
        refEt.setHint(R.string.actions_branch);
        refEt.setText(mPresenter.getDefaultBranch());
        content.addView(refEt);

        final EditText inputsEt = new EditText(getActivity());
        inputsEt.setHint(R.string.actions_inputs);
        inputsEt.setMinLines(2);
        inputsEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        content.addView(inputsEt);

        String title = StringUtils.isBlank(workflow.getName()) ? workflow.getFileName() : workflow.getName();
        new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.actions_run, (dialog, which) -> {
                    String ref = refEt.getText().toString().trim();
                    if (ref.isEmpty()) ref = mPresenter.getDefaultBranch();
                    if (StringUtils.isBlank(ref)) ref = "main";
                    mPresenter.dispatchWorkflow(workflow, ref, parseInputs(inputsEt.getText().toString()));
                })
                .show();
    }

    private static Map<String, String> parseInputs(String text) {
        Map<String, String> inputs = new LinkedHashMap<>();
        for (String line : text.split("\\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            if (!key.isEmpty()) inputs.put(key, line.substring(eq + 1).trim());
        }
        return inputs;
    }

    @Override
    public void onWorkflowFilterChanged(@Nullable String workflowName) {
        if (getActivity() instanceof ActionsActivity) {
            ((ActionsActivity) getActivity()).onWorkflowFilterChanged(workflowName);
        }
    }

    @Override
    public void onWorkflowDispatched(@NonNull String workflowName, @NonNull String ref) {
        showSuccessToast(getString(R.string.actions_workflow_started, workflowName, ref));
        if (getContext() != null) {
            // announce the end of the run, even if the user leaves the app
            com.thirtydegreesray.openhub.util.ActionsWatcher.followOnce(getContext(),
                    mPresenter.getOwner() + "/" + mPresenter.getRepoName());
            if (getActivity() instanceof ActionsActivity) {
                ((ActionsActivity) getActivity()).askNotificationPermission();
            }
        }
        // the new run shows up in the API after a few seconds
        if (recyclerView != null) {
            recyclerView.postDelayed(() -> {
                if (mPresenter != null && isAdded()) mPresenter.loadRuns(1, true);
            }, 4000);
        }
    }

    private void downloadArtifact(WorkflowRun run, Artifact artifact) {
        if (artifact.isExpired()) {
            showWarningToast(getString(R.string.actions_artifact_expired));
            return;
        }
        String fileName = mPresenter.getRepoName() + "-" + artifact.getName()
                + "-run" + run.getRunNumber() + ".zip";
        AppOpener.startDownload(getActivity(), artifact.getArchiveDownloadUrl(), fileName);
    }

}
