package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GistService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.TextInputDialog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GistChanges;
import com.thirtydegreesray.openhub.util.GitHubHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * One gist: its files (tap to view, arrow to download). The owner adds, edits, renames and
 * deletes files, edits the description and deletes the gist; anyone can star and fork it.
 */
public class GistActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String gistId) {
        return new Intent(context, GistActivity.class)
                .putExtras(BundleHelper.builder().put("gistId", gistId).build());
    }

    /** Bigger files are opened in the browser (intents cannot carry them) and not edited. */
    static final int MAX_INLINE_CHARS = 200_000;

    @AutoAccess String gistId;

    private SwipeRefreshLayout refreshLayout;
    private boolean refreshing;
    private TextView descriptionText;
    private TextView metaText;
    private TextView filesTitle;
    private LinearLayout filesList;
    private FloatingActionButton addFileBn;
    private TextView commentsTitle;
    private LinearLayout commentsList;
    private View addCommentBn;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private JsonObject gist;
    @Nullable private Boolean starred;
    /** null while not loaded (or not loadable) */
    @Nullable private List<JsonObject> comments;
    private long seenStamp;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_gist;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.gists));
        refreshLayout.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(this));
        refreshLayout.setOnRefreshListener(this::load);
        addFileBn.setOnClickListener(v -> {
            if (gist != null) startActivity(GistEditActivity.addFileIntent(this, gistId, fileNames()));
        });
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // a file was added or edited on the editor screen
        if (seenStamp != 0 && seenStamp != GistChanges.stamp()) load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private GistService service() {
        return GitHubApi.service(GistService.class);
    }

    private boolean isMine() {
        String owner = gist == null ? null : Json.str(gist, "owner.login");
        return owner != null && AppData.INSTANCE.getLoggedUser() != null
                && owner.equalsIgnoreCase(AppData.INSTANCE.getLoggedUser().getLogin());
    }

    // ---------------------------------------------------------------------------------
    // Load and show
    // ---------------------------------------------------------------------------------

    /** Posted: a SwipeRefreshLayout told before its first layout keeps a stuck circle. */
    private void showRefreshing(boolean on) {
        refreshing = on;
        refreshLayout.post(() -> refreshLayout.setRefreshing(refreshing));
    }

    private void load() {
        showRefreshing(true);
        seenStamp = GistChanges.stamp();
        background(() -> {
            JsonObject json = GitHubApi.call(service().getGist(gistId));
            Boolean star = null;
            try {
                retrofit2.Response<okhttp3.ResponseBody> response = service().isStarred(gistId).blockingFirst();
                star = response.code() == 204 ? Boolean.TRUE : response.code() == 404 ? Boolean.FALSE : null;
                if (response.body() != null) response.body().close();
            } catch (Exception ignored) {
            }
            return new Object[]{json, star, fetchComments()};
        }, (Object[] result) -> {
            showRefreshing(false);
            gist = (JsonObject) result[0];
            starred = (Boolean) result[1];
            @SuppressWarnings("unchecked") List<JsonObject> loaded = (List<JsonObject>) result[2];
            comments = loaded;
            render();
            invalidateOptionsMenu();
        }, error -> showRefreshing(false));
    }

    /** All comments (up to 300), or null when GitHub would not give them. */
    @Nullable
    private List<JsonObject> fetchComments() {
        try {
            List<JsonObject> list = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                com.google.gson.JsonArray array = GitHubApi.call(service().getComments(gistId, 100, page));
                if (array == null || array.size() == 0) break;
                for (JsonElement element : array) {
                    if (element.isJsonObject()) list.add(element.getAsJsonObject());
                }
                if (array.size() < 100) break;
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private List<JsonObject> files() {
        List<JsonObject> list = new ArrayList<>();
        JsonElement files = Json.get(gist, "files");
        if (files != null && files.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : files.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) list.add(entry.getValue().getAsJsonObject());
            }
        }
        return list;
    }

    @NonNull
    private ArrayList<String> fileNames() {
        ArrayList<String> names = new ArrayList<>();
        for (JsonObject file : files()) names.add(Json.str(file, "filename"));
        return names;
    }

    private void render() {
        if (gist == null) return;
        String description = Json.str(gist, "description");
        List<JsonObject> files = files();
        descriptionText.setText(StringUtils.isBlank(description)
                ? (files.isEmpty() ? gistId : Json.str(files.get(0), "filename")) : description.trim());
        List<String> meta = new ArrayList<>();
        meta.add(getString(Json.bool(gist, "public") ? R.string.gist_public_badge : R.string.gist_secret));
        String owner = Json.str(gist, "owner.login");
        if (!StringUtils.isBlank(owner)) meta.add(getString(R.string.gist_by, owner));
        Date updated = Json.date(gist, "updated_at");
        if (updated != null) meta.add(getString(R.string.gist_updated, StringUtils.getNewsTimeStr(this, updated)));
        int comments = Json.integer(gist, "comments");
        if (comments > 0) meta.add(getResources().getQuantityString(R.plurals.gist_comments, comments, comments));
        metaText.setText(String.join(" · ", meta));
        setToolbarTitle(getString(R.string.gists), owner);

        filesTitle.setText(getResources().getQuantityString(R.plurals.gist_files, files.size(), files.size()));
        filesList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean mine = isMine();
        for (JsonObject file : files) {
            View row = inflater.inflate(R.layout.layout_item_artifact, filesList, false);
            String name = Json.str(file, "filename");
            ((TextView) row.findViewById(R.id.artifact_name)).setText(name);
            String info = StringUtils.getSizeString(Json.get(file, "size") == null ? 0
                    : Json.get(file, "size").getAsLong());
            String language = Json.str(file, "language");
            if (!StringUtils.isBlank(language)) info = language + " · " + info;
            ((TextView) row.findViewById(R.id.artifact_size)).setText(info);
            ImageView download = row.findViewById(R.id.artifact_download);
            download.setOnClickListener(v -> AppOpener.startDownload(this, Json.str(file, "raw_url"), name));
            row.setBackgroundResource(selectable());
            row.setOnClickListener(v -> openFile(file));
            row.setOnLongClickListener(v -> {
                showFileMenu(v, file, mine);
                return true;
            });
            filesList.addView(row);
        }
        if (mine) addFileBn.show();
        else addFileBn.hide();
        renderComments();
    }

    // ---------------------------------------------------------------------------------
    // Comments
    // ---------------------------------------------------------------------------------

    private void renderComments() {
        commentsList.removeAllViews();
        if (comments == null) {
            commentsTitle.setVisibility(View.GONE);
            addCommentBn.setVisibility(View.GONE);
            return;
        }
        commentsTitle.setVisibility(View.VISIBLE);
        addCommentBn.setVisibility(View.VISIBLE);
        commentsTitle.setText(comments.isEmpty() ? getString(R.string.gist_no_comments)
                : getResources().getQuantityString(R.plurals.gist_comments, comments.size(), comments.size()));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (JsonObject comment : comments) {
            View row = inflater.inflate(R.layout.layout_item_gist_comment, commentsList, false);
            String login = Json.str(comment, "user.login");
            Date created = Json.date(comment, "created_at");
            Date updated = Json.date(comment, "updated_at");
            StringBuilder header = new StringBuilder(login);
            if (created != null) header.append(" · ").append(StringUtils.getNewsTimeStr(this, created));
            if (created != null && updated != null && updated.getTime() - created.getTime() > 60_000) {
                header.append(" · ").append(getString(R.string.gist_comment_edited));
            }
            ((TextView) row.findViewById(R.id.comment_header)).setText(header);
            TextView body = row.findViewById(R.id.comment_body);
            String html = Json.str(comment, "body_html");
            if (StringUtils.isBlank(html)) {
                body.setText(Json.str(comment, "body"));
            } else {
                body.setText(linksInApp(android.text.Html.fromHtml(html,
                        android.text.Html.FROM_HTML_MODE_COMPACT)));
                body.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            }
            View.OnLongClickListener menu = v -> {
                showCommentMenu(row, comment);
                return true;
            };
            row.setOnClickListener(v -> showCommentMenu(row, comment));
            row.setOnLongClickListener(menu);
            body.setOnLongClickListener(menu);
            commentsList.addView(row);
        }
    }

    /** Links in a comment open like links anywhere in the app (GitHub pages in OpenHub). */
    @NonNull
    private CharSequence linksInApp(@NonNull CharSequence text) {
        android.text.SpannableStringBuilder spannable = new android.text.SpannableStringBuilder(text);
        for (android.text.style.URLSpan span : spannable.getSpans(0, spannable.length(),
                android.text.style.URLSpan.class)) {
            int start = spannable.getSpanStart(span);
            int end = spannable.getSpanEnd(span);
            String url = span.getURL();
            spannable.removeSpan(span);
            spannable.setSpan(new android.text.style.ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    AppOpener.launchUrl(GistActivity.this, android.net.Uri.parse(url));
                }
            }, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // trailing new lines of the last paragraph
        int length = spannable.length();
        while (length > 0 && spannable.charAt(length - 1) == '\n') length--;
        return spannable.subSequence(0, length);
    }

    private void showCommentMenu(@NonNull View anchor, @NonNull JsonObject comment) {
        String me = AppData.INSTANCE.getLoggedUser() == null ? "" : AppData.INSTANCE.getLoggedUser().getLogin();
        boolean own = Json.str(comment, "user.login").equalsIgnoreCase(me);
        long id = Json.get(comment, "id") == null ? 0 : Json.get(comment, "id").getAsLong();
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.gist_comment_copy);
        if (own) popup.getMenu().add(0, 2, 1, R.string.gist_comment_edit);
        // the gist's owner may remove anyone's comment
        if (own || isMine()) popup.getMenu().add(0, 3, 2, R.string.gist_comment_delete);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                AppUtils.copyToClipboard(this, Json.str(comment, "body"));
            } else if (item.getItemId() == 2) {
                writeComment(id, Json.str(comment, "body"));
            } else {
                deleteComment(id);
            }
            return true;
        });
        popup.show();
    }

    /** @param id 0 for a new comment */
    private void writeComment(long id, @Nullable String text) {
        new com.thirtydegreesray.openhub.ui.widget.ComposeDialog.Builder(this)
                .title(getString(id == 0 ? R.string.gist_add_comment : R.string.gist_comment_edit))
                .body(getString(R.string.gist_comment_hint), text)
                .bodyRequired(true, null)
                .positive(getString(id == 0 ? R.string.gist_comment_post : R.string.save))
                .callback((choice, title, body) -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("body", body);
                    showProgressDialog(getString(R.string.please_wait));
                    background(() -> id == 0
                            ? GitHubApi.call(service().createComment(gistId, json(json)))
                            : GitHubApi.call(service().updateComment(gistId, id, json(json))), done -> {
                        dismissProgressDialog();
                        showSuccessToast(getString(id == 0 ? R.string.gist_comment_posted : R.string.gist_comment_saved));
                        load();
                    }, error -> dismissProgressDialog());
                })
                .show();
    }

    private void deleteComment(long id) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.gist_comment_delete)
                .setMessage(R.string.gist_comment_delete_message)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    showProgressDialog(getString(R.string.please_wait));
                    background(() -> {
                        GitHubApi.call(service().deleteComment(gistId, id));
                        return true;
                    }, done -> {
                        dismissProgressDialog();
                        showSuccessToast(getString(R.string.gist_comment_deleted));
                        load();
                    }, error -> dismissProgressDialog());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int selectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private static boolean editable(@NonNull JsonObject file) {
        String content = Json.str(file, "content");
        return content != null && !Json.bool(file, "truncated") && content.length() <= MAX_INLINE_CHARS;
    }

    private void openFile(@NonNull JsonObject file) {
        String name = Json.str(file, "filename");
        String content = Json.str(file, "content");
        if (!editable(file)) {
            AppOpener.openInCustomTabsOrBrowser(this, Json.str(file, "raw_url"));
        } else if (GitHubHelper.isMarkdown(name)) {
            ViewerActivity.showMdSource(this, name, MarkdownHtml.render(content));
        } else {
            ViewerActivity.showCode(this, name, content);
        }
    }

    private void showFileMenu(@NonNull View anchor, @NonNull JsonObject file, boolean mine) {
        String name = Json.str(file, "filename");
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        if (mine) {
            popup.getMenu().add(0, 1, 0, R.string.gist_edit_file);
            popup.getMenu().add(0, 2, 1, R.string.rename);
            popup.getMenu().add(0, 3, 2, R.string.gist_file_delete);
        }
        popup.getMenu().add(0, 4, 3, R.string.download);
        popup.getMenu().add(0, 5, 4, R.string.copy_url);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (!editable(file)) {
                        showWarningToast(getString(R.string.gist_file_too_big));
                    } else {
                        startActivity(GistEditActivity.editFileIntent(this, gistId, name, fileNames()));
                    }
                    break;
                case 2:
                    renameFile(name);
                    break;
                case 3:
                    removeFile(name);
                    break;
                case 4:
                    AppOpener.startDownload(this, Json.str(file, "raw_url"), name);
                    break;
                case 5:
                    AppUtils.copyToClipboard(this, Json.str(file, "raw_url"));
                    break;
            }
            return true;
        });
        popup.show();
    }

    // ---------------------------------------------------------------------------------
    // Changes
    // ---------------------------------------------------------------------------------

    private void renameFile(@NonNull String name) {
        TextInputDialog.show(this, R.string.rename, R.string.gist_filename, name, true, text -> {
            String newName = text.trim();
            if (newName.equals(name)) return;
            if (newName.contains("/")) {
                showErrorToast(getString(R.string.gist_name_invalid));
                return;
            }
            if (fileNames().contains(newName)) {
                showErrorToast(getString(R.string.gist_name_exists, newName));
                return;
            }
            JsonObject change = new JsonObject();
            change.addProperty("filename", newName);
            JsonObject files = new JsonObject();
            files.add(name, change);
            JsonObject body = new JsonObject();
            body.add("files", files);
            update(body, R.string.gist_saved);
        });
    }

    private void removeFile(@NonNull String name) {
        if (files().size() <= 1) {
            showWarningToast(getString(R.string.gist_last_file));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.gist_file_delete)
                .setMessage(getString(R.string.gist_file_delete_message, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    JsonObject files = new JsonObject();
                    files.add(name, JsonNull.INSTANCE);
                    JsonObject body = new JsonObject();
                    body.add("files", files);
                    update(body, R.string.gist_saved);
                })
                .show();
    }

    private void editDescription() {
        String current = gist == null ? "" : Json.str(gist, "description");
        TextInputDialog.show(this, R.string.gist_edit_description, R.string.gist_description,
                current == null ? "" : current, false, true, text -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("description", text.trim());
                    update(body, R.string.gist_saved);
                });
    }

    private void update(@NonNull JsonObject body, int doneText) {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GitHubApi.call(service().updateGist(gistId, json(body))), json -> {
            dismissProgressDialog();
            showSuccessToast(getString(doneText));
            GistChanges.markChanged();
            gist = json;
            seenStamp = GistChanges.stamp();
            render();
        }, error -> dismissProgressDialog());
    }

    /** JSON with nulls kept ("file": null deletes it). */
    @NonNull
    static RequestBody json(@NonNull JsonObject body) {
        return RequestBody.create(new GsonBuilder().serializeNulls().create().toJson(body),
                MediaType.parse("application/json; charset=utf-8"));
    }

    private void toggleStar() {
        boolean star = !Boolean.TRUE.equals(starred);
        background(() -> {
            GitHubApi.call(star ? service().star(gistId) : service().unstar(gistId));
            return Boolean.TRUE;
        }, done -> {
            starred = star;
            GistChanges.markChanged();
            seenStamp = GistChanges.stamp();
            showSuccessToast(getString(star ? R.string.gist_starred_done : R.string.gist_unstarred_done));
            invalidateOptionsMenu();
        }, null);
    }

    private void fork() {
        showProgressDialog(getString(R.string.please_wait));
        background(() -> GitHubApi.call(service().fork(gistId)), json -> {
            dismissProgressDialog();
            GistChanges.markChanged();
            showSuccessToast(getString(R.string.gist_forked));
            String id = Json.str(json, "id");
            if (!StringUtils.isBlank(id)) startActivity(createIntent(this, id));
        }, error -> dismissProgressDialog());
    }

    private void deleteGist() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.gist_delete)
                .setMessage(R.string.gist_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    showProgressDialog(getString(R.string.please_wait));
                    background(() -> {
                        GitHubApi.call(service().deleteGist(gistId));
                        return Boolean.TRUE;
                    }, done -> {
                        dismissProgressDialog();
                        GistChanges.markChanged();
                        showSuccessToast(getString(R.string.gist_deleted));
                        finish();
                    }, error -> dismissProgressDialog());
                })
                .show();
    }

    // ---------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.gist_star);
        menu.add(0, 2, 1, R.string.gist_edit_description);
        menu.add(0, 3, 2, R.string.gist_fork);
        menu.add(0, 4, 3, R.string.share);
        menu.add(0, 5, 4, R.string.copy_url);
        menu.add(0, 6, 5, R.string.open_in_browser);
        menu.add(0, 7, 6, R.string.gist_delete);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean loaded = gist != null;
        boolean mine = isMine();
        MenuItem star = menu.findItem(1);
        star.setVisible(loaded && starred != null);
        star.setTitle(Boolean.TRUE.equals(starred) ? R.string.gist_unstar : R.string.gist_star);
        menu.findItem(2).setVisible(loaded && mine);
        menu.findItem(3).setVisible(loaded && !mine);
        menu.findItem(7).setVisible(loaded && mine);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String url = gist == null ? "https://gist.github.com/" + gistId : Json.str(gist, "html_url");
        switch (item.getItemId()) {
            case 1:
                toggleStar();
                return true;
            case 2:
                editDescription();
                return true;
            case 3:
                fork();
                return true;
            case 4:
                AppOpener.shareText(this, url);
                return true;
            case 5:
                AppUtils.copyToClipboard(this, url);
                return true;
            case 6:
                AppOpener.openInCustomTabsOrBrowser(this, url);
                return true;
            case 7:
                deleteGist();
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
                    String message = StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
                    if (error instanceof GitHubApi.ApiException && ((GitHubApi.ApiException) error).code == 404
                            && gist != null) {
                        message = getString(R.string.gist_scope_missing) + "\n(" + message + ")";
                    }
                    showErrorToast(message);
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        refreshLayout = root.findViewById(R.id.refresh_layout);
        descriptionText = root.findViewById(R.id.gist_description);
        metaText = root.findViewById(R.id.gist_meta);
        filesTitle = root.findViewById(R.id.gist_files_title);
        filesList = root.findViewById(R.id.gist_files);
        addFileBn = root.findViewById(R.id.add_file_bn);
        commentsTitle = root.findViewById(R.id.gist_comments_title);
        commentsList = root.findViewById(R.id.gist_comments);
        addCommentBn = root.findViewById(R.id.gist_add_comment);
        addCommentBn.setOnClickListener(v -> writeComment(0, null));
    }
}
