package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.ActionsService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.TextInputDialog;
import com.thirtydegreesray.openhub.util.ActionsLog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.RunStatus;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * The log of one job: timestamps and colour codes removed, groups collapsible (groups
 * with errors open), error and warning lines coloured, find, jump to the next error.
 * A job that is still running is checked again every few seconds.
 */
public class JobLogActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      long jobId, @Nullable String jobName, @Nullable String htmlUrl) {
        return new Intent(context, JobLogActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("jobId", jobId)
                .put("jobName", jobName).put("htmlUrl", htmlUrl).build());
    }

    /** Longer logs show their end (where the errors are); Download log has everything. */
    private static final int MAX_LINES = 100_000;
    private static final long POLL_MS = 8000;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess long jobId;
    @AutoAccess String jobName;
    @AutoAccess String htmlUrl;

    private RecyclerView list;
    private TextView message;
    private ProgressBar loader;
    private LinearLayoutManager layoutManager;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** while the job runs only its status is checked (cheap); the log loads when it ends */
    private final Runnable poll = this::checkJob;
    private boolean resumed;
    /** the job was still running at the last check */
    private boolean running;

    @Nullable private ActionsLog log;
    /** groups the user opened or closed (by default: closed, open when they have errors) */
    private final Set<Integer> toggled = new HashSet<>();
    /** what the list shows: -(group + 1) for a group header, else a line index */
    private final List<Integer> items = new ArrayList<>();
    private String query = "";
    private int lastFound = -1;
    private int lastError = -1;
    private final LogAdapter adapter = new LogAdapter();
    private int primaryColor;
    private int secondaryColor;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_job_log;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(StringUtils.isBlank(jobName) ? getString(R.string.run_log) : jobName,
                owner + "/" + repo);
        primaryColor = resolveColor(android.R.attr.textColorPrimary);
        secondaryColor = resolveColor(android.R.attr.textColorSecondary);
        layoutManager = new LinearLayoutManager(this);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (running) schedulePoll();
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

    // ---------------------------------------------------------------------------------
    // Load
    // ---------------------------------------------------------------------------------

    private void load() {
        loader.setVisibility(View.VISIBLE);
        disposables.add(Single.fromCallable(() -> {
                    ActionsService service = GitHubApi.service(ActionsService.class);
                    JsonObject job = GitHubApi.call(service.getJob(owner, repo, jobId));
                    boolean done = "completed".equals(Json.str(job, "status"));
                    Response<ResponseBody> response = service.getJobLog(owner, repo, jobId).blockingFirst();
                    if (!response.isSuccessful()) {
                        if (!done && (response.code() == 404 || response.code() == 410)) {
                            return new Object[]{job, null};
                        }
                        throw new GitHubApi.ApiException(response.code(), GitHubApi.errorMessage(response));
                    }
                    try (ResponseBody body = response.body();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(
                                 body.byteStream(), StandardCharsets.UTF_8))) {
                        return new Object[]{job, ActionsLog.read(reader, MAX_LINES)};
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loader.setVisibility(View.GONE);
                    JsonObject job = (JsonObject) result[0];
                    boolean done = "completed".equals(Json.str(job, "status"));
                    RunStatus status = RunStatus.of(Json.str(job, "status"), Json.str(job, "conclusion"));
                    setToolbarTitle(StringUtils.isBlank(jobName) ? getString(R.string.run_log) : jobName,
                            getString(status.label) + " · " + owner + "/" + repo);
                    if (result[1] == null) {
                        showMessage(getString(R.string.log_not_ready));
                    } else {
                        boolean atEnd = log != null && !list.canScrollVertically(1);
                        log = (ActionsLog) result[1];
                        rebuild();
                        if (log.lines.isEmpty()) showMessage(getString(R.string.log_empty));
                        else message.setVisibility(View.GONE);
                        // a running job: keep following the end if the reader is there
                        if (atEnd || (!done && items.size() > 0)) list.scrollToPosition(adapter.getItemCount() - 1);
                    }
                    invalidateOptionsMenu();
                    running = !done;
                    if (running) schedulePoll();
                }, error -> {
                    loader.setVisibility(View.GONE);
                    String text = StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
                    if (log == null) showMessage(text);
                    else showErrorToast(text);
                }));
    }

    private void schedulePoll() {
        handler.removeCallbacks(poll);
        if (resumed) handler.postDelayed(poll, POLL_MS);
    }

    /** Status of the running job; when it has finished, the whole log is loaded. */
    private void checkJob() {
        disposables.add(Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(ActionsService.class)
                        .getJob(owner, repo, jobId)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(job -> {
                    if ("completed".equals(Json.str(job, "status"))) {
                        running = false;
                        load();
                    } else {
                        schedulePoll();
                    }
                }, error -> schedulePoll()));
    }

    private void showMessage(@NonNull String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------------------------
    // Groups and the shown items
    // ---------------------------------------------------------------------------------

    private boolean isOpen(int group) {
        ActionsLog.Group g = log.groups.get(group);
        if (g.title == null) return true;
        boolean byDefault = g.hasError;
        return toggled.contains(group) != byDefault;
    }

    private void setOpen(int group, boolean open) {
        ActionsLog.Group g = log.groups.get(group);
        if (g.title == null) return;
        if (open == g.hasError) toggled.remove(group);
        else toggled.add(group);
    }

    private void rebuild() {
        items.clear();
        if (log == null) return;
        for (int g = 0; g < log.groups.size(); g++) {
            ActionsLog.Group group = log.groups.get(g);
            if (group.lineCount == 0) continue;
            if (group.title != null) items.add(-(g + 1));
            if (isOpen(g)) {
                for (int i = group.firstLine; i < group.firstLine + group.lineCount; i++) items.add(i);
            }
        }
        adapter.notifyDataSetChanged();
    }

    /** Opens the line's group and scrolls to it. */
    private void showLine(int line) {
        if (log == null || line < 0) return;
        int group = log.lines.get(line).group;
        if (!isOpen(group)) {
            setOpen(group, true);
            rebuild();
        }
        int position = items.indexOf(line);
        if (position >= 0) layoutManager.scrollToPositionWithOffset(position + skippedRows(), 120);
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.log_find);
        menu.add(0, 2, 1, R.string.log_find_next);
        menu.add(0, 3, 2, R.string.log_next_error);
        menu.add(0, 4, 3, R.string.log_expand_all);
        menu.add(0, 5, 4, R.string.log_collapse_all);
        menu.add(0, 6, 5, R.string.log_to_top);
        menu.add(0, 7, 6, R.string.log_to_end);
        menu.add(0, 8, 7, R.string.refresh);
        menu.add(0, 9, 8, R.string.log_download);
        menu.add(0, 10, 9, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean hasLog = log != null && !log.lines.isEmpty();
        for (int id = 1; id <= 7; id++) menu.findItem(id).setVisible(hasLog);
        menu.findItem(2).setVisible(hasLog && !query.isEmpty());
        menu.findItem(9).setVisible(hasLog);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                TextInputDialog.show(this, R.string.log_find, R.string.log_find_hint, query, false, text -> {
                    query = text.trim();
                    lastFound = -1;
                    findNext();
                    invalidateOptionsMenu();
                });
                return true;
            case 2:
                findNext();
                return true;
            case 3:
                if (log == null) return true;
                int error = log.nextError(lastError + 1);
                if (error < 0 && lastError >= 0) error = log.nextError(0); // wrap around
                if (error < 0) {
                    showInfoToast(getString(R.string.log_no_errors));
                } else {
                    lastError = error;
                    showLine(error);
                }
                return true;
            case 4:
            case 5:
                if (log == null) return true;
                for (int g = 0; g < log.groups.size(); g++) setOpen(g, item.getItemId() == 4);
                rebuild();
                return true;
            case 6:
                list.scrollToPosition(0);
                return true;
            case 7:
                if (!items.isEmpty()) list.scrollToPosition(adapter.getItemCount() - 1);
                return true;
            case 8:
                load();
                return true;
            case 9:
                String name = repo + "-" + (StringUtils.isBlank(jobName) ? "job" : jobName
                        .replaceAll("[^A-Za-z0-9._-]+", "_")) + "-" + jobId + ".log";
                AppOpener.startDownload(this, AppConfig.GITHUB_API_BASE_URL + "repos/" + owner + "/" + repo
                        + "/actions/jobs/" + jobId + "/logs", name);
                return true;
            case 10:
                AppOpener.openInCustomTabsOrBrowser(this, StringUtils.isBlank(htmlUrl)
                        ? "https://github.com/" + owner + "/" + repo + "/actions" : htmlUrl);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void findNext() {
        if (log == null || query.isEmpty()) return;
        int found = log.find(query, lastFound + 1);
        if (found < 0 && lastFound >= 0) found = log.find(query, 0); // wrap around
        if (found < 0) {
            showInfoToast(getString(R.string.log_not_found, query));
            return;
        }
        lastFound = found;
        showLine(found);
        adapter.notifyDataSetChanged(); // highlight
    }

    // ---------------------------------------------------------------------------------
    // Adapter
    // ---------------------------------------------------------------------------------

    private int skippedRows() {
        return log != null && log.skipped > 0 && !items.isEmpty() ? 1 : 0;
    }

    private class LogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int HEADER = 1;
        private static final int LINE = 2;
        private static final int SKIPPED = 3;

        @Override
        public int getItemCount() {
            int extra = log != null && log.skipped > 0 && !items.isEmpty() ? 1 : 0;
            return items.size() + extra;
        }

        private boolean hasSkippedRow() {
            return log != null && log.skipped > 0 && !items.isEmpty();
        }

        private int item(int position) {
            return items.get(hasSkippedRow() ? position - 1 : position);
        }

        @Override
        public int getItemViewType(int position) {
            if (hasSkippedRow() && position == 0) return SKIPPED;
            return item(position) < 0 ? HEADER : LINE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int pad = getResources().getDimensionPixelSize(R.dimen.spacing_normal);
            text.setTypeface(Typeface.MONOSPACE);
            if (viewType == HEADER) {
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                text.setPadding(pad, pad / 2, pad, pad / 2);
                TypedValue value = new TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
                text.setBackgroundResource(value.resourceId);
            } else {
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                text.setPadding(pad, 0, pad, 0);
                text.setTextIsSelectable(viewType == LINE);
            }
            return new RecyclerView.ViewHolder(text) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextView text = (TextView) holder.itemView;
            int type = getItemViewType(position);
            if (type == SKIPPED) {
                text.setText(getString(R.string.log_skipped, log.skipped));
                text.setTextColor(RunStatus.GREY);
                return;
            }
            int item = item(position);
            if (type == HEADER) {
                int g = -item - 1;
                ActionsLog.Group group = log.groups.get(g);
                text.setText((isOpen(g) ? "▾ " : "▸ ") + group.title);
                text.setTextColor(group.hasError ? RunStatus.RED : primaryColor);
                text.setOnClickListener(v -> {
                    setOpen(g, !isOpen(g));
                    rebuild();
                });
                return;
            }
            ActionsLog.Line line = log.lines.get(item);
            int color;
            switch (line.kind) {
                case ActionsLog.ERROR:
                    color = RunStatus.RED;
                    break;
                case ActionsLog.WARNING:
                    color = RunStatus.AMBER;
                    break;
                case ActionsLog.COMMAND:
                    color = 0xFF57A6FF;
                    break;
                case ActionsLog.NOTICE:
                case ActionsLog.DEBUG:
                    color = RunStatus.GREY;
                    break;
                default:
                    color = secondaryColor;
            }
            text.setTextColor(color);
            String shown = line.text.isEmpty() ? " " : line.text;
            if (!query.isEmpty() && item == lastFound) {
                SpannableString span = new SpannableString(shown);
                int start = shown.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
                if (start >= 0) {
                    span.setSpan(new BackgroundColorSpan(0x66FFD33D), start, start + query.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                text.setText(span);
            } else {
                text.setText(shown);
            }
        }
    }

    private int resolveColor(int attr) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            android.content.res.ColorStateList list =
                    androidx.core.content.ContextCompat.getColorStateList(this, value.resourceId);
            if (list != null) return list.getDefaultColor();
        }
        return value.data;
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        list = root.findViewById(R.id.log_list);
        message = root.findViewById(R.id.log_message);
        loader = root.findViewById(R.id.loader);
    }
}
