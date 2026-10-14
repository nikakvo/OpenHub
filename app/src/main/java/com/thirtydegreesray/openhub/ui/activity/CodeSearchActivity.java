package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.SearchService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Searches the code of one repository (GitHub code search, default branch), shows the
 * found fragments with the matches marked, and opens a file with a tap.
 * GitHub qualifiers such as path:src or extension:java can be typed after the words.
 */
public class CodeSearchActivity extends BaseActivity<IBaseContract.Presenter> {

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner, @NonNull String repo) {
        return new Intent(context, CodeSearchActivity.class).putExtras(BundleHelper.builder()
                .put("owner", owner).put("repo", repo).build());
    }

    private static final int PER_PAGE = 30;
    /** GitHub returns at most 1000 results of a search. */
    private static final int MAX_RESULTS = 1000;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String query;

    private EditText searchEdit;
    private LinearLayout results;
    private Button loadMoreBn;
    private TextView emptyText;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private int page;
    private int shown;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_code_search;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.code_search_title), owner + "/" + repo);
        if (!StringUtils.isBlank(query)) {
            searchEdit.setText(query);
            search();
        } else {
            searchEdit.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    private void search() {
        String text = searchEdit.getText().toString().trim();
        if (text.isEmpty()) return;
        query = text;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
        disposables.clear();
        results.removeAllViews();
        page = 0;
        shown = 0;
        loadNext();
    }

    private void loadNext() {
        final int next = page + 1;
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        loadMoreBn.setVisibility(View.GONE);
        String q = query + " repo:" + owner + "/" + repo;
        disposables.add(Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(SearchService.class)
                        .searchCode(q, next, PER_PAGE)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(answer -> {
                    loader.setVisibility(View.GONE);
                    page = next;
                    int total = Json.integer(answer, "total_count");
                    JsonElement items = answer.get("items");
                    if (items != null && items.isJsonArray()) {
                        for (JsonElement e : items.getAsJsonArray()) {
                            results.addView(row(e.getAsJsonObject()));
                            shown++;
                        }
                    }
                    setToolbarSubTitle(owner + "/" + repo + " · "
                            + getResources().getQuantityString(R.plurals.code_search_count, total, total));
                    loadMoreBn.setVisibility(shown < total && shown < MAX_RESULTS ? View.VISIBLE : View.GONE);
                    if (shown == 0) searchPaths();
                }, error -> {
                    loader.setVisibility(View.GONE);
                    String message = StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
                    if (error instanceof GitHubApi.ApiException && ((GitHubApi.ApiException) error).code == 403) {
                        // code search allows only a few searches per minute
                        message = getString(R.string.code_search_rate_limit);
                    }
                    showErrorToast(message);
                    if (shown > 0) loadMoreBn.setVisibility(View.VISIBLE);
                }));
    }

    /**
     * GitHub's code search finds nothing in forks (unless they have more stars than the
     * original) and in repositories it has not indexed yet. Then the file list of the
     * default branch is searched by path and name instead, which always works.
     */
    private void searchPaths() {
        String words = query;
        loader.setVisibility(View.VISIBLE);
        disposables.add(Single.fromCallable(() -> {
                    SearchService service = GitHubApi.service(SearchService.class);
                    JsonObject repository = GitHubApi.call(service.getRepoInfo(owner, repo));
                    String branch = Json.str(repository, "default_branch");
                    JsonObject tree = GitHubApi.call(service.getFileTree(owner, repo,
                            android.net.Uri.encode(branch)));
                    java.util.List<String> found = new java.util.ArrayList<>();
                    JsonElement entries = tree == null ? null : tree.get("tree");
                    if (entries != null && entries.isJsonArray()) {
                        for (JsonElement e : entries.getAsJsonArray()) {
                            JsonObject entry = e.getAsJsonObject();
                            if (!"blob".equals(Json.str(entry, "type"))) continue;
                            String path = Json.str(entry, "path");
                            if (pathMatches(path, words)) found.add(path);
                            if (found.size() >= 300) break;
                        }
                    }
                    return new Object[]{Json.bool(repository, "fork"), branch, found};
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    loader.setVisibility(View.GONE);
                    boolean fork = (Boolean) result[0];
                    String branch = (String) result[1];
                    //noinspection unchecked
                    java.util.List<String> found = (java.util.List<String>) result[2];
                    String why = getString(fork ? R.string.code_search_fork : R.string.code_search_not_indexed);
                    if (found.isEmpty()) {
                        emptyText.setText(getString(R.string.code_search_none, query) + "\n\n" + why);
                        emptyText.setVisibility(View.VISIBLE);
                        return;
                    }
                    TextView note = new TextView(this);
                    note.setText(why + "\n" + getResources().getQuantityString(R.plurals.code_search_paths,
                            found.size(), found.size()));
                    note.setTextSize(13);
                    note.setPadding(dp(16), dp(8), dp(16), dp(4));
                    note.setTextColor(emptyText.getCurrentTextColor());
                    results.addView(note);
                    for (String path : found) results.addView(pathRow(path, branch));
                    setToolbarSubTitle(owner + "/" + repo + " · "
                            + getResources().getQuantityString(R.plurals.code_search_paths, found.size(), found.size()));
                }, error -> {
                    loader.setVisibility(View.GONE);
                    emptyText.setText(getString(R.string.code_search_none, query));
                    emptyText.setVisibility(View.VISIBLE);
                }));
    }

    /** All words in the path; path:, extension: and filename: narrow it like on GitHub. */
    private static boolean pathMatches(@NonNull String path, @NonNull String query) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        for (String token : query.toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.startsWith("extension:")) {
                if (!name.endsWith("." + token.substring("extension:".length()))) return false;
            } else if (token.startsWith("path:")) {
                if (!lower.contains(token.substring("path:".length()))) return false;
            } else if (token.startsWith("filename:")) {
                if (!name.contains(token.substring("filename:".length()))) return false;
            } else if (token.contains(":")) {
                // other qualifiers (language:, size:...) cannot be applied to a path
            } else if (!lower.contains(token)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private View pathRow(@NonNull String path, @NonNull String branch) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_code_result, results, false);
        ((TextView) row.findViewById(R.id.result_path)).setText(path);
        TextView info = row.findViewById(R.id.result_fragments);
        info.setText(R.string.code_search_match_in_name);
        String encodedPath = encodePath(path);
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + encodedPath
                + "?ref=" + android.net.Uri.encode(branch);
        String htmlUrl = "https://github.com/" + owner + "/" + repo + "/blob/" + branch + "/" + encodedPath;
        row.setOnClickListener(v -> ViewerActivity.show(this, url, htmlUrl));
        row.setOnLongClickListener(v -> {
            AppUtils.copyToClipboard(this, path);
            return true;
        });
        return row;
    }

    @NonNull
    private static String encodePath(@NonNull String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/")) {
            if (out.length() > 0) out.append('/');
            out.append(android.net.Uri.encode(part));
        }
        return out.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @NonNull
    private View row(@NonNull JsonObject item) {
        View row = LayoutInflater.from(this).inflate(R.layout.layout_item_code_result, results, false);
        String path = Json.str(item, "path");
        ((TextView) row.findViewById(R.id.result_path)).setText(path);
        TextView fragments = row.findViewById(R.id.result_fragments);
        SpannableStringBuilder text = new SpannableStringBuilder();
        int mark = (ViewUtils.getAccentColor(this) & 0x00FFFFFF) | 0x55000000;
        JsonElement matches = item.get("text_matches");
        if (matches != null && matches.isJsonArray()) {
            for (JsonElement m : matches.getAsJsonArray()) {
                JsonObject match = m.getAsJsonObject();
                if (!"content".equals(Json.str(match, "property"))) continue;
                String fragment = Json.str(match, "fragment");
                if (fragment.isEmpty()) continue;
                if (text.length() > 0) text.append("\n⋯\n");
                int start = text.length();
                text.append(fragment.replaceAll("\\s+$", ""));
                JsonElement spans = match.get("matches");
                if (spans == null || !spans.isJsonArray()) continue;
                for (JsonElement s : spans.getAsJsonArray()) {
                    JsonArray indices = s.getAsJsonObject().getAsJsonArray("indices");
                    if (indices == null || indices.size() < 2) continue;
                    int a = start + indices.get(0).getAsInt();
                    int b = start + indices.get(1).getAsInt();
                    if (a < start || b > text.length() || a >= b) continue;
                    text.setSpan(new BackgroundColorSpan(mark), a, b, 0);
                    text.setSpan(new StyleSpan(Typeface.BOLD), a, b, 0);
                }
            }
        }
        if (text.length() == 0) text.append(getString(R.string.code_search_match_in_name));
        fragments.setText(text);

        String url = Json.str(item, "url");
        String htmlUrl = Json.str(item, "html_url");
        row.setOnClickListener(v -> ViewerActivity.show(this, url, htmlUrl));
        row.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v, Gravity.NO_GRAVITY, R.attr.popup_menu_theme, 0);
            menu.getMenu().add(0, 1, 0, R.string.copy_path);
            menu.getMenu().add(0, 2, 1, R.string.copy_url);
            menu.getMenu().add(0, 3, 2, R.string.open_in_browser);
            menu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) AppUtils.copyToClipboard(this, path);
                else if (menuItem.getItemId() == 2) AppUtils.copyToClipboard(this, htmlUrl);
                else AppOpener.openInCustomTabsOrBrowser(this, htmlUrl);
                return true;
            });
            menu.show();
            return true;
        });
        return row;
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        searchEdit = root.findViewById(R.id.search_edit);
        results = root.findViewById(R.id.results);
        loadMoreBn = root.findViewById(R.id.load_more_bn);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
        loadMoreBn.setOnClickListener(v -> loadNext());
        root.findViewById(R.id.search_bn).setOnClickListener(v -> search());
        searchEdit.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search();
                return true;
            }
            return false;
        });
    }
}
