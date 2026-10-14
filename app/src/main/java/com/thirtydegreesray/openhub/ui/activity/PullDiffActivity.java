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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.ComposeDialog;
import com.thirtydegreesray.openhub.ui.widget.ReviewDialog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.ReviewDrafts;
import com.thirtydegreesray.openhub.util.ReviewThreads;
import com.thirtydegreesray.openhub.util.PullChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * The diff of one file of a pull request with its line comments, like "Files changed"
 * on github.com: tap a line to comment, long-press a line and tap another to comment on
 * a range, reply to threads, suggest changes, edit or delete your own comments.
 */
public class PullDiffActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo,
                                      int number, @NonNull String path, @NonNull String headSha) {
        return new Intent(context, PullDiffActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("number", number)
                .put("path", path).put("headSha", headSha).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess int number;
    @AutoAccess String path;
    @AutoAccess String headSha;

    private RecyclerView list;
    private ProgressBar loader;
    private View reviewBar;
    private TextView reviewBarText;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<Row> rows = new ArrayList<>();
    private final DiffAdapter adapter = new DiffAdapter();
    private Markwon markwon;
    @Nullable private JsonObject file;
    /** row index of the first line of a range comment, -1 when none */
    private int rangeStart = -1;
    /** review comments of this file as last loaded, to rebuild after a local change */
    private List<JsonObject> lastComments = new ArrayList<>();
    /** review threads by the id of their first comment */
    private Map<Long, ReviewThreads.Thread> threads = new HashMap<>();
    /** resolved threads the user opened */
    private final java.util.Set<Long> expanded = new java.util.HashSet<>();

    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    // ---------------------------------------------------------------------------------
    // Rows
    // ---------------------------------------------------------------------------------

    private static final int HUNK_ROW = 0, ADD = 1, DEL = 2, CTX = 3, META = 4,
            COMMENT = 5, REPLY = 6, NOTE = 7, RESOLVED = 8;

    private static final class Row {
        final int type;
        String text = "";
        int oldNo, newNo;
        int hunk;
        @Nullable JsonObject comment;
        long threadId;

        Row(int type) {
            this.type = type;
        }

        boolean isLine() {
            return type == ADD || type == DEL || type == CTX;
        }

        /** RIGHT for added and unchanged lines (new file), LEFT for removed lines. */
        String side() {
            return type == DEL ? "LEFT" : "RIGHT";
        }

        int line() {
            return type == DEL ? oldNo : newNo;
        }
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_pull_diff;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(fileName(), getString(R.string.pull_title_number, number) + " · " + owner + "/" + repo);
        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(LinkifyPlugin.create())
                .build();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    @NonNull
    private String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private PullService pulls() {
        return GitHubApi.service(PullService.class);
    }

    // ---------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------

    private void load() {
        loader.setVisibility(View.VISIBLE);
        background(() -> {
            JsonObject found = null;
            for (int page = 1; page <= 30 && found == null; page++) {
                JsonArray array = GitHubApi.call(pulls().getFiles(owner, repo, number, page));
                if (array == null || array.size() == 0) break;
                for (JsonElement e : array) {
                    if (path.equals(Json.str(e.getAsJsonObject(), "filename"))) {
                        found = e.getAsJsonObject();
                        break;
                    }
                }
                if (array.size() < 100) break;
            }
            List<JsonObject> comments = new ArrayList<>();
            for (int page = 1; page <= 30; page++) {
                JsonArray array = GitHubApi.call(pulls().getReviewComments(owner, repo, number, page));
                if (array == null || array.size() == 0) break;
                for (JsonElement e : array) {
                    JsonObject c = e.getAsJsonObject();
                    if (path.equals(Json.str(c, "path"))) comments.add(c);
                }
                if (array.size() < 100) break;
            }
            Map<Long, ReviewThreads.Thread> found2 = comments.isEmpty()
                    ? new HashMap<>() : ReviewThreads.fetch(owner, repo, number);
            return new Object[]{found == null ? new JsonObject() : found, comments, found2};
        }, (Object[] result) -> {
            loader.setVisibility(View.GONE);
            JsonObject found = (JsonObject) result[0];
            file = found.size() == 0 ? null : found;
            //noinspection unchecked
            threads = (Map<Long, ReviewThreads.Thread>) result[2];
            //noinspection unchecked
            lastComments = (List<JsonObject>) result[1];
            build(lastComments);
            invalidateOptionsMenu();
        }, error -> loader.setVisibility(View.GONE));
    }

    private void build(@NonNull List<JsonObject> comments) {
        rows.clear();
        rangeStart = -1;

        // threads: GitHub points every reply at the first comment of its thread
        Map<Long, List<JsonObject>> threads = new LinkedHashMap<>();
        for (JsonObject c : comments) {
            long reply = Json.str(c, "in_reply_to_id").isEmpty() ? 0 : longOf(c, "in_reply_to_id");
            if (reply == 0) threads.put(longOf(c, "id"), listOf(c));
        }
        for (JsonObject c : comments) {
            long reply = Json.str(c, "in_reply_to_id").isEmpty() ? 0 : longOf(c, "in_reply_to_id");
            if (reply == 0) continue;
            List<JsonObject> thread = threads.get(reply);
            if (thread != null) thread.add(c);
            else threads.put(longOf(c, "id"), listOf(c));
        }

        List<JsonObject> drafts = new ArrayList<>();
        for (JsonObject draft : ReviewDrafts.list(this, owner, repo, number)) {
            if (path.equals(Json.str(draft, "path"))) drafts.add(draftComment(draft));
        }
        updateReviewBar();

        if (file == null) {
            note(getString(R.string.diff_not_found));
            addThreads(threads);
            addDrafts(drafts);
            adapter.notifyDataSetChanged();
            return;
        }
        String patch = Json.str(file, "patch");
        if (patch.isEmpty()) {
            note(getString(R.string.diff_too_large));
            addThreads(threads);
            addDrafts(drafts);
            adapter.notifyDataSetChanged();
            return;
        }
        note(getString(R.string.diff_tip));

        List<Row> lines = parse(patch);
        // where each thread goes: after its (last) line, matched by side and line number
        Map<String, List<Long>> placed = new HashMap<>();
        List<Long> outdated = new ArrayList<>();
        for (Map.Entry<Long, List<JsonObject>> entry : threads.entrySet()) {
            JsonObject root = entry.getValue().get(0);
            String line = Json.str(root, "line");
            String key = Json.str(root, "side").isEmpty() ? "RIGHT" : Json.str(root, "side");
            if (line.isEmpty()) {
                outdated.add(entry.getKey());
                continue;
            }
            key += ":" + line;
            boolean exists = false;
            for (Row r : lines) {
                if (r.isLine() && key.equals(r.side() + ":" + r.line())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                outdated.add(entry.getKey());
                continue;
            }
            placed.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
        }

        // pending review comments go to their line too
        Map<String, List<JsonObject>> draftsAt = new HashMap<>();
        List<JsonObject> draftsElsewhere = new ArrayList<>();
        for (JsonObject draft : drafts) {
            String key = Json.str(draft, "side") + ":" + Json.str(draft, "line");
            boolean exists = false;
            for (Row r : lines) {
                if (r.isLine() && key.equals(r.side() + ":" + r.line())) {
                    exists = true;
                    break;
                }
            }
            if (exists) draftsAt.computeIfAbsent(key, k -> new ArrayList<>()).add(draft);
            else draftsElsewhere.add(draft);
        }

        if (!outdated.isEmpty() || !draftsElsewhere.isEmpty()) {
            note(getString(R.string.diff_outdated));
            for (long id : outdated) addThread(id, threads.get(id));
            addDrafts(draftsElsewhere);
        }
        for (Row r : lines) {
            rows.add(r);
            if (!r.isLine()) continue;
            List<Long> here = placed.remove(r.side() + ":" + r.line());
            if (here != null) for (long id : here) addThread(id, threads.get(id));
            List<JsonObject> pending = draftsAt.remove(r.side() + ":" + r.line());
            if (pending != null) addDrafts(pending);
        }
        adapter.notifyDataSetChanged();
    }

    @NonNull
    private static List<JsonObject> listOf(@NonNull JsonObject first) {
        List<JsonObject> list = new ArrayList<>();
        list.add(first);
        return list;
    }

    private void addThreads(@NonNull Map<Long, List<JsonObject>> threads) {
        for (Map.Entry<Long, List<JsonObject>> entry : threads.entrySet()) addThread(entry.getKey(), entry.getValue());
    }

    private void addThread(long id, @Nullable List<JsonObject> thread) {
        if (thread == null) return;
        ReviewThreads.Thread info = this.threads.get(id);
        if (info != null && info.resolved) {
            // resolved conversations are folded, like on github.com
            Row header = new Row(RESOLVED);
            header.threadId = id;
            String toggle = getString(expanded.contains(id) ? R.string.review_thread_hide : R.string.review_thread_show);
            header.text = info.resolvedBy.isEmpty()
                    ? getString(R.string.review_thread_resolved, toggle)
                    : getString(R.string.review_thread_resolved_by, info.resolvedBy, toggle);
            rows.add(header);
            if (!expanded.contains(id)) return;
        }
        for (JsonObject c : thread) {
            Row row = new Row(COMMENT);
            row.comment = c;
            row.threadId = id;
            rows.add(row);
        }
        Row reply = new Row(REPLY);
        reply.threadId = id;
        rows.add(reply);
    }

    private void addDrafts(@NonNull List<JsonObject> drafts) {
        for (JsonObject draft : drafts) {
            Row row = new Row(COMMENT);
            row.comment = draft;
            rows.add(row);
        }
    }

    /** A pending review comment shaped like a REST review comment, so it binds the same way. */
    @NonNull
    private JsonObject draftComment(@NonNull JsonObject draft) {
        JsonObject c = draft.deepCopy();
        c.addProperty("_draft", true);
        JsonObject user = new JsonObject();
        if (AppData.INSTANCE.getLoggedUser() != null) {
            user.addProperty("login", AppData.INSTANCE.getLoggedUser().getLogin());
            user.addProperty("avatar_url", AppData.INSTANCE.getLoggedUser().getAvatarUrl());
        }
        c.add("user", user);
        long created = draft.has("created") ? draft.get("created").getAsLong() : System.currentTimeMillis();
        c.addProperty("created_at", java.time.Instant.ofEpochMilli(created).toString());
        return c;
    }

    private void updateReviewBar() {
        int pending = ReviewDrafts.count(this, owner, repo, number);
        reviewBar.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);
        reviewBarText.setText(getResources().getQuantityString(R.plurals.review_bar_text, pending, pending));
        invalidateOptionsMenu();
    }

    private void finishReview() {
        ReviewDialog.show(this, owner, repo, number, headSha, (data, hasDrafts) -> run(() -> {
            GitHubApi.call(pulls().createReview(owner, repo, number, data));
            if (hasDrafts) ReviewDrafts.clear(getApplicationContext(), owner, repo, number);
            return Boolean.TRUE;
        }, getString(R.string.review_sent)));
    }

    private void toggleResolved(long threadId) {
        ReviewThreads.Thread info = threads.get(threadId);
        if (info == null) return;
        boolean resolve = !info.resolved;
        if (resolve) expanded.remove(threadId);
        run(() -> {
            ReviewThreads.setResolved(info.id, resolve);
            return Boolean.TRUE;
        }, getString(resolve ? R.string.review_thread_resolved_toast : R.string.review_thread_unresolved_toast));
    }

    private void note(@NonNull String text) {
        Row row = new Row(NOTE);
        row.text = text;
        rows.add(row);
    }

    private static long longOf(@NonNull JsonObject object, @NonNull String key) {
        try {
            return object.get(key).getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    @NonNull
    private static List<Row> parse(@NonNull String patch) {
        List<Row> out = new ArrayList<>();
        int oldNo = 0, newNo = 0, hunk = 0;
        for (String text : patch.split("\n", -1)) {
            if (text.startsWith("@@")) {
                Matcher m = HUNK.matcher(text);
                if (m.find()) {
                    oldNo = Integer.parseInt(m.group(1));
                    newNo = Integer.parseInt(m.group(2));
                }
                hunk++;
                Row row = new Row(HUNK_ROW);
                row.text = text;
                row.hunk = hunk;
                out.add(row);
                continue;
            }
            Row row;
            if (text.startsWith("+")) {
                row = new Row(ADD);
                row.newNo = newNo++;
            } else if (text.startsWith("-")) {
                row = new Row(DEL);
                row.oldNo = oldNo++;
            } else if (text.startsWith("\\")) {
                row = new Row(META);
            } else {
                row = new Row(CTX);
                row.oldNo = oldNo++;
                row.newNo = newNo++;
            }
            row.text = text;
            row.hunk = hunk;
            out.add(row);
        }
        // a patch ends with "\n" on some files: drop the empty context line it creates
        if (!out.isEmpty()) {
            Row last = out.get(out.size() - 1);
            if (last.type == CTX && last.text.isEmpty()) out.remove(out.size() - 1);
        }
        return out;
    }

    // ---------------------------------------------------------------------------------
    // Commenting
    // ---------------------------------------------------------------------------------

    private void onLineClick(int index) {
        Row row = rows.get(index);
        if (rangeStart >= 0) {
            int start = rangeStart;
            rangeStart = -1;
            adapter.notifyItemChanged(start);
            if (start == index) return;
            Row first = rows.get(start);
            if (first.hunk != row.hunk || index < start) {
                showWarningToast(getString(R.string.diff_range_other_hunk));
                return;
            }
            compose(start, index);
            return;
        }
        compose(index, index);
    }

    private void onLineLongClick(int index) {
        int previous = rangeStart;
        rangeStart = index;
        if (previous >= 0) adapter.notifyItemChanged(previous);
        adapter.notifyItemChanged(index);
        showInfoToast(getString(R.string.diff_range_started));
    }

    /** A comment on the lines from row {@code from} to row {@code to} (the same row for one line). */
    private void compose(int from, int to) {
        Row first = rows.get(from);
        Row last = rows.get(to);
        StringBuilder code = new StringBuilder();
        StringBuilder suggestion = new StringBuilder();
        boolean allNew = true;
        int shown = 0;
        for (int i = from; i <= to; i++) {
            Row r = rows.get(i);
            if (!r.isLine()) continue;
            if (shown++ < 6) code.append(code.length() > 0 ? "\n" : "").append(r.text);
            if (r.type == DEL) allNew = false;
            else suggestion.append(r.text.length() > 0 ? r.text.substring(1) : "").append('\n');
        }
        String title = from == to ? getString(R.string.diff_comment_line, last.line())
                : getString(R.string.diff_comment_lines, first.line(), last.line());
        boolean reviewing = ReviewDrafts.count(this, owner, repo, number) > 0;
        CharSequence[] choices = {getString(R.string.review_single),
                getString(reviewing ? R.string.review_add : R.string.review_start)};
        ComposeDialog.Builder dialog = new ComposeDialog.Builder(this)
                .title(title)
                .repo(owner + "/" + repo)
                .header(code)
                .choices(choices, reviewing ? 1 : 0)
                .body(getString(R.string.diff_comment_hint), null)
                .bodyRequired(true, null)
                .positive(getString(R.string.pull_comment))
                .callback((choice, unusedTitle, body) -> {
                    if (choice == 1) {
                        ReviewDrafts.add(this, owner, repo, number, path, last.line(), last.side(),
                                from != to ? first.line() : 0, from != to ? first.side() : null, body);
                        int pending = ReviewDrafts.count(this, owner, repo, number);
                        showSuccessToast(getResources().getQuantityString(R.plurals.review_added, pending, pending));
                        PullChanges.markChanged();
                        build(lastComments);
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("body", body);
                    data.put("commit_id", headSha);
                    data.put("path", path);
                    data.put("line", last.line());
                    data.put("side", last.side());
                    if (from != to) {
                        data.put("start_line", first.line());
                        data.put("start_side", first.side());
                    }
                    run(() -> GitHubApi.call(pulls().createReviewComment(owner, repo, number, data)),
                            getString(R.string.diff_comment_sent));
                });
        if (allNew) {
            dialog.insertAction(getString(R.string.diff_suggest), "```suggestion\n" + suggestion + "```\n");
        }
        dialog.show();
    }

    private void reply(long threadId) {
        new ComposeDialog.Builder(this)
                .title(getString(R.string.diff_reply_title))
                .repo(owner + "/" + repo)
                .body(getString(R.string.diff_comment_hint), null)
                .bodyRequired(true, null)
                .positive(getString(R.string.pull_comment))
                .callback((choice, unusedTitle, body) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("body", body);
                    run(() -> GitHubApi.call(pulls().replyReviewComment(owner, repo, number, threadId, data)),
                            getString(R.string.diff_comment_sent));
                })
                .show();
    }

    private boolean isMine(@NonNull JsonObject comment) {
        return AppData.INSTANCE.getLoggedUser() != null
                && AppData.INSTANCE.getLoggedUser().getLogin().equalsIgnoreCase(Json.str(comment, "user.login"));
    }

    private void draftMenu(@NonNull View anchor, @NonNull JsonObject draft) {
        long id = longOf(draft, "id");
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        menu.getMenu().add(0, 1, 0, R.string.comment_edit);
        menu.getMenu().add(0, 2, 1, R.string.comment_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                new ComposeDialog.Builder(this)
                        .title(getString(R.string.comment_edit))
                        .repo(owner + "/" + repo)
                        .body(getString(R.string.diff_comment_hint), Json.str(draft, "body"))
                        .bodyRequired(true, null)
                        .positive(getString(R.string.save))
                        .callback((choice, unusedTitle, body) -> {
                            ReviewDrafts.update(this, owner, repo, number, id, body);
                            build(lastComments);
                        })
                        .show();
            } else {
                ReviewDrafts.remove(this, owner, repo, number, id);
                PullChanges.markChanged();
                build(lastComments);
            }
            return true;
        });
        menu.show();
    }

    private void commentMenu(@NonNull View anchor, @NonNull JsonObject comment) {
        if (Json.bool(comment, "_draft")) {
            draftMenu(anchor, comment);
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (isMine(comment)) {
            menu.getMenu().add(0, 1, 0, R.string.comment_edit);
            menu.getMenu().add(0, 2, 1, R.string.comment_delete);
        }
        menu.getMenu().add(0, 3, 2, R.string.copy_url);
        menu.setOnMenuItemClickListener(item -> {
            long id = longOf(comment, "id");
            if (item.getItemId() == 1) {
                new ComposeDialog.Builder(this)
                        .title(getString(R.string.comment_edit))
                        .repo(owner + "/" + repo)
                        .body(getString(R.string.diff_comment_hint), Json.str(comment, "body"))
                        .bodyRequired(true, null)
                        .positive(getString(R.string.save))
                        .callback((choice, unusedTitle, body) -> {
                            Map<String, Object> data = new HashMap<>();
                            data.put("body", body);
                            run(() -> GitHubApi.call(pulls().updateReviewComment(owner, repo, id, data)), null);
                        })
                        .show();
            } else if (item.getItemId() == 2) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.comment_delete)
                        .setMessage(R.string.comment_delete_message)
                        .setPositiveButton(R.string.delete, (d, w) -> run(() -> {
                            GitHubApi.call(pulls().deleteReviewComment(owner, repo, id));
                            return Boolean.TRUE;
                        }, getString(R.string.comment_deleted)))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                AppUtils.copyToClipboard(this, Json.str(comment, "html_url"));
            }
            return true;
        });
        menu.show();
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.refresh);
        menu.add(0, 2, 1, R.string.copy_path);
        menu.add(0, 3, 2, R.string.open_in_browser);
        menu.add(0, 4, 3, R.string.review_discard);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem discard = menu.findItem(4);
        if (discard != null) discard.setVisible(ReviewDrafts.count(this, owner, repo, number) > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            load();
            return true;
        } else if (item.getItemId() == 2) {
            AppUtils.copyToClipboard(this, path);
            return true;
        } else if (item.getItemId() == 3) {
            String url = file != null && !Json.str(file, "blob_url").isEmpty() ? Json.str(file, "blob_url")
                    : "https://github.com/" + owner + "/" + repo + "/pull/" + number + "/files";
            AppOpener.openInCustomTabsOrBrowser(this, url);
            return true;
        } else if (item.getItemId() == 4) {
            ReviewDialog.confirmDiscard(this, owner, repo, number, () -> {
                PullChanges.markChanged();
                showInfoToast(getString(R.string.review_discarded));
                build(lastComments);
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private <T> void run(@NonNull Callable<T> work, @Nullable String successMessage) {
        showProgressDialog(getString(R.string.please_wait));
        background(work, done -> {
            dismissProgressDialog();
            PullChanges.markChanged();
            if (successMessage != null) showSuccessToast(successMessage);
            load();
        }, error -> dismissProgressDialog());
    }

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
        list = root.findViewById(R.id.diff_list);
        loader = root.findViewById(R.id.loader);
        reviewBar = root.findViewById(R.id.review_bar);
        reviewBarText = root.findViewById(R.id.review_bar_text);
        root.findViewById(R.id.review_bar_bn).setOnClickListener(v -> finishReview());
    }

    // ---------------------------------------------------------------------------------
    // Adapter
    // ---------------------------------------------------------------------------------

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View view) {
            super(view);
        }
    }

    private final class DiffAdapter extends RecyclerView.Adapter<Holder> {

        @Override
        public int getItemViewType(int position) {
            int type = rows.get(position).type;
            if (type == COMMENT) return 1;
            if (type == NOTE || type == RESOLVED) return 2;
            if (type == REPLY) return 3;
            return 0;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 1) {
                View view = inflater.inflate(R.layout.layout_item_pr_comment, parent, false);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                params.topMargin = dp(4);
                params.bottomMargin = 0;
                params.leftMargin = dp(12);
                params.rightMargin = dp(12);
                return new Holder(view);
            }
            if (viewType == 3) {
                // Reply · Resolve conversation
                LinearLayout bar = new LinearLayout(parent.getContext());
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                for (int i = 0; i < 2; i++) {
                    TextView action = new TextView(parent.getContext());
                    action.setTextColor(ViewUtils.getAccentColor(PullDiffActivity.this));
                    action.setPadding(dp(16), dp(8), dp(16), dp(12));
                    action.setTextSize(14);
                    action.setBackgroundResource(selectable());
                    bar.addView(action);
                }
                return new Holder(bar);
            }
            if (viewType == 2) {
                TextView text = new TextView(parent.getContext());
                text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return new Holder(text);
            }
            return new Holder(inflater.inflate(R.layout.layout_item_diff_line, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = rows.get(position);
            View view = holder.itemView;
            if (row.type == COMMENT) {
                bindComment(view, row);
                return;
            }
            if (row.type == REPLY) {
                LinearLayout bar = (LinearLayout) view;
                TextView replyBn = (TextView) bar.getChildAt(0);
                TextView resolveBn = (TextView) bar.getChildAt(1);
                replyBn.setText(R.string.diff_reply);
                replyBn.setOnClickListener(v -> reply(row.threadId));
                ReviewThreads.Thread info = threads.get(row.threadId);
                boolean can = info != null && (info.resolved ? info.canUnresolve : info.canResolve);
                resolveBn.setVisibility(can ? View.VISIBLE : View.GONE);
                if (can) {
                    resolveBn.setText(info.resolved ? R.string.review_thread_unresolve : R.string.review_thread_resolve);
                    resolveBn.setOnClickListener(v -> toggleResolved(row.threadId));
                }
                return;
            }
            if (row.type == NOTE || row.type == RESOLVED) {
                TextView text = (TextView) view;
                if (row.type == RESOLVED) {
                    text.setText(row.text);
                    text.setTextColor(secondaryText());
                    text.setPadding(dp(16), dp(8), dp(16), dp(8));
                    text.setTextSize(13);
                    text.setBackgroundResource(selectable());
                    text.setOnClickListener(v -> {
                        if (!expanded.remove(row.threadId)) expanded.add(row.threadId);
                        build(lastComments);
                    });
                } else {
                    text.setText(row.text);
                    text.setTextColor(secondaryText());
                    text.setPadding(dp(16), dp(10), dp(16), dp(10));
                    text.setTextSize(13);
                    text.setBackground(null);
                    text.setOnClickListener(null);
                }
                return;
            }

            TextView oldNo = view.findViewById(R.id.diff_old_no);
            TextView newNo = view.findViewById(R.id.diff_new_no);
            TextView code = view.findViewById(R.id.diff_code);
            code.setText(row.text);
            oldNo.setText(row.oldNo > 0 && row.type != ADD ? String.valueOf(row.oldNo) : "");
            newNo.setText(row.newNo > 0 && row.type != DEL ? String.valueOf(row.newNo) : "");
            int background;
            switch (row.type) {
                case ADD:
                    background = R.color.diff_add_bg;
                    break;
                case DEL:
                    background = R.color.diff_del_bg;
                    break;
                case HUNK_ROW:
                    background = R.color.diff_hunk_bg;
                    break;
                default:
                    background = 0;
            }
            if (position == rangeStart) background = R.color.diff_selected_bg;
            if (background != 0) view.setBackgroundResource(background);
            else view.setBackgroundResource(selectable());
            if (row.isLine()) {
                view.setOnClickListener(v -> {
                    int at = holder.getBindingAdapterPosition();
                    if (at != RecyclerView.NO_POSITION) onLineClick(at);
                });
                view.setOnLongClickListener(v -> {
                    int at = holder.getBindingAdapterPosition();
                    if (at != RecyclerView.NO_POSITION) onLineLongClick(at);
                    return true;
                });
            } else {
                view.setOnClickListener(null);
                view.setOnLongClickListener(null);
                view.setClickable(false);
            }
        }

        private void bindComment(@NonNull View view, @NonNull Row row) {
            JsonObject c = row.comment;
            if (c == null) return;
            GlideApp.with(PullDiffActivity.this).load(Json.str(c, "user.avatar_url"))
                    .into((ImageView) view.findViewById(R.id.comment_avatar));
            Date date = Json.date(c, "created_at");
            String when = date == null ? "" : StringUtils.getNewsTimeStr(PullDiffActivity.this, date);
            ((TextView) view.findViewById(R.id.comment_header)).setText(Json.str(c, "user.login") + " · " + when);
            TextView badge = view.findViewById(R.id.comment_badge);
            if (Json.bool(c, "_draft")) {
                badge.setText(R.string.review_pending_badge);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
            TextView body = view.findViewById(R.id.comment_body);
            markwon.setMarkdown(body, Json.str(c, "body"));
            body.setOnLongClickListener(v -> {
                commentMenu(v, c);
                return true;
            });
            view.setOnLongClickListener(v -> {
                commentMenu(v, c);
                return true;
            });
        }
    }

    private int selectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
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
}
