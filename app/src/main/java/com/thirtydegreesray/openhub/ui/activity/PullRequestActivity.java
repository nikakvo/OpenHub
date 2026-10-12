package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.CommitFile;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.ComposeDialog;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.PullChanges;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.RepoCommitter;
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
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * One pull request, like on github.com: Conversation (description, comments and
 * reviews, checks, merge box), Commits and Files changed. Write access merges,
 * closes, reopens, marks ready for review and deletes the branch; everyone who can
 * see it comments and reviews.
 */
public class PullRequestActivity extends BaseActivity<IBaseContract.Presenter> {

    private static final Pattern WEB_URL = Pattern.compile(
            "github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");
    private static final Pattern API_URL = Pattern.compile(
            "api\\.github\\.com/repos/([^/]+)/([^/]+)/pulls/(\\d+)");

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      int number) {
        return new Intent(context, PullRequestActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("number", number).build());
    }

    /** github.com/o/r/pull/12 or api.github.com/repos/o/r/pulls/12; null if it is neither. */
    @Nullable
    public static Intent createIntent(@NonNull Context context, @NonNull String url) {
        Matcher matcher = WEB_URL.matcher(url);
        if (!matcher.find()) {
            matcher = API_URL.matcher(url);
            if (!matcher.find()) return null;
        }
        return createIntent(context, matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)));
    }

    public static boolean isPullUrl(@NonNull String url) {
        return WEB_URL.matcher(url).find() || API_URL.matcher(url).find();
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess int number;
    @AutoAccess int tab;

    private TextView tabConversation;
    private TextView tabCommits;
    private TextView tabFiles;
    private NestedScrollView conversationScroll;
    private NestedScrollView commitsScroll;
    private NestedScrollView filesScroll;
    private TextView titleText;
    private TextView stateText;
    private TextView summaryText;
    private CodeWebView bodyWeb;
    private LinearLayout timeline;
    private View mergeBox;
    private TextView checksStatus;
    private TextView mergeStatus;
    private Button mergeBn;
    private Button readyBn;
    private Button deleteBranchBn;
    private Button closeBn;
    private LinearLayout commitsList;
    private LinearLayout filesList;
    private TextView filesSummary;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ColorStateList tabNormalColor;
    private Markwon markwon;

    private JsonObject pull;
    private JsonObject repoJson;
    private boolean commitsLoaded;
    private boolean filesLoaded;
    private boolean mergeableRetried;
    private boolean headBranchExists;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_pull_request;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.pull_title_number, number), owner + "/" + repo);
        tabNormalColor = tabConversation.getTextColors();
        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(LinkifyPlugin.create())
                .build();
        showTab(tab);
        load();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        disposables.clear();
        super.onDestroy();
    }

    private PullService pulls() {
        return GitHubApi.service(PullService.class);
    }

    // ---------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------

    private void showTab(int index) {
        tab = index;
        conversationScroll.setVisibility(index == 0 && pull != null ? View.VISIBLE
                : index == 0 ? View.INVISIBLE : View.GONE);
        commitsScroll.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        filesScroll.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        styleTab(tabConversation, index == 0);
        styleTab(tabCommits, index == 1);
        styleTab(tabFiles, index == 2);
        if (index == 1 && !commitsLoaded && pull != null) loadCommits();
        if (index == 2 && !filesLoaded && pull != null) loadFiles();
    }

    private void styleTab(@NonNull TextView view, boolean selected) {
        view.setTextColor(selected ? ColorStateList.valueOf(ViewUtils.getAccentColor(this)) : tabNormalColor);
        view.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        if (selected) {
            view.setBackgroundResource(R.drawable.bg_tab_selected);
        } else {
            TypedValue ripple = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            view.setBackgroundResource(ripple.resourceId);
        }
    }

    // ---------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------

    private void load() {
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            JsonObject p = GitHubApi.call(pulls().getPull(owner, repo, number));
            JsonObject r = GitHubApi.call(GitHubApi.service(RepoSettingsService.class).getRepo(owner, repo));
            List<JsonObject> items = new ArrayList<>();
            JsonArray comments = GitHubApi.call(pulls().getComments(owner, repo, number));
            if (comments != null) for (JsonElement c : comments) items.add(c.getAsJsonObject());
            JsonArray reviews = GitHubApi.call(pulls().getReviews(owner, repo, number));
            if (reviews != null) {
                for (JsonElement e : reviews) {
                    JsonObject review = e.getAsJsonObject();
                    // a review without text and without a verdict is the shell of line comments
                    if ("COMMENTED".equals(Json.str(review, "state")) && Json.str(review, "body").isEmpty()) continue;
                    review.addProperty("created_at", Json.str(review, "submitted_at"));
                    review.addProperty("_review", true);
                    items.add(review);
                }
            }
            items.sort((a, b) -> Json.str(a, "created_at").compareTo(Json.str(b, "created_at")));
            String checks = checksSummary(Json.str(p, "head.sha"));
            boolean branchExists = headBranchExists(p);
            return new Object[]{p, r, items, checks, branchExists};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            pull = (JsonObject) result[0];
            repoJson = (JsonObject) result[1];
            //noinspection unchecked
            showConversation((List<JsonObject>) result[2], (String) result[3]);
            headBranchExists = (Boolean) result[4];
            updateMergeBox((String) result[3]);
            showTab(tab);
            invalidateOptionsMenu();
        }, error -> loader.setVisibility(View.GONE));
    }

    /** "All checks have passed (3)", "1 failing, 2 successful", or null when there are none. */
    @Nullable
    private String checksSummary(@NonNull String sha) {
        if (sha.isEmpty()) return null;
        int success = 0, failure = 0, pending = 0;
        try {
            JsonObject runs = GitHubApi.call(pulls().getCheckRuns(owner, repo, sha));
            if (runs != null && runs.has("check_runs")) {
                for (JsonElement e : runs.getAsJsonArray("check_runs")) {
                    JsonObject run = e.getAsJsonObject();
                    if (!"completed".equals(Json.str(run, "status"))) pending++;
                    else {
                        String c = Json.str(run, "conclusion");
                        if (c.equals("success") || c.equals("neutral") || c.equals("skipped")) success++;
                        else failure++;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            JsonObject status = GitHubApi.call(pulls().getCombinedStatus(owner, repo, sha));
            if (status != null && status.has("statuses")) {
                for (JsonElement e : status.getAsJsonArray("statuses")) {
                    String s = Json.str(e.getAsJsonObject(), "state");
                    if (s.equals("success")) success++;
                    else if (s.equals("pending")) pending++;
                    else failure++;
                }
            }
        } catch (Exception ignored) {
        }
        int total = success + failure + pending;
        if (total == 0) return null;
        if (failure == 0 && pending == 0) return getString(R.string.pull_checks_passed, total);
        List<String> parts = new ArrayList<>();
        if (failure > 0) parts.add(getString(R.string.pull_checks_failing, failure));
        if (pending > 0) parts.add(getString(R.string.pull_checks_pending, pending));
        if (success > 0) parts.add(getString(R.string.pull_checks_successful, success));
        return String.join(", ", parts);
    }

    /** The compare branch lives in this repository (not in a fork), so it can be deleted or restored here. */
    private boolean isOwnHeadBranch(@NonNull JsonObject p) {
        return Json.str(p, "head.repo.full_name").equalsIgnoreCase(owner + "/" + repo)
                && !Json.str(p, "head.ref").isEmpty();
    }

    private boolean headBranchExists(@NonNull JsonObject p) {
        String branch = Json.str(p, "head.ref");
        if (!isOwnHeadBranch(p)) return false;
        try {
            return pulls().getBranch(owner, repo, RepoCommitter.encode(branch)).blockingFirst().isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------
    // Conversation
    // ---------------------------------------------------------------------------------

    private boolean isMerged() {
        return !Json.str(pull, "merged_at").isEmpty();
    }

    private boolean isClosed() {
        return "closed".equals(Json.str(pull, "state"));
    }

    private boolean isDraft() {
        return Json.bool(pull, "draft");
    }

    private boolean canWrite() {
        return Json.bool(repoJson, "permissions.push") || Json.bool(repoJson, "permissions.admin");
    }

    private boolean isAuthor() {
        return AppData.INSTANCE.getLoggedUser() != null
                && AppData.INSTANCE.getLoggedUser().getLogin().equalsIgnoreCase(Json.str(pull, "user.login"));
    }

    private void showConversation(@NonNull List<JsonObject> items, @Nullable String checks) {
        titleText.setText(Json.str(pull, "title"));
        setToolbarTitle(getString(R.string.pull_title_number, number), owner + "/" + repo);
        @ColorRes int color;
        int label;
        if (isMerged()) {
            color = R.color.pull_merged;
            label = R.string.pull_state_merged;
        } else if (isClosed()) {
            color = R.color.pull_closed;
            label = R.string.pull_state_closed;
        } else if (isDraft()) {
            color = R.color.pull_draft;
            label = R.string.pull_state_draft;
        } else {
            color = R.color.pull_open;
            label = R.string.pull_state_open;
        }
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(getResources().getDisplayMetrics().density * 12);
        pill.setColor(getResources().getColor(color, getTheme()));
        stateText.setBackground(pill);
        stateText.setText(label);

        int commits = Json.integer(pull, "commits");
        String author = Json.str(pull, "user.login");
        String base = Json.str(pull, "base.ref");
        String head = Json.str(pull, "head.label");
        summaryText.setText(isMerged()
                ? getString(R.string.pull_summary_merged, Json.str(pull, "merged_by.login"), commits, base, head)
                : getString(R.string.pull_summary_open, author, commits, base, head));

        tabCommits.setText(getString(R.string.pull_tab_commits_count, commits));
        tabFiles.setText(getString(R.string.pull_tab_files_count, Json.integer(pull, "changed_files")));

        String body = Json.str(pull, "body_html");
        if (body.isEmpty() && !Json.str(pull, "body").isEmpty()) body = MarkdownHtml.render(Json.str(pull, "body"));
        if (body.isEmpty()) body = "<p><i>" + getString(R.string.pull_no_description) + "</i></p>";
        bodyWeb.setMdSource(body, null);

        timeline.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (JsonObject item : items) {
            View view = inflater.inflate(R.layout.layout_item_pr_comment, timeline, false);
            GlideApp.with(this).load(Json.str(item, "user.avatar_url"))
                    .into((ImageView) view.findViewById(R.id.comment_avatar));
            Date date = Json.date(item, "created_at");
            String when = date == null ? "" : StringUtils.getNewsTimeStr(this, date);
            ((TextView) view.findViewById(R.id.comment_header)).setText(
                    Json.str(item, "user.login") + " · " + when);
            TextView badge = view.findViewById(R.id.comment_badge);
            if (Json.bool(item, "_review")) {
                String state = Json.str(item, "state");
                int text = state.equals("APPROVED") ? R.string.pull_review_approved
                        : state.equals("CHANGES_REQUESTED") ? R.string.pull_review_changes
                        : state.equals("DISMISSED") ? R.string.pull_review_dismissed
                        : R.string.pull_review_commented;
                badge.setText(text);
                badge.setVisibility(View.VISIBLE);
            }
            TextView bodyView = view.findViewById(R.id.comment_body);
            String text = Json.str(item, "body");
            if (text.isEmpty()) bodyView.setVisibility(View.GONE);
            else markwon.setMarkdown(bodyView, text);
            timeline.addView(view);
        }
    }

    private void updateMergeBox(@Nullable String checks) {
        checksStatus.setText(checks);
        checksStatus.setVisibility(checks == null ? View.GONE : View.VISIBLE);
        boolean write = canWrite();
        boolean author = isAuthor();

        mergeBn.setVisibility(View.GONE);
        readyBn.setVisibility(View.GONE);
        deleteBranchBn.setVisibility(View.GONE);
        closeBn.setVisibility((write || author) && !isMerged() ? View.VISIBLE : View.GONE);
        closeBn.setText(isClosed() ? R.string.pull_reopen : R.string.pull_close);

        if (isMerged() || isClosed()) {
            String text = isMerged() ? getString(R.string.pull_merged_into, Json.str(pull, "base.ref"))
                    : getString(R.string.pull_closed_unmerged);
            boolean ownBranch = isOwnHeadBranch(pull);
            if (ownBranch && !headBranchExists) {
                text += "\n" + getString(R.string.pull_branch_deleted, Json.str(pull, "head.ref"));
            }
            mergeStatus.setText(text);
            if (write && ownBranch) {
                deleteBranchBn.setText(headBranchExists ? R.string.pull_delete_branch : R.string.pull_restore_branch);
                deleteBranchBn.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (isDraft()) {
            mergeStatus.setText(R.string.pull_draft_status);
            if (write || author) {
                readyBn.setText(R.string.pull_ready);
                readyBn.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (write || author) {
            readyBn.setText(R.string.pull_to_draft);
            readyBn.setVisibility(View.VISIBLE);
        }
        String state = Json.str(pull, "mergeable_state");
        if (Json.get(pull, "mergeable") == null || state.equals("unknown")) {
            mergeStatus.setText(R.string.pull_checking_merge);
            if (!mergeableRetried) {
                // GitHub computes mergeability in the background after a change
                mergeableRetried = true;
                handler.postDelayed(this::load, 3000);
            }
        } else if (state.equals("dirty")) {
            mergeStatus.setText(R.string.pull_conflicts);
        } else if (state.equals("blocked")) {
            mergeStatus.setText(R.string.pull_blocked);
        } else if (state.equals("behind")) {
            mergeStatus.setText(R.string.pull_behind);
        } else if (state.equals("unstable")) {
            mergeStatus.setText(R.string.pull_unstable);
        } else {
            mergeStatus.setText(R.string.pull_mergeable);
        }
        if (write && !state.equals("dirty")) mergeBn.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------------------------
    // Commits and files
    // ---------------------------------------------------------------------------------

    private void loadCommits() {
        commitsLoaded = true;
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            JsonArray all = new JsonArray();
            for (int page = 1; page <= 3; page++) {
                JsonArray array = GitHubApi.call(pulls().getCommits(owner, repo, number, page));
                if (array == null || array.size() == 0) break;
                all.addAll(array);
                if (array.size() < 100) break;
            }
            return all;
        }, (JsonArray array) -> {
            loader.setVisibility(View.GONE);
            commitsList.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            for (JsonElement e : array) {
                JsonObject commit = e.getAsJsonObject();
                View row = inflater.inflate(R.layout.layout_setting_row, commitsList, false);
                String message = Json.str(commit, "commit.message");
                int newline = message.indexOf('\n');
                ((TextView) row.findViewById(R.id.setting_title))
                        .setText(newline > 0 ? message.substring(0, newline) : message);
                String sha = Json.str(commit, "sha");
                Date date = Json.date(commit, "commit.author.date");
                String author = Json.str(commit, "author.login");
                if (author.isEmpty()) author = Json.str(commit, "commit.author.name");
                ((TextView) row.findViewById(R.id.setting_summary)).setText(
                        sha.substring(0, Math.min(7, sha.length())) + " · " + author
                                + (date == null ? "" : " · " + StringUtils.getNewsTimeStr(this, date)));
                String url = Json.str(commit, "url");
                row.setOnClickListener(v -> CommitDetailActivity.show(this, url));
                commitsList.addView(row);
            }
        }, error -> {
            loader.setVisibility(View.GONE);
            commitsLoaded = false;
        });
    }

    private void loadFiles() {
        filesLoaded = true;
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            JsonArray all = new JsonArray();
            for (int page = 1; page <= 30; page++) {
                JsonArray array = GitHubApi.call(pulls().getFiles(owner, repo, number, page));
                if (array == null || array.size() == 0) break;
                all.addAll(array);
                if (array.size() < 100) break;
            }
            return all;
        }, (JsonArray array) -> {
            loader.setVisibility(View.GONE);
            filesList.removeAllViews();
            int additions = 0, deletions = 0;
            LayoutInflater inflater = LayoutInflater.from(this);
            Gson gson = new Gson();
            for (JsonElement e : array) {
                JsonObject file = e.getAsJsonObject();
                additions += Json.integer(file, "additions");
                deletions += Json.integer(file, "deletions");
                View row = inflater.inflate(R.layout.layout_setting_row, filesList, false);
                ((TextView) row.findViewById(R.id.setting_title)).setText(Json.str(file, "filename"));
                ((TextView) row.findViewById(R.id.setting_summary)).setText(getString(R.string.pull_file_meta,
                        Json.str(file, "status"), Json.integer(file, "additions"), Json.integer(file, "deletions")));
                row.setOnClickListener(v -> {
                    try {
                        CommitFile commitFile = gson.fromJson(file, CommitFile.class);
                        ViewerActivity.showForDiff(this, commitFile);
                    } catch (Exception ex) {
                        AppOpener.openInCustomTabsOrBrowser(this, Json.str(file, "blob_url"));
                    }
                });
                filesList.addView(row);
            }
            filesSummary.setText(getString(R.string.pull_files_summary, array.size(), additions, deletions));
        }, error -> {
            loader.setVisibility(View.GONE);
            filesLoaded = false;
        });
    }

    // ---------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------

    private void comment() {
        new ComposeDialog.Builder(this)
                .title(getString(R.string.pull_comment))
                .repo(owner + "/" + repo)
                .body(getString(R.string.pull_comment_hint), null)
                .bodyRequired(true, null)
                .positive(getString(R.string.pull_comment))
                .callback((choice, title, body) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("body", body);
                    run(() -> GitHubApi.call(pulls().createComment(owner, repo, number, data)), null);
                })
                .show();
    }

    private void review() {
        CharSequence[] choices = {getString(R.string.pull_review_comment),
                getString(R.string.pull_review_approve), getString(R.string.pull_review_request_changes)};
        String[] events = {"COMMENT", "APPROVE", "REQUEST_CHANGES"};
        new ComposeDialog.Builder(this)
                .title(getString(R.string.pull_review))
                .repo(owner + "/" + repo)
                .choices(choices, 0)
                .body(getString(R.string.pull_review_hint), null)
                // an approval may come without words; a comment or change request needs them
                .bodyRequired(true, new boolean[]{false, true, false})
                .positive(getString(R.string.pull_review_submit))
                .callback((choice, title, body) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("event", events[choice]);
                    if (!body.isEmpty()) data.put("body", body);
                    run(() -> GitHubApi.call(pulls().createReview(owner, repo, number, data)), null);
                })
                .show();
    }

    private void merge() {
        List<String> methods = new ArrayList<>();
        List<CharSequence> labels = new ArrayList<>();
        if (Json.bool(repoJson, "allow_merge_commit") || !repoJson.has("allow_merge_commit")) {
            methods.add("merge");
            labels.add(getString(R.string.pull_merge_commit));
        }
        if (Json.bool(repoJson, "allow_squash_merge")) {
            methods.add("squash");
            labels.add(getString(R.string.pull_merge_squash));
        }
        if (Json.bool(repoJson, "allow_rebase_merge")) {
            methods.add("rebase");
            labels.add(getString(R.string.pull_merge_rebase));
        }
        String title = Json.str(pull, "title");
        String head = Json.str(pull, "head.label");
        String mergeTitle = getString(R.string.pull_merge_default_title, number, head);
        String squashTitle = title + " (#" + number + ")";
        new ComposeDialog.Builder(this)
                .title(getString(R.string.pull_merge))
                .repo(owner + "/" + repo)
                .choices(labels.toArray(new CharSequence[0]), 0)
                .titleField(getString(R.string.pull_merge_commit_title),
                        methods.get(0).equals("squash") ? squashTitle : mergeTitle)
                .body(getString(R.string.pull_merge_commit_message),
                        methods.get(0).equals("merge") ? title : Json.str(pull, "body"))
                .positive(getString(R.string.pull_merge_confirm))
                .callback((choice, commitTitle, message) -> {
                    String method = methods.get(choice);
                    Map<String, Object> data = new HashMap<>();
                    data.put("merge_method", method);
                    data.put("sha", Json.str(pull, "head.sha"));
                    if (!method.equals("rebase")) {
                        data.put("commit_title", commitTitle);
                        data.put("commit_message", message);
                    }
                    run(() -> GitHubApi.call(pulls().merge(owner, repo, number, data)),
                            getString(R.string.pull_merged_toast));
                })
                .show();
    }

    private void toggleClosed() {
        boolean reopen = isClosed();
        Runnable change = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("state", reopen ? "open" : "closed");
            run(() -> GitHubApi.call(pulls().updatePull(owner, repo, number, data)), null);
        };
        if (reopen) {
            change.run();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.pull_close)
                    .setMessage(R.string.pull_close_message)
                    .setPositiveButton(R.string.pull_close, (d, w) -> change.run())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void toggleDraft() {
        boolean ready = isDraft();
        String mutation = ready
                ? "mutation($id:ID!){markPullRequestReadyForReview(input:{pullRequestId:$id}){pullRequest{isDraft}}}"
                : "mutation($id:ID!){convertPullRequestToDraft(input:{pullRequestId:$id}){pullRequest{isDraft}}}";
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", Json.str(pull, "node_id"));
        Map<String, Object> data = new HashMap<>();
        data.put("query", mutation);
        data.put("variables", variables);
        run(() -> {
            JsonObject answer = GitHubApi.call(pulls().graphql(data));
            if (answer != null && answer.has("errors") && answer.get("errors").isJsonArray()
                    && answer.getAsJsonArray("errors").size() > 0) {
                throw new GitHubApi.ApiException(422,
                        Json.str(answer.getAsJsonArray("errors").get(0).getAsJsonObject(), "message"));
            }
            return answer;
        }, null);
    }

    private void deleteBranch() {
        String branch = Json.str(pull, "head.ref");
        new AlertDialog.Builder(this)
                .setTitle(R.string.pull_delete_branch)
                .setMessage(getString(R.string.branches_delete_message, branch))
                .setPositiveButton(R.string.delete, (d, w) -> run(() -> {
                    GitHubApi.call(GitHubApi.service(RepoSettingsService.class)
                            .deleteBranch(owner, repo, RepoCommitter.encode(branch)));
                    RepoChanges.markChanged();
                    return Boolean.TRUE;
                }, getString(R.string.branches_deleted, branch)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Like "Restore branch" on github.com: recreate the branch at the last commit of the pull request. */
    private void restoreBranch() {
        String branch = Json.str(pull, "head.ref");
        String sha = Json.str(pull, "head.sha");
        if (sha.isEmpty()) return;
        run(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("ref", "refs/heads/" + branch);
            body.put("sha", sha);
            GitHubApi.call(GitHubApi.service(GitDataService.class).createRef(owner, repo, body));
            RepoChanges.markChanged();
            return Boolean.TRUE;
        }, getString(R.string.pull_branch_restored, branch));
    }

    private void edit() {
        new ComposeDialog.Builder(this)
                .title(getString(R.string.pull_edit))
                .repo(owner + "/" + repo)
                .titleField(getString(R.string.title), Json.str(pull, "title"))
                .body(getString(R.string.pull_description_hint), Json.str(pull, "body"))
                .positive(getString(R.string.save))
                .callback((choice, title, body) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("title", title);
                    data.put("body", body);
                    run(() -> GitHubApi.call(pulls().updatePull(owner, repo, number, data)), null);
                })
                .show();
    }

    /** Runs a change, then reloads the pull request. */
    private <T> void run(@NonNull Callable<T> work, @Nullable String successMessage) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            PullChanges.markChanged();
            if (successMessage != null) showSuccessToast(successMessage);
            mergeableRetried = false;
            commitsLoaded = false;
            filesLoaded = false;
            load();
        }, error -> dismissProgressDialog());
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.pull_edit);
        menu.add(0, 2, 1, R.string.refresh);
        menu.add(0, 3, 2, R.string.share);
        menu.add(0, 4, 3, R.string.copy_url);
        menu.add(0, 5, 4, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem edit = menu.findItem(1);
        if (edit != null) edit.setVisible(pull != null && (canWrite() || isAuthor()));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String url = pull == null ? "https://github.com/" + owner + "/" + repo + "/pull/" + number
                : Json.str(pull, "html_url");
        switch (item.getItemId()) {
            case 1:
                if (pull != null) edit();
                return true;
            case 2:
                commitsLoaded = false;
                filesLoaded = false;
                load();
                return true;
            case 3:
                AppOpener.shareText(this, url);
                return true;
            case 4:
                AppUtils.copyToClipboard(this, url);
                return true;
            case 5:
                AppOpener.openInCustomTabsOrBrowser(this, url);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------------------------

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        tabConversation = root.findViewById(R.id.tab_conversation);
        tabCommits = root.findViewById(R.id.tab_commits);
        tabFiles = root.findViewById(R.id.tab_files);
        conversationScroll = root.findViewById(R.id.conversation_scroll);
        commitsScroll = root.findViewById(R.id.commits_scroll);
        filesScroll = root.findViewById(R.id.files_scroll);
        titleText = root.findViewById(R.id.pr_title);
        stateText = root.findViewById(R.id.pr_state);
        summaryText = root.findViewById(R.id.pr_summary);
        bodyWeb = root.findViewById(R.id.pr_body);
        timeline = root.findViewById(R.id.timeline);
        mergeBox = root.findViewById(R.id.merge_box);
        checksStatus = root.findViewById(R.id.checks_status);
        mergeStatus = root.findViewById(R.id.merge_status);
        mergeBn = root.findViewById(R.id.merge_bn);
        readyBn = root.findViewById(R.id.ready_bn);
        deleteBranchBn = root.findViewById(R.id.delete_branch_bn);
        closeBn = root.findViewById(R.id.close_bn);
        commitsList = root.findViewById(R.id.commits_list);
        filesList = root.findViewById(R.id.files_list);
        filesSummary = root.findViewById(R.id.files_summary);
        loader = root.findViewById(R.id.loader);
        tabConversation.setOnClickListener(v -> showTab(0));
        tabCommits.setOnClickListener(v -> showTab(1));
        tabFiles.setOnClickListener(v -> showTab(2));
        mergeBn.setOnClickListener(v -> merge());
        readyBn.setOnClickListener(v -> toggleDraft());
        deleteBranchBn.setOnClickListener(v -> {
            if (headBranchExists) deleteBranch();
            else restoreBranch();
        });
        closeBn.setOnClickListener(v -> toggleClosed());
        root.findViewById(R.id.comment_bn).setOnClickListener(v -> comment());
        root.findViewById(R.id.review_bn).setOnClickListener(v -> review());
    }
}
