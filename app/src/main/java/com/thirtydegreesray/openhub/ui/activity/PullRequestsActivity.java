package com.thirtydegreesray.openhub.ui.activity;

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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Pull requests of a repository, Open or Closed (merged ones included), newest first.
 */
public class PullRequestsActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      boolean canCreate) {
        return new Intent(context, PullRequestsActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("canCreate", canCreate).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean canCreate;
    @AutoAccess boolean showClosed;

    private TextView tabOpen;
    private TextView tabClosed;
    private LinearLayout list;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton newPullBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ColorStateList tabNormalColor;
    private int page;
    private long loadedStamp;

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
        updateTabs();
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
        page = 0;
        loadedStamp = PullChanges.stamp();
        loadNext();
    }

    private void loadNext() {
        final int next = page + 1;
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        loadMoreBn.setVisibility(View.GONE);
        disposables.add(Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(PullService.class)
                        .getPulls(owner, repo, showClosed ? "closed" : "open", next)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(array -> {
                    loader.setVisibility(View.GONE);
                    page = next;
                    for (JsonElement element : array) list.addView(row(element.getAsJsonObject()));
                    loadMoreBn.setVisibility(array.size() >= 30 ? View.VISIBLE : View.GONE);
                    if (list.getChildCount() == 0) {
                        emptyText.setText(showClosed ? R.string.pulls_none_closed : R.string.pulls_none_open);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                }, error -> {
                    loader.setVisibility(View.GONE);
                    showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                }));
    }

    @NonNull
    private View row(@NonNull JsonObject pull) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_pull, list, false);
        int number = Json.integer(pull, "number");
        ((TextView) row.findViewById(R.id.pull_title)).setText(Json.str(pull, "title"));
        Date created = Json.date(pull, "created_at");
        String when = created == null ? "" : StringUtils.getNewsTimeStr(this, created);
        String meta = getString(R.string.pulls_meta, number, when, Json.str(pull, "user.login"),
                Json.str(pull, "head.ref"), Json.str(pull, "base.ref"));
        ((TextView) row.findViewById(R.id.pull_meta)).setText(meta);

        TextView badge = row.findViewById(R.id.pull_badge);
        ImageView icon = row.findViewById(R.id.pull_icon);
        int stateColor;
        if (!Json.str(pull, "merged_at").isEmpty()) {
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
        badge.setVisibility(badge.getText().length() == 0 ? View.GONE : View.VISIBLE);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(getResources().getColor(stateColor, getTheme())));
        row.setOnClickListener(v -> startActivity(PullRequestActivity.createIntent(this, owner, repo, number)));
        return row;
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
        newPullBn = root.findViewById(R.id.new_pull_bn);
        tabOpen.setOnClickListener(v -> selectTab(false));
        tabClosed.setOnClickListener(v -> selectTab(true));
        loadMoreBn.setOnClickListener(v -> loadNext());
        newPullBn.setOnClickListener(v -> startActivity(NewPullRequestActivity.createIntent(this, owner, repo)));
    }
}
