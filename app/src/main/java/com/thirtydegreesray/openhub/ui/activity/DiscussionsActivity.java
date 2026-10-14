package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.ComposeDialog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GraphQl;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Discussions of a repository (GraphQL only): Open or Closed, newest activity first,
 * optionally one category, and starting a new discussion.
 */
public class DiscussionsActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, DiscussionsActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).build());
    }

    private static final String LIST = "query($owner:String!,$repo:String!,$after:String,"
            + "$states:[DiscussionState!],$category:ID){repository(owner:$owner,name:$repo){"
            + "discussions(first:30,after:$after,states:$states,categoryId:$category,"
            + "orderBy:{field:UPDATED_AT,direction:DESC}){pageInfo{hasNextPage endCursor}"
            + "nodes{number title createdAt updatedAt isAnswered closed upvoteCount"
            + " author{login} category{name} comments{totalCount}}}}}";
    private static final String CATEGORIES = "query($owner:String!,$repo:String!){repository(owner:$owner,name:$repo){"
            + "id hasDiscussionsEnabled discussionCategories(first:25){nodes{id name description isAnswerable}}}}";
    private static final String CREATE = "mutation($repo:ID!,$category:ID!,$title:String!,$body:String!){"
            + "createDiscussion(input:{repositoryId:$repo,categoryId:$category,title:$title,body:$body}){"
            + "discussion{number}}}";

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean showClosed;
    @AutoAccess String categoryId;

    private TextView tabOpen;
    private TextView tabClosed;
    private LinearLayout list;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;
    private FloatingActionButton newBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ColorStateList tabNormalColor;
    @Nullable private String cursor;
    @Nullable private String repositoryId;
    private final List<JsonObject> categories = new ArrayList<>();

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
        setToolbarTitle(getString(R.string.discussions), owner + "/" + repo);
        tabNormalColor = tabOpen.getTextColors();
        newBn.setImageResource(R.drawable.ic_add);
        newBn.setContentDescription(getString(R.string.discussion_new));
        updateTabs();
        loadCategories();
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DiscussionActivity.changedSince(loadedStamp)) reload();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private long loadedStamp;

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

    private void loadCategories() {
        background(() -> GraphQl.run(CATEGORIES, GraphQl.vars("owner", owner, "repo", repo)), data -> {
            JsonObject repository = Json.obj(data, "repository");
            if (repository == null) return;
            repositoryId = Json.str(repository, "id");
            categories.clear();
            JsonElement nodes = Json.get(repository, "discussionCategories.nodes");
            if (nodes != null && nodes.isJsonArray()) {
                for (JsonElement e : nodes.getAsJsonArray()) categories.add(e.getAsJsonObject());
            }
            if (!Json.bool(repository, "hasDiscussionsEnabled")) {
                emptyText.setText(R.string.discussions_disabled);
                emptyText.setVisibility(View.VISIBLE);
                newBn.hide();
            } else {
                newBn.show();
            }
            invalidateOptionsMenu();
        }, false);
    }

    private void reload() {
        disposables.clear();
        list.removeAllViews();
        cursor = null;
        loadedStamp = DiscussionActivity.stamp();
        loadNext();
        if (categories.isEmpty()) loadCategories();
    }

    private void loadNext() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        loadMoreBn.setVisibility(View.GONE);
        List<String> states = Collections.singletonList(showClosed ? "CLOSED" : "OPEN");
        background(() -> GraphQl.run(LIST, GraphQl.vars("owner", owner, "repo", repo, "after", cursor,
                "states", states, "category", categoryId)), data -> {
            loader.setVisibility(View.GONE);
            JsonObject page = Json.obj(data, "repository.discussions");
            if (page == null) return;
            JsonElement nodes = page.get("nodes");
            if (nodes != null && nodes.isJsonArray()) {
                for (JsonElement e : nodes.getAsJsonArray()) list.addView(row(e.getAsJsonObject()));
            }
            boolean more = Json.bool(page, "pageInfo.hasNextPage");
            cursor = Json.str(page, "pageInfo.endCursor");
            loadMoreBn.setVisibility(more ? View.VISIBLE : View.GONE);
            if (list.getChildCount() == 0) {
                emptyText.setText(showClosed ? R.string.discussions_none_closed : R.string.discussions_none_open);
                emptyText.setVisibility(View.VISIBLE);
            }
        }, false);
    }

    @NonNull
    private View row(@NonNull JsonObject discussion) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_pull, list, false);
        int number = Json.integer(discussion, "number");
        ((TextView) row.findViewById(R.id.pull_title)).setText(Json.str(discussion, "title"));
        Date updated = Json.date(discussion, "updatedAt");
        String when = updated == null ? "" : StringUtils.getNewsTimeStr(this, updated);
        int comments = Json.integer(discussion, "comments.totalCount");
        String meta = getString(R.string.discussion_meta, number, Json.str(discussion, "category.name"),
                Json.str(discussion, "author.login"), when)
                + " · " + getResources().getQuantityString(R.plurals.discussion_comments, comments, comments);
        int votes = Json.integer(discussion, "upvoteCount");
        if (votes > 0) meta += " · ▲ " + votes;
        ((TextView) row.findViewById(R.id.pull_meta)).setText(meta);

        TextView badge = row.findViewById(R.id.pull_badge);
        boolean answered = Json.bool(discussion, "isAnswered");
        badge.setText(R.string.discussion_answered);
        badge.setVisibility(answered ? View.VISIBLE : View.GONE);
        ImageView icon = row.findViewById(R.id.pull_icon);
        icon.setImageResource(R.drawable.ic_comment);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(getResources().getColor(
                answered ? R.color.pull_open : R.color.pull_draft, getTheme())));
        row.setOnClickListener(v -> startActivity(DiscussionActivity.createIntent(this, owner, repo, number)));
        return row;
    }

    // ---------------------------------------------------------------------------------
    // New discussion and category filter
    // ---------------------------------------------------------------------------------

    private void newDiscussion() {
        if (repositoryId == null || categories.isEmpty()) {
            loadCategories();
            return;
        }
        CharSequence[] names = new CharSequence[categories.size()];
        int selected = 0;
        for (int i = 0; i < categories.size(); i++) {
            JsonObject c = categories.get(i);
            names[i] = Json.str(c, "name");
            if (Json.str(c, "id").equals(categoryId)) selected = i;
        }
        new ComposeDialog.Builder(this)
                .title(getString(R.string.discussion_new))
                .repo(owner + "/" + repo)
                .choices(names, selected)
                .titleField(getString(R.string.title), null)
                .body(getString(R.string.discussion_body_hint), null)
                .bodyRequired(true, null)
                .positive(getString(R.string.discussion_start))
                .callback((choice, title, body) -> {
                    String category = Json.str(categories.get(choice), "id");
                    showProgressDialog(getString(R.string.please_wait));
                    background(() -> GraphQl.run(CREATE, GraphQl.vars("repo", repositoryId,
                            "category", category, "title", title, "body", body)), data -> {
                        dismissProgressDialog();
                        int number = Json.integer(data, "createDiscussion.discussion.number");
                        DiscussionActivity.markChanged();
                        if (number > 0) startActivity(DiscussionActivity.createIntent(this, owner, repo, number));
                        reload();
                    }, true);
                })
                .show();
    }

    private void pickCategory() {
        CharSequence[] names = new CharSequence[categories.size() + 1];
        names[0] = getString(R.string.discussion_all_categories);
        int selected = 0;
        for (int i = 0; i < categories.size(); i++) {
            names[i + 1] = Json.str(categories.get(i), "name");
            if (Json.str(categories.get(i), "id").equals(categoryId)) selected = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.discussion_category)
                .setSingleChoiceItems(names, selected, (d, which) -> {
                    categoryId = which == 0 ? null : Json.str(categories.get(which - 1), "id");
                    setToolbarSubTitle(owner + "/" + repo + (which == 0 ? "" : " · " + names[which]));
                    d.dismiss();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.discussion_category)
                .setIcon(R.drawable.ic_filter_title)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, R.string.refresh);
        menu.add(0, 3, 2, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem category = menu.findItem(1);
        if (category != null) category.setVisible(!categories.isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            pickCategory();
            return true;
        } else if (item.getItemId() == 2) {
            reload();
            return true;
        } else if (item.getItemId() == 3) {
            AppOpener.openInCustomTabsOrBrowser(this, "https://github.com/" + owner + "/" + repo + "/discussions");
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
        newBn.setOnClickListener(v -> newDiscussion());
    }
}
