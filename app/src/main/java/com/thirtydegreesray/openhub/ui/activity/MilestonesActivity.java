package com.thirtydegreesray.openhub.ui.activity;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.MilestoneService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.MilestoneDates;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Milestones of a repository, like github.com/owner/repo/milestones: Open or Closed, by
 * due date, with the progress of their issues. People who can push create, edit, close,
 * reopen and delete them; everyone can open the issues of a milestone.
 */
public class MilestonesActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, boolean canWrite) {
        return new Intent(context, MilestonesActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("canWrite", canWrite).build());
    }

    private static final int PER_PAGE = 100;
    private static final int COLOR_PAST_DUE = 0xFFF85149;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean canWrite;
    @AutoAccess boolean showClosed;

    private TextView tabOpen;
    private TextView tabClosed;
    private LinearLayout list;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton newBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ColorStateList tabNormalColor;
    private int page = 1;

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
        setToolbarTitle(getString(R.string.milestones), owner + "/" + repo);
        tabNormalColor = tabOpen.getTextColors();
        newBn.setImageResource(R.drawable.ic_add);
        newBn.setContentDescription(getString(R.string.milestone_new));
        if (canWrite) newBn.show();
        else newBn.hide();
        updateTabs();
        reload();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private MilestoneService service() {
        return GitHubApi.service(MilestoneService.class);
    }

    // ---------------------------------------------------------------------------------
    // List
    // ---------------------------------------------------------------------------------

    private void updateTabs() {
        styleTab(tabOpen, !showClosed);
        styleTab(tabClosed, showClosed);
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

    private void selectTab(boolean closed) {
        if (showClosed == closed) return;
        showClosed = closed;
        updateTabs();
        reload();
    }

    private void reload() {
        disposables.clear();
        list.removeAllViews();
        page = 1;
        loadNext();
    }

    private void loadNext() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        loadMoreBn.setVisibility(View.GONE);
        final int loadPage = page;
        background(() -> GitHubApi.call(service().getMilestones(owner, repo,
                showClosed ? "closed" : "open", PER_PAGE, loadPage)), (JsonArray milestones) -> {
            loader.setVisibility(View.GONE);
            int count = 0;
            if (milestones != null) {
                for (JsonElement e : milestones) {
                    if (!e.isJsonObject()) continue;
                    list.addView(row(e.getAsJsonObject()));
                    count++;
                }
            }
            page = loadPage + 1;
            loadMoreBn.setVisibility(count >= PER_PAGE ? View.VISIBLE : View.GONE);
            if (list.getChildCount() == 0) {
                emptyText.setText(showClosed ? R.string.milestones_none_closed : R.string.milestones_none_open);
                emptyText.setVisibility(View.VISIBLE);
            }
        }, false);
    }

    @NonNull
    private View row(@NonNull JsonObject milestone) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_milestone, list, false);
        String title = Json.str(milestone, "title");
        ((TextView) row.findViewById(R.id.milestone_title)).setText(title);

        TextView dueText = row.findViewById(R.id.milestone_due);
        Date due = Json.date(milestone, "due_on");
        Date closedAt = Json.date(milestone, "closed_at");
        boolean open = "open".equals(Json.str(milestone, "state"));
        if (!open && closedAt != null) {
            dueText.setText(getString(R.string.milestone_closed_on, StringUtils.getNewsTimeStr(this, closedAt)));
        } else if (due == null) {
            dueText.setText(R.string.milestone_no_due);
        } else {
            int late = MilestoneDates.daysPastDue(due, new Date(), TimeZone.getDefault());
            if (late > 0) {
                dueText.setText(getString(R.string.milestone_overdue,
                        getResources().getQuantityString(R.plurals.milestone_days, late, late)));
                dueText.setTextColor(COLOR_PAST_DUE);
            } else {
                dueText.setText(getString(R.string.milestone_due, MilestoneDates.format(due)));
            }
        }

        int openIssues = Json.integer(milestone, "open_issues");
        int closedIssues = Json.integer(milestone, "closed_issues");
        int percent = MilestoneDates.percent(openIssues, closedIssues);
        ProgressBar progress = row.findViewById(R.id.milestone_progress);
        progress.setProgress(percent);
        progress.setProgressTintList(ColorStateList.valueOf(ViewUtils.getAccentColor(this)));
        ((TextView) row.findViewById(R.id.milestone_counts)).setText(
                getString(R.string.milestone_counts, percent, openIssues, closedIssues));

        String description = Json.str(milestone, "description").trim();
        TextView descriptionText = row.findViewById(R.id.milestone_description);
        descriptionText.setText(description);
        descriptionText.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);

        row.setOnClickListener(v -> showActions(v, milestone));
        return row;
    }

    private void showActions(@NonNull View anchor, @NonNull JsonObject milestone) {
        int number = Json.integer(milestone, "number");
        String title = Json.str(milestone, "title");
        boolean open = "open".equals(Json.str(milestone, "state"));
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.milestone_issues);
        if (canWrite) {
            popup.getMenu().add(0, 2, 1, R.string.milestone_edit);
            popup.getMenu().add(0, 3, 2, open ? R.string.milestone_close : R.string.milestone_reopen);
            popup.getMenu().add(0, 4, 3, R.string.delete);
        }
        popup.getMenu().add(0, 5, 4, R.string.open_in_browser);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startActivity(IssuesActivity.createIntentForMilestone(this, owner, repo, number, title));
                    return true;
                case 2:
                    edit(milestone);
                    return true;
                case 3:
                    setState(number, title, !open);
                    return true;
                case 4:
                    delete(number, title);
                    return true;
                case 5:
                    AppOpener.openInCustomTabsOrBrowser(this, Json.str(milestone, "html_url"));
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    // ---------------------------------------------------------------------------------
    // Create, edit, close / reopen, delete
    // ---------------------------------------------------------------------------------

    /** @param existing null: a new milestone */
    private void edit(@Nullable JsonObject existing) {
        View view = LayoutInflater.from(new AlertDialog.Builder(this).getContext())
                .inflate(R.layout.dialog_milestone, null);
        TextInputLayout titleLayout = view.findViewById(R.id.milestone_title_layout);
        TextInputEditText titleEdit = view.findViewById(R.id.milestone_title_edit);
        TextInputEditText descriptionEdit = view.findViewById(R.id.milestone_description_edit);
        TextView dueText = view.findViewById(R.id.milestone_due_text);
        Button setDue = view.findViewById(R.id.milestone_due_set);
        Button clearDue = view.findViewById(R.id.milestone_due_clear);

        // the chosen day as {year, month, day}, or null
        final int[][] due = new int[1][];
        if (existing != null) {
            titleEdit.setText(Json.str(existing, "title"));
            descriptionEdit.setText(Json.str(existing, "description"));
            Date date = Json.date(existing, "due_on");
            if (date != null) due[0] = MilestoneDates.day(date);
        }
        Runnable showDue = () -> {
            dueText.setText(due[0] == null ? getString(R.string.milestone_no_due)
                    : getString(R.string.milestone_due, MilestoneDates.format(dateOf(due[0]))));
            clearDue.setVisibility(due[0] == null ? View.GONE : View.VISIBLE);
        };
        showDue.run();
        setDue.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            int[] start = due[0] != null ? due[0] : new int[]{today.get(Calendar.YEAR),
                    today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)};
            new DatePickerDialog(new AlertDialog.Builder(this).getContext(), (picker, y, m, d) -> {
                due[0] = new int[]{y, m, d};
                showDue.run();
            }, start[0], start[1], start[2]).show();
        });
        clearDue.setOnClickListener(v -> {
            due[0] = null;
            showDue.run();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.milestone_new : R.string.milestone_edit)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleEdit.getText() == null ? "" : titleEdit.getText().toString().trim();
            titleLayout.setError(null);
            if (title.isEmpty()) {
                titleLayout.setError(getString(R.string.field_required));
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("title", title);
            body.addProperty("description", descriptionEdit.getText() == null ? ""
                    : descriptionEdit.getText().toString().trim());
            if (due[0] == null) body.add("due_on", JsonNull.INSTANCE);
            else body.addProperty("due_on", MilestoneDates.dueOn(due[0][0], due[0][1], due[0][2]));
            dialog.dismiss();
            int number = existing == null ? 0 : Json.integer(existing, "number");
            run(() -> {
                if (number == 0) {
                    GitHubApi.call(service().createMilestone(owner, repo, GistActivity.json(body)));
                } else {
                    GitHubApi.call(service().updateMilestone(owner, repo, number, GistActivity.json(body)));
                }
                return true;
            }, getString(R.string.milestone_saved, title));
        }));
        dialog.show();
    }

    @NonNull
    private static Date dateOf(@NonNull int[] day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(day[0], day[1], day[2], 12, 0, 0);
        return calendar.getTime();
    }

    private void setState(int number, @NonNull String title, boolean open) {
        JsonObject body = new JsonObject();
        body.addProperty("state", open ? "open" : "closed");
        run(() -> {
            GitHubApi.call(service().updateMilestone(owner, repo, number, GistActivity.json(body)));
            return true;
        }, getString(open ? R.string.milestone_reopened_done : R.string.milestone_closed_done, title));
    }

    private void delete(int number, @NonNull String title) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.milestone_delete_message, title))
                .setPositiveButton(R.string.delete, (d, w) -> run(() -> {
                    GitHubApi.call(service().deleteMilestone(owner, repo, number));
                    return true;
                }, getString(R.string.milestone_deleted, title)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Runs a change, then reloads the list. */
    private void run(@NonNull Callable<Boolean> work, @NonNull String successMessage) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            showSuccessToast(successMessage);
            reload();
        }, true);
    }

    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.refresh);
        menu.add(0, 2, 1, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            reload();
            return true;
        } else if (item.getItemId() == 2) {
            AppOpener.openInCustomTabsOrBrowser(this, "https://github.com/" + owner + "/" + repo
                    + "/milestones" + (showClosed ? "?state=closed" : ""));
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
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        tabOpen = root.findViewById(R.id.tab_open);
        tabClosed = root.findViewById(R.id.tab_closed);
        list = root.findViewById(R.id.pull_list);
        loadMoreBn = root.findViewById(R.id.load_more_bn);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        newBn = root.findViewById(R.id.new_pull_bn);
        tabOpen.setOnClickListener(v -> selectTab(false));
        tabClosed.setOnClickListener(v -> selectTab(true));
        loadMoreBn.setOnClickListener(v -> loadNext());
        newBn.setOnClickListener(v -> edit(null));
    }
}
