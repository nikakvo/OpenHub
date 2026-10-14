package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.ComposeDialog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GraphQl;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * One discussion: the post, its comments with their replies, the answer, upvotes.
 * Comment, reply, mark as answer, upvote, edit and delete your own.
 */
public class DiscussionActivity extends BaseActivity<IBaseContract.Presenter> {

    private static final Pattern WEB_URL = Pattern.compile("github\\.com/([^/]+)/([^/]+)/discussions/(\\d+)");
    private static final Pattern LIST_URL = Pattern.compile("github\\.com/([^/]+)/([^/?#]+)/discussions/?(?:[?#].*)?$");

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      int number) {
        return new Intent(context, DiscussionActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("number", number).build());
    }

    /** github.com/o/r/discussions/12 opens the discussion, github.com/o/r/discussions the list. */
    @Nullable
    public static Intent createIntent(@NonNull Context context, @NonNull String url) {
        Matcher one = WEB_URL.matcher(url);
        if (one.find()) return createIntent(context, one.group(1), one.group(2), Integer.parseInt(one.group(3)));
        Matcher all = LIST_URL.matcher(url);
        if (all.find()) return DiscussionsActivity.createIntent(context, all.group(1), all.group(2));
        return null;
    }

    // change stamp: the list reloads after a change made here
    private static long changeStamp = 1;

    static synchronized void markChanged() {
        changeStamp++;
    }

    static synchronized long stamp() {
        return changeStamp;
    }

    static synchronized boolean changedSince(long seen) {
        return seen != 0 && changeStamp != seen;
    }

    private static final String COMMENT_FIELDS = "id body url createdAt upvoteCount viewerHasUpvoted viewerCanUpvote"
            + " viewerCanUpdate viewerCanDelete author{login avatarUrl}";
    private static final String QUERY = "query($owner:String!,$repo:String!,$number:Int!,$after:String){"
            + "repository(owner:$owner,name:$repo){discussion(number:$number){"
            + "id number title body url createdAt closed locked upvoteCount viewerHasUpvoted viewerCanUpvote"
            + " author{login avatarUrl} category{name isAnswerable}"
            + " comments(first:50,after:$after){pageInfo{hasNextPage endCursor} totalCount nodes{"
            + COMMENT_FIELDS + " isAnswer viewerCanMarkAsAnswer viewerCanUnmarkAsAnswer"
            + " replies(first:50){nodes{" + COMMENT_FIELDS + "}}}}}}}";

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess int number;

    private LinearLayout content;
    private ProgressBar loader;
    private FloatingActionButton commentBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private Markwon markwon;
    @Nullable private JsonObject discussion;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_discussion;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.discussion_number, number), owner + "/" + repo);
        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(LinkifyPlugin.create())
                .build();
        load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------

    private void load() {
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            JsonObject first = null;
            List<JsonElement> comments = new ArrayList<>();
            String after = null;
            for (int page = 0; page < 10; page++) {
                JsonObject data = GraphQl.run(QUERY, GraphQl.vars("owner", owner, "repo", repo,
                        "number", number, "after", after));
                JsonObject d = Json.obj(data, "repository.discussion");
                if (d == null) throw new IllegalStateException(getString(R.string.discussion_not_found));
                if (first == null) first = d;
                JsonElement nodes = Json.get(d, "comments.nodes");
                if (nodes != null && nodes.isJsonArray()) for (JsonElement e : nodes.getAsJsonArray()) comments.add(e);
                if (!Json.bool(d, "comments.pageInfo.hasNextPage")) break;
                after = Json.str(d, "comments.pageInfo.endCursor");
            }
            com.google.gson.JsonArray all = new com.google.gson.JsonArray();
            for (JsonElement e : comments) all.add(e);
            first.add("_comments", all);
            return first;
        }, d -> {
            loader.setVisibility(View.GONE);
            discussion = d;
            show(d);
            invalidateOptionsMenu();
        }, false);
    }

    private void show(@NonNull JsonObject d) {
        content.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        TextView title = new TextView(this);
        title.setText(Json.str(d, "title"));
        title.setTextSize(20);
        title.setTextColor(primaryText());
        title.setPadding(dp(16), dp(12), dp(16), dp(4));
        content.addView(title);

        TextView meta = new TextView(this);
        StringBuilder m = new StringBuilder(Json.str(d, "category.name"));
        if (Json.bool(d, "closed")) m.append(" · ").append(getString(R.string.discussion_closed));
        if (Json.bool(d, "locked")) m.append(" · ").append(getString(R.string.discussion_locked));
        meta.setText(m);
        meta.setTextColor(secondaryText());
        meta.setTextSize(13);
        meta.setPadding(dp(16), 0, dp(16), dp(4));
        content.addView(meta);

        // the post
        View post = box(inflater, d, 0);
        content.addView(post);
        content.addView(actions(d, false, null));

        boolean answerable = Json.bool(d, "category.isAnswerable");
        JsonElement comments = d.get("_comments");
        if (comments != null && comments.isJsonArray()) {
            for (JsonElement e : comments.getAsJsonArray()) {
                JsonObject c = e.getAsJsonObject();
                content.addView(box(inflater, c, 0));
                content.addView(actions(c, answerable, c));
                JsonElement replies = Json.get(c, "replies.nodes");
                if (replies != null && replies.isJsonArray()) {
                    for (JsonElement r : replies.getAsJsonArray()) {
                        JsonObject reply = r.getAsJsonObject();
                        content.addView(box(inflater, reply, 32));
                        content.addView(actions(reply, false, null, 32));
                    }
                }
            }
        }
        boolean locked = Json.bool(d, "locked");
        if (locked) commentBn.hide();
        else commentBn.show();
    }

    /** A comment box (layout of the pull request conversation); {@code indent} in dp for replies. */
    @NonNull
    private View box(@NonNull LayoutInflater inflater, @NonNull JsonObject item, int indent) {
        View view = inflater.inflate(R.layout.layout_item_pr_comment, content, false);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.leftMargin = params.leftMargin + dp(indent);
        GlideApp.with(this).load(Json.str(item, "author.avatarUrl"))
                .into((ImageView) view.findViewById(R.id.comment_avatar));
        Date date = Json.date(item, "createdAt");
        String when = date == null ? "" : StringUtils.getNewsTimeStr(this, date);
        String login = Json.str(item, "author.login");
        ((TextView) view.findViewById(R.id.comment_header)).setText(
                (login.isEmpty() ? getString(R.string.discussion_ghost) : login) + " · " + when);
        TextView badge = view.findViewById(R.id.comment_badge);
        boolean answer = Json.bool(item, "isAnswer");
        badge.setText(R.string.discussion_answer_badge);
        badge.setVisibility(answer ? View.VISIBLE : View.GONE);
        TextView body = view.findViewById(R.id.comment_body);
        String text = Json.str(item, "body");
        if (text.isEmpty()) body.setVisibility(View.GONE);
        else markwon.setMarkdown(body, text);
        View.OnLongClickListener menu = v -> {
            commentMenu(v, item);
            return true;
        };
        view.setOnLongClickListener(menu);
        body.setOnLongClickListener(menu);
        return view;
    }

    @NonNull
    private View actions(@NonNull JsonObject item, boolean answerable, @Nullable JsonObject replyTarget) {
        return actions(item, answerable, replyTarget, 0);
    }

    /** ▲ upvotes · Reply · Mark as answer, under a box. */
    @NonNull
    private View actions(@NonNull JsonObject item, boolean answerable, @Nullable JsonObject replyTarget, int indent) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(16 + indent), 0, dp(16), 0);

        boolean voted = Json.bool(item, "viewerHasUpvoted");
        TextView vote = action("▲ " + Json.integer(item, "upvoteCount"));
        if (!voted) vote.setTextColor(secondaryText());
        if (Json.bool(item, "viewerCanUpvote") || voted) {
            vote.setOnClickListener(v -> mutate(voted
                    ? "mutation($id:ID!){removeUpvote(input:{subjectId:$id}){subject{id}}}"
                    : "mutation($id:ID!){addUpvote(input:{subjectId:$id}){subject{id}}}",
                    GraphQl.vars("id", Json.str(item, "id")), null));
        }
        bar.addView(vote);

        if (replyTarget != null && !Json.bool(discussion, "locked")) {
            TextView reply = action(getString(R.string.diff_reply));
            reply.setOnClickListener(v -> compose(Json.str(replyTarget, "id"),
                    getString(R.string.discussion_reply_to, Json.str(replyTarget, "author.login"))));
            bar.addView(reply);
        }
        if (answerable) {
            boolean isAnswer = Json.bool(item, "isAnswer");
            boolean can = isAnswer ? Json.bool(item, "viewerCanUnmarkAsAnswer") : Json.bool(item, "viewerCanMarkAsAnswer");
            if (can) {
                TextView mark = action(getString(isAnswer ? R.string.discussion_unmark_answer : R.string.discussion_mark_answer));
                mark.setOnClickListener(v -> mutate(isAnswer
                        ? "mutation($id:ID!){unmarkDiscussionCommentAsAnswer(input:{id:$id}){discussion{id}}}"
                        : "mutation($id:ID!){markDiscussionCommentAsAnswer(input:{id:$id}){discussion{id}}}",
                        GraphQl.vars("id", Json.str(item, "id")),
                        getString(isAnswer ? R.string.discussion_unmarked : R.string.discussion_marked)));
                bar.addView(mark);
            }
        }
        return bar;
    }

    @NonNull
    private TextView action(@NonNull String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(ViewUtils.getAccentColor(this));
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        view.setBackgroundResource(value.resourceId);
        return view;
    }

    // ---------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------

    /** A new comment, or a reply when {@code replyTo} is a comment id. */
    private void compose(@Nullable String replyTo, @Nullable String header) {
        if (discussion == null) return;
        ComposeDialog.Builder dialog = new ComposeDialog.Builder(this)
                .title(getString(replyTo == null ? R.string.discussion_comment_title : R.string.discussion_reply_title))
                .repo(owner + "/" + repo)
                .body(getString(R.string.diff_comment_hint), null)
                .bodyRequired(true, null)
                .positive(getString(R.string.comment));
        if (header != null) dialog.header(header);
        dialog.callback((choice, unused, body) -> {
            String mutation = "mutation($id:ID!,$body:String!,$reply:ID){addDiscussionComment(input:"
                    + "{discussionId:$id,body:$body,replyToId:$reply}){comment{id}}}";
            mutate(mutation, GraphQl.vars("id", Json.str(discussion, "id"), "body", body, "reply", replyTo),
                    getString(R.string.diff_comment_sent));
        }).show();
    }

    private void commentMenu(@NonNull View anchor, @NonNull JsonObject item) {
        boolean isPost = discussion != null && Json.str(item, "id").equals(Json.str(discussion, "id"));
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (!isPost && Json.bool(item, "viewerCanUpdate")) menu.getMenu().add(0, 1, 0, R.string.comment_edit);
        if (!isPost && Json.bool(item, "viewerCanDelete")) menu.getMenu().add(0, 2, 1, R.string.comment_delete);
        menu.getMenu().add(0, 3, 2, R.string.copy_url);
        menu.getMenu().add(0, 4, 3, R.string.copy_text);
        menu.setOnMenuItemClickListener(menuItem -> {
            String id = Json.str(item, "id");
            switch (menuItem.getItemId()) {
                case 1:
                    new ComposeDialog.Builder(this)
                            .title(getString(R.string.comment_edit))
                            .repo(owner + "/" + repo)
                            .body(getString(R.string.diff_comment_hint), Json.str(item, "body"))
                            .bodyRequired(true, null)
                            .positive(getString(R.string.save))
                            .callback((choice, unused, body) -> mutate(
                                    "mutation($id:ID!,$body:String!){updateDiscussionComment(input:{commentId:$id,body:$body}){comment{id}}}",
                                    GraphQl.vars("id", id, "body", body), null))
                            .show();
                    break;
                case 2:
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.comment_delete)
                            .setMessage(R.string.comment_delete_message)
                            .setPositiveButton(R.string.delete, (d, w) -> mutate(
                                    "mutation($id:ID!){deleteDiscussionComment(input:{id:$id}){comment{id}}}",
                                    GraphQl.vars("id", id), getString(R.string.comment_deleted)))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    break;
                case 3:
                    AppUtils.copyToClipboard(this, Json.str(item, "url"));
                    break;
                default:
                    AppUtils.copyToClipboard(this, Json.str(item, "body"));
                    break;
            }
            return true;
        });
        menu.show();
    }

    private void mutate(@NonNull String mutation, @NonNull java.util.Map<String, Object> variables,
                        @Nullable String successMessage) {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GraphQl.run(mutation, variables), done -> {
            dismissProgressDialog();
            markChanged();
            if (successMessage != null) showSuccessToast(successMessage);
            load();
        }, true);
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.refresh);
        menu.add(0, 2, 1, R.string.share);
        menu.add(0, 3, 2, R.string.copy_url);
        menu.add(0, 4, 3, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        String url = discussion != null && !Json.str(discussion, "url").isEmpty() ? Json.str(discussion, "url")
                : "https://github.com/" + owner + "/" + repo + "/discussions/" + number;
        switch (item.getItemId()) {
            case 1:
                load();
                return true;
            case 2:
                AppOpener.shareText(this, url);
                return true;
            case 3:
                AppUtils.copyToClipboard(this, url);
                return true;
            case 4:
                AppOpener.openInCustomTabsOrBrowser(this, url);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

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

    private int primaryText() {
        android.content.res.TypedArray array = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color = array.getColor(0, 0xFFFFFFFF);
        array.recycle();
        return color;
    }

    private int secondaryText() {
        android.content.res.TypedArray array = obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        int color = array.getColor(0, 0xFF8B949E);
        array.recycle();
        return color;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        content = root.findViewById(R.id.discussion_content);
        loader = root.findViewById(R.id.loader);
        commentBn = root.findViewById(R.id.comment_bn);
        commentBn.setOnClickListener(v -> compose(null, null));
    }
}
