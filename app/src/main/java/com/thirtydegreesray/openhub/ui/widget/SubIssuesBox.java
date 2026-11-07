package com.thirtydegreesray.openhub.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.widget.ImageViewCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.IssueService;
import com.thirtydegreesray.openhub.http.SubIssueService;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.ui.activity.IssueDetailActivity;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.ButtonStyles;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.SubIssues;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The parent issue and the sub-issues of an issue, shown at the top of the issue
 * timeline: GitHub's progress, the sub-issues in GitHub's order, and for people with
 * triage access or more: add an existing issue, create a new sub-issue, move, remove.
 * Everything shown is what GitHub answered; after a change the box loads again.
 */
public class SubIssuesBox {

    /** Asked to show the box again (the timeline's first item). */
    public interface Host {
        void subIssuesChanged();

        /** Opens the editor for a new issue; its result goes to {@link #addCreated(Issue)}. */
        void createSubIssue();

        /** Loads the issue again from the network (GitHub's new progress). */
        void reloadIssueFromNetwork();
    }

    /** Raised by every write here and by closing / reopening an issue: other screens reload. */
    private static volatile long changes;

    public static void markChanged() {
        changes++;
    }

    public static long stamp() {
        return changes;
    }

    private static final int MAX_PAGES = 3;

    private final BaseActivity<?> activity;
    private final Host host;
    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private Disposable loading;

    private String owner = "";
    private String repo = "";
    private int number;
    private long selfId;
    @Nullable private Issue.SubIssuesSummary summary;

    /** Null until GitHub answered. */
    @Nullable private List<SubIssues.Entry> subs;
    @Nullable private SubIssues.Entry parent;
    private boolean canWrite;
    private boolean permissionsKnown;
    private long loadedStamp = -1;

    public SubIssuesBox(@NonNull BaseActivity<?> activity, @NonNull Host host) {
        this.activity = activity;
        this.host = host;
    }

    public void destroy() {
        disposables.clear();
    }

    /** The issue was (re)loaded: read its sub-issues and its parent from GitHub. */
    public void load(@NonNull Issue issue) {
        if (issue.getRepoAuthorName() == null || issue.getRepoName() == null) return;
        owner = issue.getRepoAuthorName();
        repo = issue.getRepoName();
        number = issue.getNumber();
        selfId = parseId(issue.getId());
        summary = issue.getSubIssuesSummary();
        final boolean readSubs = summary == null || summary.total > 0;
        final boolean readParent = summary == null || !StringUtils.isBlank(issue.getParentIssueUrl());
        final boolean readPermissions = !permissionsKnown;
        final String o = owner, r = repo;
        final int n = number;
        loadedStamp = changes;
        if (loading != null) loading.dispose();
        loading = Single.fromCallable(() -> {
            List<SubIssues.Entry> list = new ArrayList<>();
            if (readSubs) {
                for (int page = 1; page <= MAX_PAGES; page++) {
                    JsonArray array = GitHubApi.call(api().getSubIssues(o, r, n, 100, page));
                    List<SubIssues.Entry> part = SubIssues.entries(array);
                    list.addAll(part);
                    if (part.size() < 100) break;
                }
            }
            SubIssues.Entry p = null;
            if (readParent) {
                try {
                    JsonObject json = GitHubApi.call(api().getParent(o, r, n));
                    if (json != null) p = SubIssues.entry(json);
                } catch (GitHubApi.ApiException e) {
                    if (e.code != 404) throw e;
                }
            }
            Boolean write = null;
            if (readPermissions) {
                try {
                    JsonObject json = GitHubApi.call(GitHubApi.service(IssueService.class).getRepoJson(o, r));
                    write = Json.bool(json, "permissions.admin") || Json.bool(json, "permissions.maintain")
                            || Json.bool(json, "permissions.push") || Json.bool(json, "permissions.triage");
                } catch (Exception ignored) {
                }
            }
            return new Object[]{list, p, write};
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    @SuppressWarnings("unchecked")
                    List<SubIssues.Entry> list = (List<SubIssues.Entry>) result[0];
                    subs = list;
                    parent = (SubIssues.Entry) result[1];
                    if (result[2] != null) {
                        canWrite = (Boolean) result[2];
                        permissionsKnown = true;
                    }
                    host.subIssuesChanged();
                }, error -> {
                    // the issue itself is still shown; the box stays as it was
                });
        disposables.add(loading);
    }

