package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BlameLines;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ErrorTexts;
import com.thirtydegreesray.openhub.util.GitHubLinks;
import com.thirtydegreesray.openhub.util.GraphQl;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.WindowUtil;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Blame of one file, like github.com/owner/repo/blame/branch/path: for every group of
 * lines the commit that changed them last (message, author, when), with a colored bar for
 * the age (orange = newest changes). Tap a header to open the commit.
 */
public class BlameActivity extends BaseActivity<IBaseContract.Presenter> {

    /** @param ref branch, tag or commit; null = the default branch */
    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, @Nullable String ref, @NonNull String path) {
        return new Intent(context, BlameActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).put("ref", ref).put("path", path).build());
    }

    private static final String BLAME_QUERY = "query($owner:String!,$name:String!,$ref:String!,$path:String!){"
            + "repository(owner:$owner,name:$name){object(expression:$ref){... on Commit{oid "
            + "blame(path:$path){ranges{startingLine endingLine age commit{oid abbreviatedOid "
            + "messageHeadline committedDate author{name user{login}}}}}}}}}";

    private static final String FILE_QUERY = "query($owner:String!,$name:String!,$expression:String!){"
            + "repository(owner:$owner,name:$name){object(expression:$expression){"
            + "... on Blob{text isBinary isTruncated}}}}";

    private static final int MENU_REFRESH = 1;
    private static final int MENU_HISTORY = 2;
    private static final int MENU_BROWSER = 3;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String ref;
    @AutoAccess String path;

    private RecyclerView listView;
    private TextView emptyText;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<BlameLines.Row> rows = new ArrayList<>();
    private final RowAdapter adapter = new RowAdapter();
    @Nullable private String note;
    private int numberDigits = 1;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_blame;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        String name = path.substring(path.lastIndexOf('/') + 1);
        String repoName = owner + "/" + repo;
        setToolbarTitle(getString(R.string.blame_title, name),
                StringUtils.isBlank(ref) ? repoName : repoName + " · " + ref);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);
        PullRefresh.wrap(listView, this::load).follow(loader);
        load();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void load() {
        disposables.clear();
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        final String expression = StringUtils.isBlank(ref) ? "HEAD" : ref;
        disposables.add(Single.fromCallable(() -> {
            JsonObject data = GraphQl.run(BLAME_QUERY, GraphQl.vars("owner", owner, "name", repo,
                    "ref", expression, "path", path));
            JsonObject commit = Json.obj(data, "repository.object");
            String oid = Json.str(commit, "oid");
            if (commit == null || oid.isEmpty()) {
                throw new IllegalStateException(getString(R.string.blame_not_found, path, expression));
            }
            List<BlameLines.Range> ranges = BlameLines.parse(Json.obj(commit, "blame"));
            // the text of exactly the commit the blame belongs to
            JsonObject file = Json.obj(GraphQl.run(FILE_QUERY, GraphQl.vars("owner", owner,
                    "name", repo, "expression", oid + ":" + path)), "repository.object");
            if (file == null) {
                throw new IllegalStateException(getString(R.string.blame_not_found, path, expression));
            }
            if (Json.bool(file, "isBinary")) throw new IllegalStateException(getString(R.string.blame_binary));
            List<String> lines = BlameLines.splitLines(Json.str(file, "text"));
            return new Object[]{BlameLines.rows(ranges, lines), Json.bool(file, "isTruncated"), lines.size()};
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loader.setVisibility(View.GONE);
                    @SuppressWarnings("unchecked")
                    List<BlameLines.Row> newRows = (List<BlameLines.Row>) result[0];
                    boolean truncated = (Boolean) result[1];
                    int lineCount = (Integer) result[2];
                    numberDigits = Math.max(1, String.valueOf(lineCount).length());
                    note = truncated ? getString(R.string.blame_truncated) : null;
                    rows.clear();
                    rows.addAll(newRows);
                    adapter.notifyDataSetChanged();
                    if (rows.isEmpty()) {
                        emptyText.setText(R.string.blame_empty);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                }, error -> {
                    loader.setVisibility(View.GONE);
                    rows.clear();
                    note = null;
                    adapter.notifyDataSetChanged();
                    String message = ErrorTexts.of(error);
                    emptyText.setText(message);
                    emptyText.setVisibility(View.VISIBLE);
                }));
    }

    private void openCommit(@NonNull BlameLines.Range range) {
        startActivity(CommitDetailActivity.createIntent(this, owner, repo, range.oid));
    }

    private void showRangeActions(@NonNull View anchor, @NonNull BlameLines.Range range) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
        popup.getMenu().add(0, 1, 0, R.string.blame_open_commit);
        popup.getMenu().add(0, 2, 1, R.string.blame_copy_sha);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openCommit(range);
                return true;
            } else if (item.getItemId() == 2) {
                AppUtils.copyToClipboard(this, range.oid);
                showSuccessToast(getString(R.string.blame_sha_copied));
                return true;
            }
            return false;
        });
        popup.show();
    }

    // ---------------------------------------------------------------------------------
    // Rows (built in code: a header per range, then monospace lines with numbers)
    // ---------------------------------------------------------------------------------

    private static final int TYPE_NOTE = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_LINE = 2;

    private class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private int offset() {
            return note == null ? 0 : 1;
        }

        @Override
        public int getItemCount() {
            return rows.size() + offset();
        }

        @Override
        public int getItemViewType(int position) {
            if (position < offset()) return TYPE_NOTE;
            return rows.get(position - offset()).isHeader() ? TYPE_HEADER : TYPE_LINE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            View view;
            if (viewType == TYPE_NOTE) {
                TextView text = new TextView(ctx);
                int pad = WindowUtil.dipToPx(ctx, 16);
                text.setPadding(pad, pad / 2, pad, pad / 2);
                text.setTextColor(emptyText.getTextColors());
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                view = text;
            } else {
                view = viewType == TYPE_HEADER ? headerView(ctx) : lineView(ctx);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(view) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_NOTE) {
                ((TextView) holder.itemView).setText(note);
                return;
            }
            BlameLines.Row row = rows.get(position - offset());
            boolean dark = AppUtils.isNightMode();
            View bar = holder.itemView.findViewWithTag("bar");
            BlameLines.Range owner = row.owner;
            bar.setBackgroundColor(BlameLines.ageColor(owner == null ? 10 : owner.age, dark));
            if (row.isHeader()) {
                BlameLines.Range range = row.range;
                ((TextView) holder.itemView.findViewWithTag("headline")).setText(range.headline);
                String when = range.date == null ? "" : StringUtils.getNewsTimeStr(BlameActivity.this, range.date);
                ((TextView) holder.itemView.findViewWithTag("meta")).setText(
                        getString(R.string.blame_header_meta, range.shortOid, range.who(), when));
                holder.itemView.setOnClickListener(v -> openCommit(range));
                holder.itemView.setOnLongClickListener(v -> {
                    showRangeActions(v, range);
                    return true;
                });
            } else {
                TextView number = holder.itemView.findViewWithTag("number");
                // room for the longest line number of the file
                StringBuilder widest = new StringBuilder();
                for (int i = 0; i < numberDigits; i++) widest.append('9');
                number.setMinWidth(Math.round(number.getPaint().measureText(widest.toString()))
                        + number.getPaddingLeft() + number.getPaddingRight());
                number.setText(String.valueOf(row.number));
                ((TextView) holder.itemView.findViewWithTag("code")).setText(row.text);
            }
        }
    }

    @NonNull
    private View headerView(@NonNull Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        int pad = WindowUtil.dipToPx(ctx, 8);
        row.addView(bar(ctx), barParams(ctx));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(pad + pad / 2, pad + pad / 2, pad * 2, pad / 2);
        TextView headline = new TextView(ctx);
        headline.setTag("headline");
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        headline.setTypeface(null, Typeface.BOLD);
        headline.setMaxLines(2);
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headline.setTextColor(primaryTextColor());
        texts.addView(headline);
        TextView meta = new TextView(ctx);
        meta.setTag("meta");
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setTypeface(Typeface.MONOSPACE);
        meta.setTextColor(emptyText.getTextColors());
        texts.addView(meta);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    @NonNull
    private View lineView(@NonNull Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(bar(ctx), barParams(ctx));
        int pad = WindowUtil.dipToPx(ctx, 8);
        TextView number = new TextView(ctx);
        number.setTag("number");
        number.setTypeface(Typeface.MONOSPACE);
        number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        number.setGravity(Gravity.END);
        number.setTextColor(emptyText.getTextColors());
        number.setPadding(pad, 0, pad, 0);
        row.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView code = new TextView(ctx);
        code.setTag("code");
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        code.setTextColor(primaryTextColor());
        code.setPadding(0, 0, pad, 0);
        row.addView(code, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** 6 dp wide, a little away from the screen edge (curved edges hide the first pixels). */
    @NonNull
    private static LinearLayout.LayoutParams barParams(@NonNull Context ctx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WindowUtil.dipToPx(ctx, 6),
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMarginStart(WindowUtil.dipToPx(ctx, 4));
        return params;
    }

    @NonNull
    private static View bar(@NonNull Context ctx) {
        View bar = new View(ctx);
        bar.setTag("bar");
        return bar;
    }

    private android.content.res.ColorStateList primaryTextColor() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true);
        if (value.resourceId != 0) {
            return androidx.core.content.ContextCompat.getColorStateList(this, value.resourceId);
        }
        return android.content.res.ColorStateList.valueOf(value.data);
    }

    // ---------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, R.string.refresh);
        menu.add(0, MENU_HISTORY, 1, R.string.file_history);
        menu.add(0, MENU_BROWSER, 2, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_REFRESH) {
            load();
            return true;
        } else if (id == MENU_HISTORY) {
            startActivity(CommitsListActivity.createIntentForFile(this, owner, repo, ref, path));
            return true;
        } else if (id == MENU_BROWSER) {
            AppOpener.openInCustomTabsOrBrowser(this, "https://github.com/" + owner + "/" + repo
                    + "/blame/" + GitHubLinks.encodePath(StringUtils.isBlank(ref) ? "HEAD" : ref)
                    + "/" + GitHubLinks.encodePath(path));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        listView = root.findViewById(R.id.blame_list);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
    }
}
