package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.ActionsService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.WorkflowChanges;
import com.thirtydegreesray.openhub.util.WorkflowStates;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Workflows and caches of GitHub Actions, like github.com/owner/repo/actions (left
 * column) and .../actions/caches. Tab Workflows: every workflow with its state; people
 * with write access enable and disable them. Tab Caches: GitHub's dependency caches with
 * their size and last use; people with write access delete one or all of them.
 * Every state shown is what GitHub answered; a change is read back before it is shown.
 */
public class WorkflowsActivity extends BaseActivity<IBaseContract.Presenter> {

    /** Result extras for "Show runs": the Actions screen filters its runs by this workflow. */
    public static final String RESULT_WORKFLOW_ID = "workflowId";
    public static final String RESULT_WORKFLOW_NAME = "workflowName";

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, boolean canWrite, boolean caches) {
        return new Intent(context, WorkflowsActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("canWrite", canWrite)
                .put("showCaches", caches).build());
    }

    private static final int PER_PAGE = 100;
    /** At most this many pages of workflows (1,000 workflows). */
    private static final int MAX_WORKFLOW_PAGES = 10;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_SORT = 2;
    private static final int MENU_DELETE_ALL = 3;
    private static final int MENU_BROWSER = 4;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean canWrite;
    @AutoAccess boolean showCaches;
    /** last_accessed_at, size_in_bytes or created_at; always newest / biggest first */
    @AutoAccess String cacheSort = "last_accessed_at";

    private TextView tabWorkflows;
    private TextView tabCaches;
    private LinearLayout list;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton fab;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ColorStateList tabNormalColor;
    private int cachePage = 1;
    private int cacheRows = 0;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        // the same screen shape as the pull request list
        return R.layout.activity_pull_requests;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.actions), owner + "/" + repo);
        tabWorkflows.setText(R.string.actions_workflows);
        tabCaches.setText(R.string.actions_caches);
        tabNormalColor = tabWorkflows.getTextColors();
        fab.hide();
        updateTabs();
        PullRefresh.wrap(findViewById(R.id.pulls_scroll), this::reload).follow(loader);
        reload();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private ActionsService service() {
        return GitHubApi.service(ActionsService.class);
    }

    // ---------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------

    private void updateTabs() {
        styleTab(tabWorkflows, !showCaches);
        styleTab(tabCaches, showCaches);
        invalidateOptionsMenu();
    }

    private void styleTab(@NonNull TextView tab, boolean selected) {
        tab.setTextColor(selected ? ColorStateList.valueOf(ViewUtils.getAccentColor(this)) : tabNormalColor);
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected);
        } else {
            TypedValue ripple = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            tab.setBackgroundResource(ripple.resourceId);
        }
    }

    private void selectTab(boolean caches) {
        if (showCaches == caches) return;
        showCaches = caches;
        updateTabs();
        reload();
    }

    private void reload() {
        disposables.clear();
        list.removeAllViews();
        loadMoreBn.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        if (showCaches) {
            cachePage = 1;
            cacheRows = 0;
            loadCaches();
        } else {
            loadWorkflows();
        }
    }

    // ---------------------------------------------------------------------------------
    // Workflows
    // ---------------------------------------------------------------------------------

    private void loadWorkflows() {
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            List<JsonObject> all = new ArrayList<>();
            for (int page = 1; page <= MAX_WORKFLOW_PAGES; page++) {
                JsonObject answer = GitHubApi.call(service().listWorkflows(owner, repo, PER_PAGE, page));
                JsonElement items = Json.get(answer, "workflows");
                int count = 0;
                if (items != null && items.isJsonArray()) {
                    for (JsonElement e : items.getAsJsonArray()) {
                        if (!e.isJsonObject()) continue;
                        all.add(e.getAsJsonObject());
                        count++;
                    }
                }
                if (count < PER_PAGE) break;
            }
            return all;
        }, (List<JsonObject> workflows) -> {
            loader.setVisibility(View.GONE);
            list.removeAllViews();
            if (!workflows.isEmpty() && !canWrite) list.addView(note(getString(R.string.workflows_read_only)));
            for (JsonObject workflow : workflows) list.addView(workflowRow(workflow));
            if (workflows.isEmpty()) {
                emptyText.setText(R.string.workflows_none);
                emptyText.setVisibility(View.VISIBLE);
            }
        }, false);
    }

    @NonNull
    private static String workflowName(@NonNull JsonObject workflow) {
        String name = Json.str(workflow, "name");
        return name.isEmpty() ? WorkflowStates.fileName(Json.str(workflow, "path")) : name;
    }

    @NonNull
    private View workflowRow(@NonNull JsonObject workflow) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_actions_entry, list, false);
        ((TextView) row.findViewById(R.id.entry_title)).setText(workflowName(workflow));
        ((TextView) row.findViewById(R.id.entry_detail)).setText(Json.str(workflow, "path"));
        TextView state = row.findViewById(R.id.entry_state);
        String value = Json.str(workflow, "state");
        state.setText(stateLabel(value));
        WorkflowStates.Kind kind = WorkflowStates.kind(value);
        if (kind == WorkflowStates.Kind.ACTIVE) {
            state.setTextColor(ContextCompat.getColor(this, R.color.pull_open));
        } else if (kind == WorkflowStates.Kind.DISABLED) {
            state.setTextColor(ContextCompat.getColor(this, R.color.pull_closed));
        }
        row.setOnClickListener(v -> showWorkflowActions(v, workflow));
        return row;
    }

    @NonNull
    private String stateLabel(@NonNull String state) {
        switch (state) {
            case WorkflowStates.ACTIVE: return getString(R.string.workflow_state_active);
            case WorkflowStates.DISABLED_MANUALLY: return getString(R.string.workflow_state_disabled_manually);
            case WorkflowStates.DISABLED_INACTIVITY: return getString(R.string.workflow_state_disabled_inactivity);
            case WorkflowStates.DISABLED_FORK: return getString(R.string.workflow_state_disabled_fork);
            case WorkflowStates.DELETED: return getString(R.string.workflow_state_deleted);
            default: return state;
        }
    }

    private void showWorkflowActions(@NonNull View anchor, @NonNull JsonObject workflow) {
        String state = Json.str(workflow, "state");
        String path = Json.str(workflow, "path");
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.workflow_show_runs);
        if (canWrite && WorkflowStates.canEnable(state)) popup.getMenu().add(0, 2, 1, R.string.workflow_enable);
        if (canWrite && WorkflowStates.canDisable(state)) popup.getMenu().add(0, 3, 2, R.string.workflow_disable);
        boolean hasFile = path.startsWith(".github/") && !WorkflowStates.DELETED.equals(state)
                && !Json.str(workflow, "html_url").isEmpty();
        if (hasFile) popup.getMenu().add(0, 4, 3, R.string.workflow_open_file);
        popup.getMenu().add(0, 5, 4, R.string.open_in_browser);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    setResult(Activity.RESULT_OK, new Intent()
                            .putExtra(RESULT_WORKFLOW_ID, Json.lng(workflow, "id"))
                            .putExtra(RESULT_WORKFLOW_NAME, workflowName(workflow)));
                    finish();
                    return true;
                case 2:
                    setEnabled(workflow, true);
                    return true;
                case 3:
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.workflow_disable)
                            .setMessage(getString(R.string.workflow_disable_message, workflowName(workflow)))
                            .setPositiveButton(R.string.workflow_disable, (d, w) -> setEnabled(workflow, false))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                case 4:
                    // a blob link: opens in the app's file viewer
                    AppOpener.launchUrl(this, Uri.parse(Json.str(workflow, "html_url")));
                    return true;
                case 5:
                    AppOpener.openInCustomTabsOrBrowser(this, WorkflowStates.actionsUrl(owner, repo, path));
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    /** Enables or disables, then reads the workflow back: the toast tells GitHub's real state. */
    private void setEnabled(@NonNull JsonObject workflow, boolean enable) {
        long id = Json.lng(workflow, "id");
        String name = workflowName(workflow);
        showProgressDialog(getString(R.string.please_wait));
        background(() -> {
            if (enable) GitHubApi.call(service().enableWorkflow(owner, repo, id));
            else GitHubApi.call(service().disableWorkflow(owner, repo, id));
            WorkflowChanges.markChanged();
            JsonObject now = GitHubApi.call(service().getWorkflow(owner, repo, id));
            return Json.str(now, "state");
        }, (String state) -> {
            dismissProgressDialog();
            boolean done = enable ? WorkflowStates.ACTIVE.equals(state)
                    : WorkflowStates.kind(state) == WorkflowStates.Kind.DISABLED;
            if (done) {
                showSuccessToast(getString(enable ? R.string.workflow_enabled_done
                        : R.string.workflow_disabled_done, name));
            } else {
                showWarningToast(getString(R.string.workflow_state_not_changed, name));
            }
            reload();
        }, true);
    }

    // ---------------------------------------------------------------------------------
    // Caches
    // ---------------------------------------------------------------------------------

    private void loadCaches() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        loadMoreBn.setVisibility(View.GONE);
        final int page = cachePage;
        final String sort = cacheSort;
        background(() -> {
            JsonObject usage = null;
            if (page == 1) {
                try {
                    usage = GitHubApi.call(service().getCacheUsage(owner, repo));
                } catch (Exception ignored) {
                    // the list still works; the summary line is left out
                }
            }
            JsonObject answer = GitHubApi.call(service().getCaches(owner, repo, sort, "desc", PER_PAGE, page));
            return new Object[]{usage, answer};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            JsonObject usage = (JsonObject) result[0];
            JsonObject answer = (JsonObject) result[1];
            if (page == 1) {
                list.removeAllViews();
                if (usage != null) {
                    int count = Json.integer(usage, "active_caches_count");
                    long size = Json.lng(usage, "active_caches_size_in_bytes");
                    list.addView(note(getString(R.string.caches_usage,
                            getResources().getQuantityString(R.plurals.caches_count, count, count),
                            StringUtils.getSizeString(size))));
                }
            }
            JsonElement items = Json.get(answer, "actions_caches");
            int count = 0;
            if (items != null && items.isJsonArray()) {
                for (JsonElement e : items.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    if (cacheRows == 0 && !canWrite) list.addView(note(getString(R.string.caches_read_only)));
                    list.addView(cacheRow(e.getAsJsonObject()));
                    cacheRows++;
                    count++;
                }
            }
            cachePage = page + 1;
            loadMoreBn.setVisibility(count >= PER_PAGE ? View.VISIBLE : View.GONE);
            if (cacheRows == 0) {
                emptyText.setText(R.string.caches_none);
                emptyText.setVisibility(View.VISIBLE);
            }
            invalidateOptionsMenu();
        }, false);
    }

    /** "main" for refs/heads/main; pull request refs stay as GitHub writes them. */
    @NonNull
    private static String shortRef(@NonNull String ref) {
        return ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
    }

    @NonNull
    private String when(@Nullable Date date) {
        return date == null ? "-" : StringUtils.getNewsTimeStr(this, date);
    }

    @NonNull
    private View cacheRow(@NonNull JsonObject cache) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_actions_entry, list, false);
        TextView title = row.findViewById(R.id.entry_title);
        title.setText(Json.str(cache, "key"));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        ((TextView) row.findViewById(R.id.entry_detail)).setText(getString(R.string.caches_meta,
                shortRef(Json.str(cache, "ref")), StringUtils.getSizeString(Json.lng(cache, "size_in_bytes"))));
        ((TextView) row.findViewById(R.id.entry_state)).setText(getString(R.string.caches_times,
                when(Json.date(cache, "last_accessed_at")), when(Json.date(cache, "created_at"))));
        row.setOnClickListener(v -> showCacheActions(v, cache));
        return row;
    }

    private void showCacheActions(@NonNull View anchor, @NonNull JsonObject cache) {
        String key = Json.str(cache, "key");
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.caches_copy_key);
        if (canWrite) popup.getMenu().add(0, 2, 1, R.string.caches_delete);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                AppUtils.copyToClipboard(this, key);
                showSuccessToast(getString(R.string.caches_key_copied));
                return true;
            } else if (item.getItemId() == 2) {
                long id = Json.lng(cache, "id");
                new AlertDialog.Builder(this)
                        .setTitle(R.string.caches_delete)
                        .setMessage(getString(R.string.caches_delete_message, key,
                                StringUtils.getSizeString(Json.lng(cache, "size_in_bytes"))))
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            showProgressDialog(getString(R.string.please_wait));
                            background(() -> {
                                GitHubApi.call(service().deleteCache(owner, repo, id));
                                return true;
                            }, done -> {
                                dismissProgressDialog();
                                showSuccessToast(getString(R.string.caches_deleted));
                                reload();
                            }, true);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** Reads every cache from GitHub first, so the question shows the real count and size. */
    private void deleteAllCaches() {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> {
            List<long[]> all = new ArrayList<>();
            for (int page = 1; page <= 100; page++) {
                JsonObject answer = GitHubApi.call(service().getCaches(owner, repo,
                        "last_accessed_at", "desc", PER_PAGE, page));
                JsonElement items = Json.get(answer, "actions_caches");
                int count = 0;
                if (items != null && items.isJsonArray()) {
                    for (JsonElement e : items.getAsJsonArray()) {
                        if (!e.isJsonObject()) continue;
                        JsonObject cache = e.getAsJsonObject();
                        all.add(new long[]{Json.lng(cache, "id"), Json.lng(cache, "size_in_bytes")});
                        count++;
                    }
                }
                if (count < PER_PAGE) break;
            }
            return all;
        }, (List<long[]> caches) -> {
            dismissProgressDialog();
            if (caches.isEmpty()) {
                showInfoToast(getString(R.string.caches_none));
                reload();
                return;
            }
            long size = 0;
            for (long[] cache : caches) size += cache[1];
            int total = caches.size();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.caches_delete_all)
                    .setMessage(getString(R.string.caches_delete_all_message,
                            getResources().getQuantityString(R.plurals.caches_count, total, total),
                            StringUtils.getSizeString(size)))
                    .setPositiveButton(R.string.delete, (d, w) -> deleteCaches(caches))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }, true);
    }

    private void deleteCaches(@NonNull List<long[]> caches) {
        int total = caches.size();
        showProgressDialog(getString(R.string.caches_deleting, 1, total));
        background(() -> {
            int deleted = 0;
            for (int i = 0; i < total; i++) {
                final int index = i + 1;
                runOnUiThread(() -> getProgressDialog(getString(R.string.caches_deleting, index, total)));
                try {
                    GitHubApi.call(service().deleteCache(owner, repo, caches.get(i)[0]));
                    deleted++;
                } catch (GitHubApi.ApiException e) {
                    // 404: GitHub already removed it (evicted); anything else stops here
                    if (e.code != 404) throw e;
                }
            }
            return deleted;
        }, (Integer deleted) -> {
            dismissProgressDialog();
            showSuccessToast(getResources().getQuantityString(R.plurals.caches_deleted_count, deleted, deleted));
            reload();
        }, true);
    }

    private void chooseSort() {
        String[] values = {"last_accessed_at", "size_in_bytes", "created_at"};
        String[] labels = {getString(R.string.caches_sort_last_used), getString(R.string.caches_sort_size),
                getString(R.string.caches_sort_created)};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cacheSort)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.caches_sort)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (values[which].equals(cacheSort)) return;
                    cacheSort = values[which];
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------

    @NonNull
    private TextView note(@NonNull String text) {
        TextView note = new TextView(this);
        int padding = getResources().getDimensionPixelSize(R.dimen.spacing_large);
        note.setPadding(padding, padding / 2, padding, padding / 2);
        note.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_small));
        // the empty text uses ?android:textColorSecondary of the theme
        note.setTextColor(emptyText.getTextColors());
        note.setText(text);
        return note;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.refresh);
        menu.add(0, MENU_SORT, 1, R.string.caches_sort);
        menu.add(0, MENU_DELETE_ALL, 2, R.string.caches_delete_all);
        menu.add(0, MENU_BROWSER, 3, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem sort = menu.findItem(MENU_SORT);
        if (sort != null) sort.setVisible(showCaches);
        MenuItem deleteAll = menu.findItem(MENU_DELETE_ALL);
        if (deleteAll != null) deleteAll.setVisible(showCaches && canWrite && cacheRows > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_REFRESH) {
            reload();
            return true;
        } else if (id == MENU_SORT) {
            chooseSort();
            return true;
        } else if (id == MENU_DELETE_ALL) {
            deleteAllCaches();
            return true;
        } else if (id == MENU_BROWSER) {
            AppOpener.openInCustomTabsOrBrowser(this, "https://github.com/" + owner + "/" + repo
                    + "/actions" + (showCaches ? "/caches" : ""));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess, boolean withProgress) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, error -> {
                    loader.setVisibility(View.GONE);
                    if (withProgress) dismissProgressDialog();
                    showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                    if (withProgress) reload();
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        tabWorkflows = root.findViewById(R.id.tab_open);
        tabCaches = root.findViewById(R.id.tab_closed);
        list = root.findViewById(R.id.pull_list);
        loadMoreBn = root.findViewById(R.id.load_more_bn);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        fab = root.findViewById(R.id.new_pull_bn);
        tabWorkflows.setOnClickListener(v -> selectTab(false));
        tabCaches.setOnClickListener(v -> selectTab(true));
        loadMoreBn.setOnClickListener(v -> loadCaches());
    }
}
