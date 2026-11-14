package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.ReleaseService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ErrorTexts;
import com.thirtydegreesray.openhub.util.GraphQl;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.ReleaseChanges;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.TagRows;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Every tag of a repository, like github.com/owner/repo/tags: newest commit first, with
 * the commit, the date, whether it is annotated and the release made from it. A tap
 * opens the release (or makes one), the files, the commit, a comparison with the tag
 * below, the source code downloads; people with write access delete tags.
 * Everything shown is GitHub's answer; after a delete the list is read again.
 */
public class TagsActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, TagsActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
    }

    private static final long FILTER_DELAY_MS = 400;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_BROWSER = 2;

    @AutoAccess String owner;
    @AutoAccess String repo;

    private EditText filterEdit;
    private LinearLayout list;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable filterRunnable = this::reload;

    /** names of the rows shown, newest first (for "Compare with the tag below") */
    private final List<String> names = new ArrayList<>();
    private boolean canWrite;
    @Nullable private String cursor;
    /** the filter the shown rows belong to */
    @NonNull private String shownQuery = "";
    private long seenReleaseStamp;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_tags;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.tags_title), owner + "/" + repo);
        PullRefresh.wrap(findViewById(R.id.tags_scroll), this::reload).follow(loader);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // a release made, edited or deleted elsewhere changes the release marks
        if (ReleaseChanges.changedSince(seenReleaseStamp)) reload();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(filterRunnable);
        disposables.clear();
        super.onDestroy();
    }

    @NonNull
    private String query() {
        return filterEdit.getText().toString().trim();
    }

    private void reload() {
        handler.removeCallbacks(filterRunnable);
        disposables.clear();
        cursor = null;
        names.clear();
        list.removeAllViews();
        loadMoreBn.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        loadPage(true);
    }

    // ---------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------

    /** One page of tags, then the release of each tag of that page. */
    private static final class Page {
        boolean canWrite;
        int total;
        boolean hasNext;
        @Nullable String endCursor;
        final List<TagRows.Entry> entries = new ArrayList<>();
        final List<JsonObject> releases = new ArrayList<>();
    }

    private void loadPage(boolean first) {
        loader.setVisibility(View.VISIBLE);
        loadMoreBn.setEnabled(false);
        final String q = query();
        final String after = first ? null : cursor;
        final long stamp = ReleaseChanges.stamp();
        background(() -> {
            Map<String, Object> vars = GraphQl.vars("owner", owner, "repo", repo);
            if (after != null) vars.put("after", after);
            if (!q.isEmpty()) vars.put("q", q);
            JsonObject data = GraphQl.run(TagRows.TAGS_QUERY, vars);
            JsonObject repository = Json.obj(data, "repository");
            if (repository == null) throw new GitHubApi.ApiException(404, getString(R.string.tags_repo_not_found));
            Page page = new Page();
            page.canWrite = TagRows.canWrite(Json.str(repository, "viewerPermission"));
            page.total = Json.integer(repository, "refs.totalCount");
            page.hasNext = Json.bool(repository, "refs.pageInfo.hasNextPage");
            page.endCursor = Json.str(repository, "refs.pageInfo.endCursor");
            JsonElement nodes = Json.get(repository, "refs.nodes");
            if (nodes != null && nodes.isJsonArray()) {
                for (JsonElement node : nodes.getAsJsonArray()) {
                    TagRows.Entry entry = node.isJsonObject() ? TagRows.parse(node.getAsJsonObject()) : null;
                    if (entry != null) page.entries.add(entry);
                }
            }
            if (!page.entries.isEmpty()) {
                Map<String, Object> releaseVars = GraphQl.vars("owner", owner, "repo", repo);
                for (int i = 0; i < page.entries.size(); i++) releaseVars.put("t" + i, page.entries.get(i).name);
                JsonObject releaseData = GraphQl.run(TagRows.releasesQuery(page.entries.size()), releaseVars);
                JsonObject releaseRepo = Json.obj(releaseData, "repository");
                for (int i = 0; i < page.entries.size(); i++) page.releases.add(TagRows.release(releaseRepo, i));
            }
            return page;
        }, (Page page) -> {
            loader.setVisibility(View.GONE);
            loadMoreBn.setEnabled(true);
            seenReleaseStamp = stamp;
            canWrite = page.canWrite;
            shownQuery = q;
            if (first) {
                list.removeAllViews();
                names.clear();
                if (page.total == 0 && q.isEmpty()) {
                    emptyText.setText(R.string.tags_none);
                    emptyText.setVisibility(View.VISIBLE);
                } else if (page.total == 0) {
                    list.addView(note(getString(R.string.tags_none_match, q)));
                } else {
                    list.addView(note(q.isEmpty()
                            ? getResources().getQuantityString(R.plurals.tags_count, page.total, page.total)
                            : getResources().getQuantityString(R.plurals.tags_count_match, page.total, page.total, q)));
                }
            }
            for (int i = 0; i < page.entries.size(); i++) {
                TagRows.Entry entry = page.entries.get(i);
                names.add(entry.name);
                list.addView(tagRow(entry, page.releases.get(i)));
            }
            cursor = page.endCursor;
            loadMoreBn.setVisibility(page.hasNext && !StringUtils.isBlank(cursor) ? View.VISIBLE : View.GONE);
        });
    }

    // ---------------------------------------------------------------------------------
    // Rows
    // ---------------------------------------------------------------------------------

    @NonNull
    private View tagRow(@NonNull TagRows.Entry entry, @Nullable JsonObject release) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_actions_entry, list, false);
        ((TextView) row.findViewById(R.id.entry_title)).setText(entry.name);

        TextView detail = row.findViewById(R.id.entry_detail);
        detail.setTypeface(Typeface.DEFAULT);
        SpannableStringBuilder commit = new SpannableStringBuilder();
        if (entry.commitOid.isEmpty()) {
            commit.append(getString(R.string.tags_no_commit));
        } else {
            commit.append(entry.shortOid);
            commit.setSpan(new TypefaceSpan("monospace"), 0, commit.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!entry.headline.isEmpty()) commit.append("  ").append(entry.headline);
        }
        detail.setText(commit);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);

        SpannableStringBuilder state = new SpannableStringBuilder();
        TagRows.Kind kind = TagRows.kind(release);
        if (kind != TagRows.Kind.NONE) {
            state.append(releaseLabel(kind));
            state.setSpan(new ForegroundColorSpan(releaseColor(kind)), 0, state.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            state.setSpan(new StyleSpan(Typeface.BOLD), 0, state.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (entry.annotated) {
            if (state.length() > 0) state.append("  ·  ");
            state.append(getString(R.string.tags_annotated));
        }
        Date date = TagRows.date(entry.date);
        if (date != null) {
            if (state.length() > 0) state.append("  ·  ");
            state.append(StringUtils.getNewsTimeStr(this, date));
        }
        TextView stateView = row.findViewById(R.id.entry_state);
        stateView.setText(state);
        stateView.setVisibility(state.length() > 0 ? View.VISIBLE : View.GONE);

        row.setOnClickListener(v -> showActions(v, entry, release));
        return row;
    }

    @NonNull
    private String releaseLabel(@NonNull TagRows.Kind kind) {
        switch (kind) {
            case LATEST: return getString(R.string.tags_release_latest);
            case PRE_RELEASE: return getString(R.string.tags_release_pre);
            case DRAFT: return getString(R.string.tags_release_draft);
            default: return getString(R.string.tags_release);
        }
    }

    private int releaseColor(@NonNull TagRows.Kind kind) {
        switch (kind) {
            case LATEST: return ContextCompat.getColor(this, R.color.pull_open);
            case PRE_RELEASE: return ContextCompat.getColor(this, R.color.material_amber_800);
            case DRAFT: return emptyText.getCurrentTextColor();
            default: return ViewUtils.getAccentColor(this);
        }
    }

    // ---------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------

    private static final int ACTION_OPEN_RELEASE = 1;
    private static final int ACTION_CREATE_RELEASE = 2;
    private static final int ACTION_FILES = 3;
    private static final int ACTION_COMMIT = 4;
    private static final int ACTION_COMPARE = 5;
    private static final int ACTION_ZIP = 6;
    private static final int ACTION_TAR = 7;
    private static final int ACTION_COPY = 8;
    private static final int ACTION_DELETE = 9;
    private static final int ACTION_BROWSER = 10;

    private void showActions(@NonNull View anchor, @NonNull TagRows.Entry entry, @Nullable JsonObject release) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        Menu menu = popup.getMenu();
        int order = 0;
        if (release != null && Json.lng(release, "databaseId") > 0) {
            menu.add(0, ACTION_OPEN_RELEASE, order++, R.string.tags_open_release);
        } else if (release == null && canWrite) {
            menu.add(0, ACTION_CREATE_RELEASE, order++, R.string.tags_create_release);
        }
        menu.add(0, ACTION_FILES, order++, R.string.tags_browse_files);
        if (!entry.commitOid.isEmpty()) menu.add(0, ACTION_COMMIT, order++, R.string.tags_open_commit);
        // "the tag below" means the next older one only in the full list, not in a filtered one
        final String previous = shownQuery.isEmpty() ? TagRows.previous(names, entry.name) : null;
        if (previous != null) {
            menu.add(0, ACTION_COMPARE, order++, getString(R.string.tags_compare_previous, previous));
        }
        menu.add(0, ACTION_ZIP, order++, R.string.tags_download_zip);
        menu.add(0, ACTION_TAR, order++, R.string.tags_download_tar);
        menu.add(0, ACTION_COPY, order++, R.string.tags_copy_name);
        if (canWrite) menu.add(0, ACTION_DELETE, order++, R.string.tags_delete);
        menu.add(0, ACTION_BROWSER, order, R.string.open_in_browser);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case ACTION_OPEN_RELEASE:
                    openRelease(release);
                    return true;
                case ACTION_CREATE_RELEASE:
                    startActivity(EditReleaseActivity.createForTag(this, owner, repo, entry.name));
                    return true;
                case ACTION_FILES:
                    startActivity(RepositoryActivity.createFilesIntent(this, owner, repo, entry.name, ""));
                    return true;
                case ACTION_COMMIT:
                    startActivity(CommitDetailActivity.createIntent(this, owner, repo, entry.commitOid));
                    return true;
                case ACTION_COMPARE:
                    startActivity(CompareActivity.createIntent(this, owner, repo, previous, entry.name));
                    return true;
                case ACTION_ZIP:
                    AppOpener.startDownload(this, TagRows.archiveUrl(owner, repo, entry.name, true),
                            TagRows.archiveName(repo, entry.name, true));
                    return true;
                case ACTION_TAR:
                    AppOpener.startDownload(this, TagRows.archiveUrl(owner, repo, entry.name, false),
                            TagRows.archiveName(repo, entry.name, false));
                    return true;
                case ACTION_COPY:
                    AppUtils.copyToClipboard(this, entry.name);
                    showSuccessToast(getString(R.string.tags_name_copied));
                    return true;
                case ACTION_DELETE:
                    confirmDelete(entry, release);
                    return true;
                case ACTION_BROWSER:
                    AppOpener.openInCustomTabsOrBrowser(this, TagRows.browserUrl(owner, repo, entry.name));
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    /** By id: drafts have no tag page on GitHub, so a tag would not find them. */
    private void openRelease(@Nullable JsonObject release) {
        if (release == null) return;
        startActivity(new Intent(this, ReleaseInfoActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("owner", owner)
                        .put("repoName", repo)
                        .put("releaseId", String.valueOf(Json.lng(release, "databaseId")))
                        .build()));
    }

    @NonNull
    private String releaseName(@NonNull JsonObject release, @NonNull String tag) {
        String name = Json.str(release, "name");
        return name.isEmpty() ? tag : name;
    }

    private void confirmDelete(@NonNull TagRows.Entry entry, @Nullable JsonObject release) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.tags_delete);
        if (release != null && Json.bool(release, "immutable")) {
            // GitHub refuses it; say so instead of offering a button that cannot work
            builder.setMessage(getString(R.string.tags_delete_immutable, releaseName(release, entry.name)))
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        String message = getString(R.string.tags_delete_message, entry.name);
        if (release != null) {
            // only a published release offers "Also delete the tag" on its own screen
            message += "\n\n" + getString(Json.bool(release, "isDraft") ? R.string.tags_delete_draft_note
                    : R.string.tags_delete_release_note, releaseName(release, entry.name));
        }
        builder.setMessage(message)
                .setPositiveButton(R.string.delete, (d, w) -> deleteTag(entry.name))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Deletes, then asks GitHub whether the tag is really gone before it says so. */
    private void deleteTag(@NonNull String tag) {
        showProgressDialog(getString(R.string.please_wait));
        disposables.add(Single.fromCallable(() -> {
                    GitHubApi.call(GitHubApi.service(ReleaseService.class)
                            .deleteTag(owner, repo, Uri.encode(tag, "/")));
                    ReleaseChanges.markChanged();
                    RepoChanges.markChanged();
                    Map<String, Object> vars = GraphQl.vars("owner", owner, "repo", repo,
                            "ref", "refs/tags/" + tag);
                    JsonObject data = GraphQl.run("query($owner: String!, $repo: String!, $ref: String!) {"
                            + " repository(owner: $owner, name: $repo) { ref(qualifiedName: $ref) { name } } }", vars);
                    return Json.obj(data, "repository.ref") == null;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(gone -> {
                    dismissProgressDialog();
                    if (gone) showSuccessToast(getString(R.string.tags_deleted, tag));
                    else showWarningToast(getString(R.string.tags_still_there, tag));
                    reload();
                }, error -> {
                    dismissProgressDialog();
                    showErrorToast(errorText(error));
                    reload();
                }));
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

    @NonNull
    private static String errorText(@NonNull Throwable error) {
        return ErrorTexts.of(error);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.refresh);
        menu.add(0, MENU_BROWSER, 1, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_REFRESH) {
            reload();
            return true;
        } else if (id == MENU_BROWSER) {
            AppOpener.openInCustomTabsOrBrowser(this, TagRows.tagsUrl(owner, repo));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, error -> {
                    loader.setVisibility(View.GONE);
                    loadMoreBn.setEnabled(true);
                    showErrorToast(errorText(error));
                }));
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        filterEdit = root.findViewById(R.id.tags_filter);
        list = root.findViewById(R.id.tags_list);
        loadMoreBn = root.findViewById(R.id.load_more_bn);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        loadMoreBn.setOnClickListener(v -> loadPage(false));
        filterEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(filterRunnable);
                handler.postDelayed(filterRunnable, FILTER_DELAY_MS);
            }
        });
        filterEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                reload();
                AppUtils.hideKeyboard(v);
                return true;
            }
            return false;
        });
    }
}
