package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.DownloadSource;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.DownloadSourceDialog;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.ReleaseFeed;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Newest release of every starred repository, newest first, grouped by age.
 * Releases published since the last visit are marked "New".
 */
public class ReleaseFeedActivity extends BaseActivity<IBaseContract.Presenter> {

    public static void show(@NonNull Activity activity) {
        activity.startActivity(new Intent(activity, ReleaseFeedActivity.class));
    }

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_RELEASE = 1;

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private EditText filterEdit;
    private TextView emptyText;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final FeedAdapter adapter = new FeedAdapter();
    @Nullable private JsonObject feed;
    /** Releases published after this are "New" during this visit. */
    private long seenAt;
    private boolean withPreReleases;
    private boolean loading;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_release_feed;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.release_feed_title));
        withPreReleases = ReleaseFeed.showPreReleases(this);
        seenAt = savedInstanceState != null ? savedInstanceState.getLong("seenAt") : 0;
        if (seenAt == 0) {
            seenAt = ReleaseFeed.lastSeen(this);
            // First visit: the last week counts as new.
            if (seenAt == 0) seenAt = System.currentTimeMillis() - 7 * DAY;
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        refreshLayout.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(this));
        refreshLayout.setOnRefreshListener(this::refresh);

        feed = ReleaseFeed.loadCache(this);
        render();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("seenAt", seenAt);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        if (isFinishing()) {
            long newest = newestPublished();
            if (newest > ReleaseFeed.lastSeen(this)) ReleaseFeed.markSeen(this, newest);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_release_feed, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem pre = menu.findItem(R.id.action_show_prereleases);
        if (pre != null) pre.setChecked(withPreReleases);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            refresh();
            return true;
        } else if (id == R.id.action_show_prereleases) {
            withPreReleases = !item.isChecked();
            item.setChecked(withPreReleases);
            ReleaseFeed.setShowPreReleases(this, withPreReleases);
            render();
            return true;
        } else if (id == R.id.action_mark_all_seen) {
            long newest = newestPublished();
            if (newest > 0) {
                seenAt = Math.max(seenAt, newest);
                ReleaseFeed.markSeen(this, seenAt);
                render();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onToolbarDoubleClick() {
        super.onToolbarDoubleClick();
        recyclerView.scrollToPosition(0);
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        refreshLayout.setRefreshing(true);
        disposables.add(Single.fromCallable(() -> ReleaseFeed.fetch(getApplicationContext()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loading = false;
                    refreshLayout.setRefreshing(false);
                    feed = result;
                    render();
                }, error -> {
                    loading = false;
                    refreshLayout.setRefreshing(false);
                    showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                    render();
                }));
    }

    private long newestPublished() {
        long newest = 0;
        for (JsonObject item : repos()) {
            newest = Math.max(newest, ReleaseFeed.published(ReleaseFeed.shown(item, withPreReleases)));
        }
        return newest;
    }

    @NonNull
    private List<JsonObject> repos() {
        List<JsonObject> list = new ArrayList<>();
        JsonElement repos = feed == null ? null : feed.get("repos");
        if (repos != null && repos.isJsonArray()) {
            for (JsonElement element : repos.getAsJsonArray()) {
                if (element.isJsonObject()) list.add(element.getAsJsonObject());
            }
        }
        return list;
    }

    /** Filters, sorts and groups the feed into the list rows. */
    private void render() {
        String query = filterEdit.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Row> releases = new ArrayList<>();
        int newCount = 0;
        for (JsonObject item : repos()) {
            JsonObject release = ReleaseFeed.shown(item, withPreReleases);
            if (release == null) continue;
            long published = ReleaseFeed.published(release);
            boolean isNew = published > seenAt;
            if (isNew) newCount++;
            if (!query.isEmpty() && !Json.str(item, "repo").toLowerCase(Locale.ROOT).contains(query)) continue;
            releases.add(new Row(item, release, published, isNew));
        }
        Collections.sort(releases, (a, b) -> Long.compare(b.published, a.published));

        List<Row> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        int lastGroup = -1;
        for (Row row : releases) {
            long age = now - row.published;
            int group = age < DAY ? 0 : age < 7 * DAY ? 1 : age < 30 * DAY ? 2 : 3;
            if (group != lastGroup) {
                rows.add(new Row(getString(group == 0 ? R.string.release_feed_today
                        : group == 1 ? R.string.release_feed_week
                        : group == 2 ? R.string.release_feed_month : R.string.release_feed_older)));
                lastGroup = group;
            }
            rows.add(row);
        }
        adapter.setRows(rows);

        if (newCount > 0) {
            setToolbarSubTitle(getResources().getQuantityString(R.plurals.release_feed_new_count,
                    newCount, newCount));
        } else if (feed != null && feed.has("updated")) {
            setToolbarSubTitle(getString(R.string.release_feed_updated,
                    StringUtils.getNewsTimeStr(this, new Date(feed.get("updated").getAsLong()))));
        } else {
            setToolbarSubTitle(null);
        }

        if (rows.isEmpty() && feed != null) {
            emptyText.setText(query.isEmpty() ? R.string.release_feed_empty : R.string.release_feed_empty_filter);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private void openRelease(@NonNull Row row) {
        String repo = Json.str(row.item, "repo");
        int slash = repo.indexOf('/');
        if (slash <= 0) return;
        ReleaseInfoActivity.show(this, repo.substring(0, slash), repo.substring(slash + 1),
                Json.str(row.release, "tag"));
    }

    private void openRepository(@NonNull Row row) {
        String repo = Json.str(row.item, "repo");
        int slash = repo.indexOf('/');
        if (slash <= 0) return;
        RepositoryActivity.show(this, repo.substring(0, slash), repo.substring(slash + 1));
    }

    private void showDownloads(@NonNull Row row) {
        List<DownloadSource> sources = new ArrayList<>();
        JsonElement assets = row.release.get("assets");
        if (assets != null && assets.isJsonArray()) {
            for (JsonElement element : assets.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject asset = element.getAsJsonObject();
                JsonElement size = asset.get("size");
                sources.add(new DownloadSource(Json.str(asset, "url"), Json.str(asset, "name"),
                        size == null ? 0 : size.getAsLong(), Json.integer(asset, "count"))
                        .setDigest(Json.str(asset, "digest")));
            }
        }
        DownloadSourceDialog.show(this, Json.str(row.item, "repo"), Json.str(row.release, "tag"), sources);
    }

    private void showRowMenu(@NonNull View anchor, @NonNull Row row) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        Menu menu = popup.getMenu();
        menu.add(0, 1, 0, R.string.release_feed_open_repo);
        menu.add(0, 2, 1, R.string.download);
        menu.add(0, 3, 2, R.string.open_in_browser);
        menu.add(0, 4, 3, R.string.share);
        popup.setOnMenuItemClickListener(menuItem -> {
            String url = Json.str(row.release, "url");
            switch (menuItem.getItemId()) {
                case 1: openRepository(row); break;
                case 2: showDownloads(row); break;
                case 3: if (!url.isEmpty()) AppOpener.openInCustomTabsOrBrowser(this, url); break;
                case 4: if (!url.isEmpty()) AppOpener.shareText(this, url); break;
                default: return false;
            }
            return true;
        });
        popup.show();
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        refreshLayout = root.findViewById(R.id.refresh_layout);
        recyclerView = root.findViewById(R.id.feed_list);
        filterEdit = root.findViewById(R.id.feed_filter);
        emptyText = root.findViewById(R.id.empty_text);
        filterEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render();
            }
        });
    }

    private static final class Row {
        @Nullable final String header;
        final JsonObject item;
        final JsonObject release;
        final long published;
        final boolean isNew;

        Row(@NonNull String header) {
            this.header = header;
            this.item = null;
            this.release = null;
            this.published = 0;
            this.isNew = false;
        }

        Row(@NonNull JsonObject item, @NonNull JsonObject release, long published, boolean isNew) {
            this.header = null;
            this.item = item;
            this.release = release;
            this.published = published;
            this.isNew = isNew;
        }
    }

    private final class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<Row> rows = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void setRows(@NonNull List<Row> rows) {
            this.rows = rows;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).header != null ? TYPE_HEADER : TYPE_RELEASE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.layout_item_feed_header, parent, false));
            }
            return new ReleaseHolder(inflater.inflate(R.layout.layout_item_release_feed, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).text.setText(row.header);
            } else {
                ((ReleaseHolder) holder).bind(row);
            }
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(@NonNull View view) {
            super(view);
            text = (TextView) view;
        }
    }

    private final class ReleaseHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView repo;
        final TextView newBadge;
        final TextView tag;
        final TextView preBadge;
        final TextView title;
        final TextView meta;
        final View downloadBn;

        ReleaseHolder(@NonNull View view) {
            super(view);
            avatar = view.findViewById(R.id.feed_avatar);
            repo = view.findViewById(R.id.feed_repo);
            newBadge = view.findViewById(R.id.feed_new);
            tag = view.findViewById(R.id.feed_tag);
            preBadge = view.findViewById(R.id.feed_pre);
            title = view.findViewById(R.id.feed_title);
            meta = view.findViewById(R.id.feed_meta);
            downloadBn = view.findViewById(R.id.feed_download_bn);
        }

        void bind(@NonNull Row row) {
            repo.setText(Json.str(row.item, "repo"));
            newBadge.setVisibility(row.isNew ? View.VISIBLE : View.GONE);
            String tagName = Json.str(row.release, "tag");
            String name = Json.str(row.release, "name").trim();
            tag.setText(tagName);
            preBadge.setVisibility(Json.bool(row.release, "pre") ? View.VISIBLE : View.GONE);
            boolean showTitle = !name.isEmpty() && !name.equals(tagName);
            title.setText(name);
            title.setVisibility(showTitle ? View.VISIBLE : View.GONE);

            JsonElement assets = row.release.get("assets");
            int files = assets != null && assets.isJsonArray() ? ((JsonArray) assets).size() : 0;
            String when = row.published == 0 ? "" : StringUtils.getNewsTimeStr(ReleaseFeedActivity.this,
                    new Date(row.published));
            meta.setText(files == 0 ? when : getString(R.string.release_feed_meta, when,
                    getResources().getQuantityString(R.plurals.release_feed_files, files, files)));

            GlideApp.with(ReleaseFeedActivity.this)
                    .load(Json.str(row.item, "avatar"))
                    .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                    .into(avatar);

            itemView.setOnClickListener(v -> openRelease(row));
            itemView.setOnLongClickListener(v -> {
                showRowMenu(repo, row);
                return true;
            });
            avatar.setOnClickListener(v -> openRepository(row));
            downloadBn.setOnClickListener(v -> showDownloads(row));
        }
    }
}