    /** True when something changed sub-issues or issue states since the last load. */
    public boolean isStale() {
        return loadedStamp != -1 && loadedStamp != changes;
    }

    // ---------------------------------------------------------------- showing

    /** Fills {@code container} (in the first timeline item) with the parent and the sub-issues. */
    public void bind(@NonNull LinearLayout container) {
        container.removeAllViews();
        Context ctx = container.getContext();
        boolean hasSubs = subs != null && !subs.isEmpty();
        if (parent == null && !hasSubs && !(canWrite && subs != null)) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        if (parent != null) {
            LinearLayout box = box(ctx);
            box.addView(heading(ctx, ctx.getString(R.string.sub_issue_parent)));
            box.addView(row(ctx, parent, -1));
            container.addView(box);
        }
        if (hasSubs || canWrite) {
            LinearLayout box = box(ctx);
            int total = summary != null ? summary.total : hasSubs ? subs.size() : 0;
            String title = summary != null && summary.total > 0
                    ? ctx.getString(R.string.sub_issues_progress, summary.completed, summary.total)
                    : total > 0 ? ctx.getString(R.string.sub_issues_count, total)
                    : ctx.getString(R.string.sub_issues_title);
            box.addView(heading(ctx, title));
            if (summary != null && summary.total > 0) box.addView(progress(ctx, summary.percentCompleted));
            if (hasSubs) {
                for (int i = 0; i < subs.size(); i++) box.addView(row(ctx, subs.get(i), i));
            } else {
                TextView none = new TextView(ctx);
                none.setText(R.string.sub_issues_none);
                none.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
                none.setTextColor(secondary(ctx));
                int pad = dp(ctx, 12);
                none.setPadding(pad, dp(ctx, 2), pad, dp(ctx, 4));
                box.addView(none);
            }
            if (canWrite) {
                TextView add = new TextView(ctx);
                add.setText(R.string.sub_issue_add);
                ButtonStyles.tonal(add);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginStart(dp(ctx, 12));
                params.topMargin = dp(ctx, 6);
                params.bottomMargin = dp(ctx, 4);
                box.addView(add, params);
                add.setOnClickListener(v -> chooseAdd());
            }
            container.addView(box);
        }
    }

