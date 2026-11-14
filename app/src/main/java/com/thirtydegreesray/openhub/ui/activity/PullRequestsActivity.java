package com.thirtydegreesray.openhub.ui.activity;

import com.thirtydegreesray.openhub.util.ArchivedPulls;
import com.thirtydegreesray.openhub.util.ErrorTexts;
import com.thirtydegreesray.openhub.util.PullRefresh;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Pull requests of a repository, Open or Closed (merged ones included), newest first.
 * People with triage access or higher also get Archived (found with GitHub's search).
 */
public class PullRequestsActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      boolean canCreate) {
        return createIntent(context, owner, repo, canCreate, false);
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      boolean canCreate, boolean canTriage) {
        return new Intent(context, PullRequestsActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("canCreate", canCreate)
                .put("canTriage", canTriage).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean canCreate;
    @AutoAccess boolean showClosed;
    @AutoAccess boolean canTriage;
    @AutoAccess boolean showArchived;

    private TextView tabOpen;
    private TextView tabClosed;
    private TextView tabArchived;
    private LinearLayout list;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton newPullBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ColorStateList tabNormalColor;
    private int page;
    private long loadedStamp;
    /** Archived pull requests, to mark them in Closed; empty without triage access. */
    private Set<Integer> archived = new HashSet<>();

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_pull_requests;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.pulls_title), owner + "/" + repo);
        tabNormalColor = tabOpen.getTextColors();
        if (canCreate) newPullBn.show();
        tabArchived.setVisibility(canTriage ? View.VISIBLE : View.GONE);
        updateTabs();
        PullRefresh.wrap(findViewById(R.id.pulls_scroll), this::reload).follow(loader);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PullChanges.changedSince(loadedStamp)) reload();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void updateTabs() {
        styleTab(tabOpen, !showClosed && !showArchived);
        styleTab(tabClosed, showClosed && !showArchived);
        styleTab(tabArchived, showArchived);
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

    private void selectTab(boolean closed, boolean archivedTab) {
        if (showClosed == closed && showArchived == archivedTab) return;
        showClosed = closed;
        showArchived = archivedTab;
        updateTabs();
        reload();
    }

    private void reload() {
        disposables.clear();
        list.removeAllViews();
        page = 0;
        loadedStamp = PullChanges.stamp();
        loadNext();
    }

    private void loadNext() {
        final int next = page + 1;
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        loadMoreBn.setVisibility(View.GONE);
        final boolean archivedTab = showArchived;
        final boolean closedTab = showClosed;
        disposables.add(Single.fromCallable(() -> {
                    PullService pulls = GitHubApi.service(PullService.class);
                    if (archivedTab) {
                        JsonObject answer = GitHubApi.call(pulls.searchIssues(ArchivedPulls.query(owner, repo), next));
                        JsonElement items = Json.get(answer, "items");
                        return new Object[]{items != null && items.isJsonArray() ? items.getAsJsonArray()
                                : new JsonArray(), null};
                    }
                    JsonArray array = GitHubApi.call(pulls.getPulls(owner, repo, closedTab ? "closed" : "open", next));
                    Set<Integer> marks = null;
                    if (closedTab && canTriage && next == 1) {
                        try {
                            marks = ArchivedPulls.numbers(owner, repo);
                        } catch (Exception ignored) {
                            // no marks then; the pull request screen still says it
                        }
                    }
                    return new Object[]{array, marks};
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loader.setVisibility(View.GONE);
                    page = next;
                    JsonArray array = (JsonArray) result[0];
                    //noinspection unchecked
                    if (result[1] != null) archived = (Set<Integer>) result[1];
                    for (JsonElement element : array) list.addView(row(element.getAsJsonObject()));
                    loadMoreBn.setVisibility(array.size() >= (archivedTab ? 50 : 30) ? View.VISIBLE : View.GONE);
                    if (list.getChildCount() == 0) {
                        emptyText.setText(archivedTab ? R.string.pulls_none_archived
                                : showClosed ? R.string.pulls_none_closed : R.string.pulls_none_open);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                }, error -> {
                    loader.setVisibility(View.GONE);
                    showErrorToast(ErrorTexts.of(error));
                }));
    }

    @NonNull
    private View row(@NonNull JsonObject pull) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_pull, list, false);
        int number = Json.integer(pull, "number");
        ((TextView) row.findViewById(R.id.pull_title)).setText(Json.str(pull, "title"));
        Date created = Json.date(pull, "created_at");
        String when = created == null ? "" : StringUtils.getNewsTimeStr(this, created);
        // search results (Archived) are issues: no branches, merged_at under pull_request
        String meta = Json.str(pull, "head.ref").isEmpty()
                ? getString(R.string.pulls_meta_short, number, when, Json.str(pull, "user.login"))
                : getString(R.string.pulls_meta, number, when, Json.str(pull, "user.login"),
                Json.str(pull, "head.ref"), Json.str(pull, "base.ref"));
        ((TextView) row.findViewById(R.id.pull_meta)).setText(meta);

        TextView badge = row.findViewById(R.id.pull_badge);
        ImageView icon = row.findViewById(R.id.pull_icon);
        int stateColor;
        if (!Json.str(pull, "merged_at").isEmpty() || !Json.str(pull, "pull_request.merged_at").isEmpty()) {
            badge.setText(R.string.pull_state_merged);
            stateColor = R.color.pull_merged;
        } else if ("closed".equals(Json.str(pull, "state"))) {
            badge.setText(R.string.pull_state_closed);
            stateColor = R.color.pull_closed;
        } else if (Json.bool(pull, "draft")) {
            badge.setText(R.string.pull_state_draft);
            stateColor = R.color.pull_draft;
        } else {
            badge.setText("");
            stateColor = R.color.pull_open;
        }
        if (showArchived || archived.contains(number)) {
            badge.setText(badge.getText().length() == 0 ? getString(R.string.pull_state_archived)
                    : badge.getText() + " · " + getString(R.string.pull_state_archived));
        }
        badge.setVisibility(badge.getText().length() == 0 ? View.GONE : View.VISIBLE);
        // stacked pull request: "Stack 2/4" (position 1 = the bottom, next to the base branch)
        TextView stackBadge = row.findViewById(R.id.pull_stack_badge);
        int stackSize = Json.integer(pull, "stack.size");
        int stackPosition = Json.integer(pull, "stack.position");
        if (stackSize > 1 && stackPosition > 0) {
            stackBadge.setText(getString(R.string.pulls_stack_badge, stackPosition, stackSize));
            stackBadge.setVisibility(View.VISIBLE);
        } else {
            stackBadge.setVisibility(View.GONE);
        }
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(getResources().getColor(stateColor, getTheme())));
        row.setOnClickListener(v -> startActivity(PullRequestActivity.createIntent(this, owner, repo, number)));
        return row;
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        tabOpen = root.findViewById(R.id.tab_open);
        tabClosed = root.findViewById(R.id.tab_closed);
        tabArchived = root.findViewById(R.id.tab_archived);
        list = root.findViewById(R.id.pull_list);
        loadMoreBn = root.findViewById(R.id.load_more_bn);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        newPullBn = root.findViewById(R.id.new_pull_bn);
        tabOpen.setOnClickListener(v -> selectTab(false, false));
        tabClosed.setOnClickListener(v -> selectTab(true, false));
        tabArchived.setOnClickListener(v -> selectTab(false, true));
        loadMoreBn.setOnClickListener(v -> loadNext());
        newPullBn.setOnClickListener(v -> startActivity(NewPullRequestActivity.createIntent(this, owner, repo)));
    }
}
