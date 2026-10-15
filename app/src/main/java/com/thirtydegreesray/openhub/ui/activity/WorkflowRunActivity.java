package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
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
import android.widget.ProgressBar;
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
import com.thirtydegreesray.openhub.util.ArtifactFiles;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.RunChanges;
import com.thirtydegreesray.openhub.util.RunStatus;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.io.File;
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
    /** a cancel was sent; GitHub takes a while (up to minutes) to stop the jobs */
    private boolean cancelRequested;

    // live view while the run is going: pulsing dots of running items, ticking durations
    private ProgressBar runProgress;
    private final List<View> pulsing = new ArrayList<>();
    private final List<TextView> tickingViews = new ArrayList<>();
    private final List<Date> tickingStarts = new ArrayList<>();
    @Nullable private ValueAnimator pulse;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < tickingViews.size(); i++) {
                tickingViews.get(i).setText(RunStatus.duration(tickingStarts.get(i), null));
            }
            if (!tickingViews.isEmpty() && resumed) handler.postDelayed(this, 1000);
        }
    };

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
        startLive();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(poll);
        stopLive();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        stopLive();
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

        pulsing.clear();
        tickingViews.clear();
        tickingStarts.clear();
        boolean running = !isCompleted();
        runProgress.setVisibility(running ? View.VISIBLE : View.GONE);

        RunStatus status = RunStatus.of(Json.str(run, "status"), Json.str(run, "conclusion"));
        status.paintDot(statusDot);
        status.paintLabel(statusText);
        String now = runningStepName();
        if (running && now != null) statusText.setText(getString(R.string.run_now, getString(status.label), now));
        if (running && cancelRequested) statusText.setText(R.string.run_cancelling);
        if (!running) cancelRequested = false;
        statusDot.setAlpha(1f);
        if (running) pulsing.add(statusDot);
        Date started = Json.date(run, "run_started_at");
        durationText.setText(RunStatus.duration(started, running ? null : Json.date(run, "updated_at")));
        if (running && started != null) ticking(durationText, started);

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
        startLive();
    }

    /** "Build kernel" while that step runs (of the first running job), else null. */
    @Nullable
    private String runningStepName() {
        for (JsonObject job : jobs) {
            if (!"in_progress".equals(Json.str(job, "status"))) continue;
            JsonElement steps = Json.get(job, "steps");
            if (steps == null || !steps.isJsonArray()) return Json.str(job, "name");
            for (JsonElement element : steps.getAsJsonArray()) {
                if (element.isJsonObject() && "in_progress".equals(Json.str(element.getAsJsonObject(), "status"))) {
                    return Json.str(element.getAsJsonObject(), "name");
                }
            }
            return Json.str(job, "name");
        }
        return null;
    }

    private void ticking(@NonNull TextView view, @NonNull Date start) {
        tickingViews.add(view);
        tickingStarts.add(start);
    }

    private void startLive() {
        handler.removeCallbacks(tick);
        if (!resumed) return;
        if (!tickingViews.isEmpty()) handler.postDelayed(tick, 1000);
        if (pulsing.isEmpty()) {
            if (pulse != null) pulse.cancel();
            return;
        }
        if (pulse == null) {
            pulse = ValueAnimator.ofFloat(1f, 0.25f);
            pulse.setDuration(700);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(animation -> {
                float alpha = (float) animation.getAnimatedValue();
                for (View view : pulsing) view.setAlpha(alpha);
            });
        }
        if (!pulse.isStarted()) pulse.start();
    }

    private void stopLive() {
        handler.removeCallbacks(tick);
        if (pulse != null) pulse.cancel();
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
            View jobDot = row.findViewById(R.id.job_status_dot);
            status.paintDot(jobDot);
            ((TextView) row.findViewById(R.id.job_name)).setText(Json.str(job, "name"));
            boolean done = "completed".equals(Json.str(job, "status"));
            if (!done) pulsing.add(jobDot);
            TextView jobDuration = row.findViewById(R.id.job_duration);
            Date jobStart = Json.date(job, "started_at");
            jobDuration.setText(RunStatus.duration(jobStart, done ? Json.date(job, "completed_at") : null));
            if (!done && jobStart != null) ticking(jobDuration, jobStart);
            View header = row.findViewById(R.id.job_header);
            header.setOnClickListener(v -> openLog(job));
            header.setOnLongClickListener(v -> {
                showJobMenu(v, job);
                return true;
            });

            LinearLayout steps = row.findViewById(R.id.job_steps);
            JsonElement stepList = Json.get(job, "steps");
            if (!done && (stepList == null || !stepList.isJsonArray() || stepList.getAsJsonArray().size() == 0)) {
                TextView waiting = new TextView(this);
                waiting.setText(R.string.run_job_waiting);
                waiting.setTextColor(RunStatus.AMBER);
                steps.addView(waiting);
            }
            if (stepList != null && stepList.isJsonArray()) {
                for (JsonElement element : stepList.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject step = element.getAsJsonObject();
                    View stepRow = inflater.inflate(R.layout.layout_item_run_step, steps, false);
                    RunStatus stepStatus = RunStatus.of(Json.str(step, "status"), Json.str(step, "conclusion"));
                    View stepDot = stepRow.findViewById(R.id.step_status_dot);
                    stepStatus.paintDot(stepDot);
                    TextView stepName = stepRow.findViewById(R.id.step_name);
                    stepName.setText(Json.integer(step, "number") + ". " + Json.str(step, "name"));
                    if (stepStatus.failed()) stepName.setTextColor(RunStatus.RED);
                    boolean stepRunning = "in_progress".equals(Json.str(step, "status"));
                    if (stepRunning) {
                        // the step that runs now: bold, amber, pulsing, its time ticking
                        stepName.setTypeface(stepName.getTypeface(), Typeface.BOLD);
                        stepName.setTextColor(RunStatus.AMBER);
                        pulsing.add(stepDot);
                    } else if (!"completed".equals(Json.str(step, "status"))) {
                        stepDot.setAlpha(0.4f); // waiting
                    }
                    boolean stepDone = "completed".equals(Json.str(step, "status"));
                    TextView stepDuration = stepRow.findViewById(R.id.step_duration);
                    Date stepStart = Json.date(step, "started_at");
                    stepDuration.setText("skipped".equals(Json.str(step, "conclusion")) ? ""
                            : RunStatus.duration(stepStart, stepDone ? Json.date(step, "completed_at") : null));
                    if (stepRunning && stepStart != null) ticking(stepDuration, stepStart);
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
                row.setOnClickListener(v -> showArtifactMenu(v, artifact));
                download.setOnClickListener(v -> downloadArtifact(artifact));
            }
            artifactsList.addView(row);
        }
    }

    private int outValueSelectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private void showArtifactMenu(@NonNull View anchor, @NonNull Artifact artifact) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.download);
        popup.getMenu().add(0, 2, 1, R.string.artifact_to_release);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) downloadArtifact(artifact);
            else unpackForRelease(artifact);
            return true;
        });
        popup.show();
    }

    /**
     * Downloads the artifact (a zip) into the app's cache, unpacks it and lets the user pick
     * the files that go to a release of this repository (or another one).
     */
    private void unpackForRelease(@NonNull Artifact artifact) {
        showProgressDialog(getString(R.string.artifact_unpacking, artifact.getName()));
        File root = new File(getCacheDir(), "artifacts");
        background(() -> {
            // artifacts unpacked for earlier uploads: only today's may still be uploading
            File[] old = root.listFiles();
            if (old != null) {
                for (File dir : old) {
                    if (System.currentTimeMillis() - dir.lastModified() > 24 * 3600 * 1000L) {
                        ArtifactFiles.deleteTree(dir);
                    }
                }
            }
            File dir = new File(root, runId + "-" + artifact.getId());
            ArtifactFiles.deleteTree(dir);
            retrofit2.Response<okhttp3.ResponseBody> response =
                    service().getArtifactZip(owner, repo, artifact.getId()).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new GitHubApi.ApiException(response.code(), GitHubApi.errorMessage(response));
            }
            try (okhttp3.ResponseBody body = response.body()) {
                return ArtifactFiles.unzip(body.byteStream(), dir);
            }
        }, (List<File> files) -> {
            dismissProgressDialog();
            if (files.isEmpty()) {
                showWarningToast(getString(R.string.artifact_empty));
                return;
            }
            chooseArtifactFiles(files);
        }, error -> dismissProgressDialog(), true);
    }

    private void chooseArtifactFiles(@NonNull List<File> files) {
        String[] names = new String[files.size()];
        boolean[] checked = new boolean[files.size()];
        for (int i = 0; i < files.size(); i++) {
            names[i] = files.get(i).getName() + "  (" + StringUtils.getSizeString(files.get(i).length()) + ")";
            checked[i] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.artifact_choose_files)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.artifact_next, (d, w) -> {
                    ArrayList<android.net.Uri> uris = new ArrayList<>();
                    for (int i = 0; i < files.size(); i++) {
                        if (!checked[i]) continue;
                        uris.add(androidx.core.content.FileProvider.getUriForFile(this,
                                getPackageName() + ".fileProvider", files.get(i)));
                    }
                    if (uris.isEmpty()) return;
                    // the share screen: this repository, then a release (or a new one)
                    Intent intent = new Intent(this, ShareUploadActivity.class)
                            .setAction(Intent.ACTION_SEND_MULTIPLE)
                            .setType("*/*")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            .putExtra(ShareUploadActivity.EXTRA_REPO, owner + "/" + repo)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                })
                .show();
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
                    }, R.string.run_rerun_started, true, false);
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
                        }, R.string.run_cancel_requested, false, true))
                .show();
    }

    private void act(@NonNull Callable<Boolean> work, int doneText, boolean rerun, boolean cancel) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            showSuccessToast(getString(doneText));
            if (cancel) cancelRequested = true;
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
        runProgress = root.findViewById(R.id.run_progress);
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