    private LinearLayout box(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_box);
        box.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(ctx, 8);
        box.setLayoutParams(params);
        return box;
    }

    private TextView heading(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(secondary(ctx));
        int pad = dp(ctx, 12);
        view.setPadding(pad, 0, pad, dp(ctx, 4));
        return view;
    }

    private View progress(Context ctx, int percent) {
        ProgressBar bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, percent)));
        bar.setProgressTintList(ColorStateList.valueOf(color(ctx, R.color.pull_merged)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(secondary(ctx)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 6));
        params.setMarginStart(dp(ctx, 12));
        params.setMarginEnd(dp(ctx, 12));
        params.bottomMargin = dp(ctx, 4);
        bar.setLayoutParams(params);
        return bar;
    }

    /** {@code index} is the place in the sub-issues list, -1 for the parent. */
    private View row(Context ctx, SubIssues.Entry e, int index) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(ctx, 12);
        row.setPadding(pad, dp(ctx, 6), pad, dp(ctx, 6));

        @ColorRes int color = e.isOpen() ? R.color.pull_open
                : e.isNotPlanned() ? R.color.pull_draft : R.color.pull_merged;
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(e.isOpen() ? R.drawable.ic_issues : R.drawable.ic_issues_closed);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color(ctx, color)));
        int size = dp(ctx, 16);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView text = new TextView(ctx);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_normal));
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setText(SubIssues.label(e, owner, repo) + "  " + e.title);
        text.setTextColor(primary(ctx));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(ctx, 8));
        row.addView(text, textParams);

        if (!e.isOpen()) {
            TextView state = new TextView(ctx);
            state.setText(e.isNotPlanned() ? R.string.sub_issue_not_planned : R.string.closed);
            state.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
            state.setTextColor(color(ctx, color));
            LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stateParams.setMarginStart(dp(ctx, 8));
            row.addView(state, stateParams);
        }

        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        row.setOnClickListener(v -> open(e));
        if (index >= 0 && canWrite) {
            row.setOnLongClickListener(v -> {
                showRowMenu(v, e, index);
                return true;
            });
        }
        return row;
    }

    private void open(SubIssues.Entry e) {
        if (e.number <= 0) return;
        String o = e.owner.isEmpty() ? owner : e.owner;
        String r = e.repo.isEmpty() ? repo : e.repo;
        IssueDetailActivity.show(activity, o, r, e.number);
    }

    private void showRowMenu(View anchor, SubIssues.Entry e, int index) {
        PopupMenu menu = new PopupMenu(activity, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        Menu m = menu.getMenu();
        boolean many = subs != null && subs.size() > 1;
        if (many && index > 0) m.add(0, 1, 0, R.string.sub_issue_move_up);
        if (many && subs != null && index < subs.size() - 1) m.add(0, 2, 1, R.string.sub_issue_move_down);
        m.add(0, 3, 2, R.string.sub_issue_remove);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) move(index, true);
            else if (item.getItemId() == 2) move(index, false);
            else if (item.getItemId() == 3) confirmRemove(e);
            return true;
        });
        menu.show();
    }

    // ---------------------------------------------------------------- writing

    private void move(int index, boolean up) {
        if (subs == null) return;
        long[] place = SubIssues.move(subs, index, up);
        if (place == null) return;
        final long id = subs.get(index).id;
        write(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("sub_issue_id", id);
            if (place[0] != 0) body.addProperty("before_id", place[0]);
            else body.addProperty("after_id", place[1]);
            GitHubApi.call(api().reprioritize(owner, repo, number, body));
            return Boolean.TRUE;
        }, null);
    }

    private void confirmRemove(SubIssues.Entry e) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sub_issue_remove)
                .setMessage(activity.getString(R.string.sub_issue_remove_message,
                        SubIssues.label(e, owner, repo), e.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sub_issue_remove_button, (d, w) -> write(() -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("sub_issue_id", e.id);
                    GitHubApi.call(api().removeSubIssue(owner, repo, number, body));
                    return Boolean.TRUE;
                }, activity.getString(R.string.sub_issue_removed, SubIssues.label(e, owner, repo))))
                .show();
    }

    private void chooseAdd() {
        String[] items = {activity.getString(R.string.sub_issue_add_existing),
                activity.getString(R.string.sub_issue_create_new)};
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sub_issue_add)
                .setItems(items, (d, which) -> {
                    if (which == 0) pickExisting();
                    else host.createSubIssue();
                })
                .show();
    }

    /** The editor created a new issue in this repository: make it a sub-issue. */
    public void addCreated(@NonNull Issue created) {
        long id = parseId(created.getId());
        if (id == 0) return;
        add(id, false, "#" + created.getNumber(), true);
    }

    private void pickExisting() {
        Context ctx = new AlertDialog.Builder(activity).getContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        layout.setPadding(pad, dp(ctx, 8), pad, 0);

        EditText search = new EditText(ctx);
        search.setHint(R.string.sub_issue_search_hint);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(ctx);
        status.setText(R.string.sub_issue_loading_open);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.getResources().getDimension(R.dimen.text_small));
        status.setTextColor(secondary(ctx));
        status.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        layout.addView(status);

        ListView listView = new ListView(ctx);
        layout.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 300)));

        List<SubIssues.Entry> open = new ArrayList<>();
        List<Object> shown = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1,
                new ArrayList<>());
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.sub_issue_add_existing)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .create();

        boolean[] loaded = {false};
        Runnable refresh = () -> {
            String filter = search.getText().toString();
            shown.clear();
            adapter.clear();
            SubIssues.Ref ref = SubIssues.parse(filter, owner, repo);
            List<SubIssues.Entry> current = subs == null ? new ArrayList<>() : subs;
            List<SubIssues.Entry> list = SubIssues.candidates(open, selfId,
                    parent == null ? 0 : parent.id, current, ref != null ? "" : filter);
            if (ref != null) {
                List<SubIssues.Entry> exact = new ArrayList<>();
                for (SubIssues.Entry e : list) {
                    if (e.number == ref.number && e.sameRepo(ref.owner, ref.repo)) exact.add(e);
                }
                list = exact;
                if (list.isEmpty()) {
                    shown.add(ref);
                    adapter.add(activity.getString(R.string.sub_issue_add_ref,
                            ref.owner.equalsIgnoreCase(owner) && ref.repo.equalsIgnoreCase(repo)
                                    ? "#" + ref.number : ref.owner + "/" + ref.repo + "#" + ref.number));
                }
            }
            for (SubIssues.Entry e : list) {
                shown.add(e);
                String text = "#" + e.number + "  " + e.title;
                if (e.hasParent) text += "  " + activity.getString(R.string.sub_issue_has_parent);
                adapter.add(text);
            }
            if (loaded[0]) {
                status.setText(shown.isEmpty() ? activity.getString(R.string.sub_issue_no_candidates)
                        : activity.getString(R.string.sub_issue_pick_hint));
            }
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });
        listView.setOnItemClickListener((parentView, view, position, id) -> {
            if (position < 0 || position >= shown.size()) return;
            Object item = shown.get(position);
            dialog.dismiss();
            if (item instanceof SubIssues.Entry) {
                SubIssues.Entry e = (SubIssues.Entry) item;
                addChecked(e);
            } else {
                SubIssues.Ref ref = (SubIssues.Ref) item;
                background(() -> {
                    JsonObject json = GitHubApi.call(api().getIssue(ref.owner, ref.repo, ref.number));
                    return json == null ? new SubIssues.Entry() : SubIssues.entry(json);
                }, e -> {
                    if (e.isPullRequest) {
                        activity.showErrorToast(activity.getString(R.string.sub_issue_is_pull));
                    } else if (e.id == selfId) {
                        activity.showErrorToast(activity.getString(R.string.sub_issue_is_self));
                    } else {
                        addChecked(e);
                    }
                });
            }
        });
        dialog.show();

        disposables.add(Single.fromCallable(() -> {
            JsonArray array = GitHubApi.call(api().getOpenIssues(owner, repo));
            return SubIssues.entries(array);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    loaded[0] = true;
                    open.clear();
                    open.addAll(list);
                    refresh.run();
                }, error -> {
                    loaded[0] = true;
                    status.setText(message(error));
                }));
    }

    /** An issue that already has a parent moves here only when the user says so. */
    private void addChecked(SubIssues.Entry e) {
        String label = SubIssues.label(e, owner, repo);
        if (!e.hasParent) {
            add(e.id, false, label, false);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sub_issue_move_title)
                .setMessage(activity.getString(R.string.sub_issue_move_message, label))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sub_issue_move_button, (d, w) -> add(e.id, true, label, false))
                .show();
    }

    private void add(long id, boolean replaceParent, String label, boolean justCreated) {
        write(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("sub_issue_id", id);
            if (replaceParent) body.addProperty("replace_parent", true);
            try {
                GitHubApi.call(api().addSubIssue(owner, repo, number, body));
            } catch (GitHubApi.ApiException e) {
                // a brand-new issue can be unknown to this endpoint for a moment
                if (!justCreated || e.code != 404) throw e;
                Thread.sleep(2000);
                GitHubApi.call(api().addSubIssue(owner, repo, number, body));
            }
            return Boolean.TRUE;
        }, activity.getString(R.string.sub_issue_added, label));
    }

    /** A write with the progress dialog; on success the issue and the box load again. */
    private void write(Callable<Boolean> work, @Nullable String done) {
        background(work, ok -> {
            markChanged();
            if (done != null) activity.showSuccessToast(done);
            PullRefresh.freshFromNow();
            host.reloadIssueFromNetwork();
        });
    }

    private <T> void background(Callable<T> work, Consumer<T> onSuccess) {
        activity.showProgressDialog(activity.getString(R.string.please_wait));
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    activity.dismissProgressDialog();
                    onSuccess.accept(result);
                }, error -> {
                    activity.dismissProgressDialog();
                    activity.showErrorToast(message(error));
                }));
    }

    // ---------------------------------------------------------------- small helpers

    private static SubIssueService api() {
        return GitHubApi.service(SubIssueService.class);
    }

    private static long parseId(@Nullable String id) {
        if (StringUtils.isBlank(id)) return 0;
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(id.trim());
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }

    private static String message(Throwable error) {
        return StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static int color(Context ctx, @ColorRes int res) {
        return ctx.getResources().getColor(res, ctx.getTheme());
    }

    private static int primary(Context ctx) {
        android.content.res.TypedArray array = ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color = array.getColor(0, 0xFFE2E8EE);
        array.recycle();
        return color;
    }

    private static int secondary(Context ctx) {
        android.content.res.TypedArray array = ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        int color = array.getColor(0, 0xFF8B949E);
        array.recycle();
        return color;
    }
}
