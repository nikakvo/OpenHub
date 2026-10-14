package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.ActionsService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.Artifact;
import com.thirtydegreesray.openhub.mvp.model.ArtifactsResult;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.ActionsWatcher;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.RunChanges;
import com.thirtydegreesray.openhub.util.RunStatus;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * One workflow run like on github.com: status and duration, every job with its steps
 * (tap a job for its log), artifacts; re-run (all or failed jobs, one job, optionally
 * with debug logging) and cancel. Updates itself every few seconds while it runs.
 */
public class WorkflowRunActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, long runId) {
        return new Intent(context, WorkflowRunActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("runId", runId).build());
    }

    private static final long POLL_MS = 5000;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess long runId;

    private SwipeRefreshLayout refreshLayout;
    private TextView titleText;
    private View statusDot;
    private TextView statusText;
    private TextView durationText;
    private TextView metaText;
    private LinearLayout jobsList;
    private TextView artifactsTitle;
    private LinearLayout artifactsList;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = () -> load(false);
    private boolean resumed;
    private boolean loading;

    @Nullable private JsonObject run;
    private final List<JsonObject> jobs = new ArrayList<>();
    private final List<Artifact> artifacts = new ArrayList<>();

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_workflow_run;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.actions), owner + "/" + repo);
        refreshLayout.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(this));
        refreshLayout.setOnRefreshListener(() -> load(true));
        load(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (run != null && !isCompleted()) schedulePoll();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        disposables.clear();
        super.onDestroy();
    }

    private ActionsService service() {
        return GitHubApi.service(ActionsService.class);
    }

    private boolean isCompleted() {
        return run != null && "completed".equals(Json.str(run, "status"));
    }

    private void schedulePoll() {
        handler.removeCallbacks(poll);
        if (resumed) handler.postDelayed(poll, POLL_MS);
    }

    // ---------------------------------------------------------------------------------
    // Load and show
    // ---------------------------------------------------------------------------------

    private void load(boolean showProgress) {
        if (loading) return;
        loading = true;
        if (showProgress) refreshLayout.setRefreshing(true);
        background(() -> {
            JsonObject runJson = GitHubApi.call(service().getRun(owner, repo, runId));
            JsonObject jobsJson = GitHubApi.call(service().getJobs(owner, repo, runId));
            ArtifactsResult artifactsResult = null;
            try {
                artifactsResult = GitHubApi.call(GitHubApi.service(RepoService.class)
                        .getRunArtifacts(true, owner, repo, runId));
            } catch (Exception ignored) {
                // artifacts are optional
            }
            return new Object[]{runJson, jobsJson, artifactsResult};
        }, (Object[] result) -> {
            loading = false;
            refreshLayout.setRefreshing(false);
            run = (JsonObject) result[0];
            jobs.clear();
            JsonElement list = Json.get((JsonObject) result[1], "jobs");
            if (list != null && list.isJsonArray()) {
                for (JsonElement element : list.getAsJsonArray()) {
                    if (element.isJsonObject()) jobs.add(element.getAsJsonObject());
                }
            }
            artifacts.clear();
            if (result[2] != null) artifacts.addAll(((ArtifactsResult) result[2]).getArtifacts());
            render();
            invalidateOptionsMenu();
            if (!isCompleted()) schedulePoll();
        }, error -> {
            loading = false;
            refreshLayout.setRefreshing(false);
            if (run != null && !isCompleted()) schedulePoll();
        }, showProgress);
    }

    private void render() {
        if (run == null) return;
        String name = Json.str(run, "name");
        String title = Json.str(run, "display_title");
        titleText.setText(StringUtils.isBlank(title) ? name : title);

        RunStatus status = RunStatus.of(Json.str(run, "status"), Json.str(run, "conclusion"));
        status.paintDot(statusDot);
        status.paintLabel(statusText);
        Date started = Json.date(run, "run_started_at");
        durationText.setText(RunStatus.duration(started, isCompleted() ? Json.date(run, "updated_at") : null));

        List<String> meta = new ArrayList<>();
        meta.add((StringUtils.isBlank(name) ? "" : name + " ") + "#" + Json.integer(run, "run_number"));
        int attempt = Json.integer(run, "run_attempt");
        if (attempt > 1) meta.add(getString(R.string.run_attempt, attempt));
        if (!StringUtils.isBlank(Json.str(run, "head_branch"))) meta.add(Json.str(run, "head_branch"));
        if (!StringUtils.isBlank(Json.str(run, "event"))) meta.add(Json.str(run, "event"));
        String sha = Json.str(run, "head_sha");
        if (!StringUtils.isBlank(sha)) meta.add(sha.substring(0, Math.min(7, sha.length())));
        String actor = Json.str(run, "triggering_actor.login");
        if (StringUtils.isBlank(actor)) actor = Json.str(run, "actor.login");
        if (!StringUtils.isBlank(actor)) meta.add(getString(R.string.run_by, actor));
        metaText.setText(String.join(" · ", meta));

        renderJobs();
        renderArtifacts();
    }

    private void renderJobs() {
        jobsList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (jobs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.run_no_jobs);
            int pad = getResources().getDimensionPixelSize(R.dimen.spacing_large);
            empty.setPadding(pad, 0, pad, 0);
            jobsList.addView(empty);
            return;
        }
        for (JsonObject job : jobs) {
            View row = inflater.inflate(R.layout.layout_item_run_job, jobsList, false);
            RunStatus status = RunStatus.of(Json.str(job, "status"), Json.str(job, "conclusion"));
            status.paintDot(row.findViewById(R.id.job_status_dot));
            ((TextView) row.findViewById(R.id.job_name)).setText(Json.str(job, "name"));
            boolean done = "completed".equals(Json.str(job, "status"));
            ((TextView) row.findViewById(R.id.job_duration)).setText(
                    RunStatus.duration(Json.date(job, "started_at"), done ? Json.date(job, "completed_at") : null));
            View header = row.findViewById(R.id.job_header);
            header.setOnClickListener(v -> openLog(job));
            header.setOnLongClickListener(v -> {
                showJobMenu(v, job);
                return true;
            });

            LinearLayout steps = row.findViewById(R.id.job_steps);
            JsonElement stepList = Json.get(job, "steps");
            if (stepList != null && stepList.isJsonArray()) {
                for (JsonElement element : stepList.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject step = element.getAsJsonObject();
                    View stepRow = inflater.inflate(R.layout.layout_item_run_step, steps, false);
                    RunStatus stepStatus = RunStatus.of(Json.str(step, "status"), Json.str(step, "conclusion"));
                    stepStatus.paintDot(stepRow.findViewById(R.id.step_status_dot));
                    TextView stepName = stepRow.findViewById(R.id.step_name);
                    stepName.setText(Json.integer(step, "number") + ". " + Json.str(step, "name"));
                    if (stepStatus.failed()) stepName.setTextColor(RunStatus.RED);
                    boolean stepDone = "completed".equals(Json.str(step, "status"));
                    ((TextView) stepRow.findViewById(R.id.step_duration)).setText(
                            "skipped".equals(Json.str(step, "conclusion")) ? "" : RunStatus.duration(
                                    Json.date(step, "started_at"),
                                    stepDone ? Json.date(step, "completed_at") : null));
                    stepRow.setOnClickListener(v -> openLog(job));
                    steps.addView(stepRow);
                }
            }
            jobsList.addView(row);
        }
    }

    private void renderArtifacts() {
        artifactsList.removeAllViews();
        artifactsTitle.setVisibility(artifacts.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Artifact artifact : artifacts) {
            View row = inflater.inflate(R.layout.layout_item_artifact, artifactsList, false);
            ((TextView) row.findViewById(R.id.artifact_name)).setText(artifact.getName());
            ((TextView) row.findViewById(R.id.artifact_size)).setText(artifact.isExpired()
                    ? getString(R.string.actions_artifact_expired)
                    : StringUtils.getSizeString(artifact.getSizeInBytes()));
            ImageView download = row.findViewById(R.id.artifact_download);
            download.setVisibility(artifact.isExpired() ? View.INVISIBLE : View.VISIBLE);
            row.setAlpha(artifact.isExpired() ? 0.5f : 1f);
            if (!artifact.isExpired()) {
                row.setBackgroundResource(outValueSelectable());
                row.setOnClickListener(v -> downloadArtifact(artifact));
            }
            artifactsList.addView(row);
        }
    }

    private int outValueSelectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private void downloadArtifact(@NonNull Artifact artifact) {
        String fileName = repo + "-" + artifact.getName() + "-run" + Json.integer(run, "run_number") + ".zip";
        AppOpener.startDownload(this, artifact.getArchiveDownloadUrl(), fileName);
    }

    private void openLog(@NonNull JsonObject job) {
        JsonElement id = Json.get(job, "id");
        if (id == null) return;
        startActivity(JobLogActivity.createIntent(this, owner, repo, id.getAsLong(),
                Json.str(job, "name"), Json.str(job, "html_url")));
    }

    private void showJobMenu(@NonNull View anchor, @NonNull JsonObject job) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.run_log);
        if ("completed".equals(Json.str(job, "status")) && isCompleted()) {
            popup.getMenu().add(0, 2, 1, R.string.run_rerun_job);
        }
        popup.getMenu().add(0, 3, 2, R.string.open_in_browser);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openLog(job);
            } else if (item.getItemId() == 2) {
                JsonElement id = Json.get(job, "id");
                if (id != null) confirmRerun(id.getAsLong(), false);
            } else if (item.getItemId() == 3) {
                AppOpener.openInCustomTabsOrBrowser(this, Json.str(job, "html_url"));
            }
            return true;
        });
        popup.show();
    }

    // ---------------------------------------------------------------------------------
    // Re-run and cancel
    // ---------------------------------------------------------------------------------

    /** @param jobId a single job, or 0 for the run ({@code failedOnly} for failed jobs) */
    private void confirmRerun(long jobId, boolean failedOnly) {
        CheckBox debug = new CheckBox(new AlertDialog.Builder(this).getContext());
        debug.setText(R.string.run_debug_logging);
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_large);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(debug);
        new AlertDialog.Builder(this)
                .setTitle(R.string.run_rerun_title)
                .setMessage(R.string.run_rerun_message)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(jobId != 0 ? R.string.run_rerun_job
                        : failedOnly ? R.string.run_rerun_failed : R.string.run_rerun_all, (d, w) -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("enable_debug_logging", debug.isChecked());
                    act(() -> {
                        if (jobId != 0) GitHubApi.call(service().rerunJob(owner, repo, jobId, body));
                        else if (failedOnly) GitHubApi.call(service().rerunFailed(owner, repo, runId, body));
                        else GitHubApi.call(service().rerun(owner, repo, runId, body));
                        return Boolean.TRUE;
                    }, R.string.run_rerun_started, true);
                })
                .show();
    }

    private void confirmCancel(boolean force) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.run_cancel_title)
                .setMessage(force ? R.string.run_force_cancel_message : R.string.run_cancel_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(force ? R.string.run_force_cancel : R.string.run_cancel, (d, w) ->
                        act(() -> {
                            GitHubApi.call(force ? service().forceCancel(owner, repo, runId)
                                    : service().cancel(owner, repo, runId));
                            return Boolean.TRUE;
                        }, R.string.run_cancel_requested, false))
                .show();
    }

    private void act(@NonNull Callable<Boolean> work, int doneText, boolean rerun) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            showSuccessToast(getString(doneText));
            RunChanges.markChanged();
            if (rerun) ActionsWatcher.followOnce(this, owner + "/" + repo);
            // GitHub needs a moment before the run shows the new state
            handler.postDelayed(() -> load(true), 2500);
        }, error -> dismissProgressDialog(), true);
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.refresh);
        menu.add(0, 2, 1, R.string.run_rerun_all);
        menu.add(0, 3, 2, R.string.run_rerun_failed);
        menu.add(0, 4, 3, R.string.run_cancel);
        menu.add(0, 5, 4, R.string.run_force_cancel);
        menu.add(0, 6, 5, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean completed = isCompleted();
        String conclusion = run == null ? null : Json.str(run, "conclusion");
        boolean anyFailed = !"success".equals(conclusion) && !"skipped".equals(conclusion);
        menu.findItem(2).setVisible(run != null && completed);
        menu.findItem(3).setVisible(run != null && completed && anyFailed);
        menu.findItem(4).setVisible(run != null && !completed);
        menu.findItem(5).setVisible(run != null && !completed);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                load(true);
                return true;
            case 2:
                confirmRerun(0, false);
                return true;
            case 3:
                confirmRerun(0, true);
                return true;
            case 4:
                confirmCancel(false);
                return true;
            case 5:
                confirmCancel(true);
                return true;
            case 6:
                String url = run == null ? "https://github.com/" + owner + "/" + repo + "/actions/runs/" + runId
                        : Json.str(run, "html_url");
                AppOpener.openInCustomTabsOrBrowser(this, url);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------------------------

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError, boolean showErrors) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    if (showErrors || !(error instanceof java.io.IOException)
                            || error instanceof GitHubApi.ApiException) {
                        showErrorToast(StringUtils.isBlank(error.getMessage())
                                ? error.toString() : error.getMessage());
                    }
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        refreshLayout = root.findViewById(R.id.refresh_layout);
        titleText = root.findViewById(R.id.run_title);
        statusDot = root.findViewById(R.id.run_status_dot);
        statusText = root.findViewById(R.id.run_status);
        durationText = root.findViewById(R.id.run_duration);
        metaText = root.findViewById(R.id.run_meta);
        jobsList = root.findViewById(R.id.jobs_list);
        artifactsTitle = root.findViewById(R.id.artifacts_title);
        artifactsList = root.findViewById(R.id.artifacts_list);
    }
}
