package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.MarkdownService;
import com.thirtydegreesray.openhub.http.WikiService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GitHubLinks;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * One page of a repository's wiki, rendered like on github.com. Links to other pages of
 * the wiki open here again; a page that cannot be shown here (another format, a private
 * wiki) opens in the browser when it was reached through a link.
 */
public class WikiPageActivity extends BaseActivity<IBaseContract.Presenter> {

    /** The wiki's own sidebar: GitHub shows it next to every page as its contents. */
    static final String SIDEBAR = "_Sidebar";

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String owner,
                                      @NonNull String repo, @Nullable String page,
                                      boolean fromLink) {
        Intent intent = new Intent(context, WikiPageActivity.class)
                .putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo)
                        .put("page", StringUtils.isBlank(page) ? "Home" : page)
                        .put("fromLink", fromLink).build());
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static final int MENU_HOME = 1;
    private static final int MENU_CONTENTS = 2;
    private static final int MENU_RECENT = 3;
    private static final int MENU_SHARE = 4;
    private static final int MENU_BROWSER = 5;

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess String page;
    @AutoAccess boolean fromLink;

    private SwipeRefreshLayout refreshLayout;
    private boolean refreshing;
    private CodeWebView webView;
    private View emptyLayout;
    private TextView emptyText;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private boolean shown;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_wiki_page;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(isSidebar() ? getString(R.string.wiki_contents) : wiki().title(),
                owner + "/" + repo);
        refreshLayout.setColorSchemeColors(ViewUtils.getRefreshLayoutColors(this));
        refreshLayout.setOnRefreshListener(() -> load(true));
        // the WebView scrolls itself: pull to refresh only at its top
        refreshLayout.setOnChildScrollUpCallback((parent, child) -> webView.canScrollVertically(-1));
        findViewById(R.id.empty_browser_bn).setOnClickListener(v ->
                AppOpener.openInCustomTabsOrBrowser(this, pageUrl()));
        load(false);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    @NonNull
    private GitHubLinks.Wiki wiki() {
        return GitHubLinks.wiki(owner, repo, page);
    }

    private boolean isSidebar() {
        return SIDEBAR.equals(page);
    }

    /** github.com address of this page; for the sidebar GitHub's list of all pages. */
    @NonNull
    private String pageUrl() {
        return isSidebar() ? "https://github.com/" + owner + "/" + repo + "/wiki/_pages"
                : wiki().htmlUrl();
    }

    /** Posted: a SwipeRefreshLayout told before its first layout keeps a stuck circle. */
    private void showRefreshing(boolean on) {
        refreshing = on;
        refreshLayout.post(() -> refreshLayout.setRefreshing(refreshing));
    }

    private void load(boolean force) {
        List<String> urls = wiki().rawUrls();
        if (urls.isEmpty()) {
            notShown();
            return;
        }
        showRefreshing(true);
        disposables.add(Single.fromCallable(() -> {
            String markdown = null;
            for (String url : urls) {
                Response<ResponseBody> response = GitHubApi.service(WikiService.class)
                        .getRaw(force, url).blockingFirst();
                if (response.isSuccessful() && response.body() != null) {
                    markdown = response.body().string();
                    break;
                }
                int code = response.code();
                String message = GitHubApi.errorMessage(response);
                if (code != 404) throw new GitHubApi.ApiException(code, message);
            }
            if (markdown == null) return "";
            markdown = GitHubLinks.wikiMarkdown(markdown);
            String html;
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("text", markdown);
                body.put("mode", "markdown");
                ResponseBody rendered = GitHubApi.call(GitHubApi.service(MarkdownService.class).render(body));
                html = rendered == null ? MarkdownHtml.render(markdown) : rendered.string();
            } catch (IOException e) {
                html = MarkdownHtml.render(markdown);
            }
            return "<div class=\"markdown-body\">" + GitHubLinks.wikiHtml(html, owner, repo) + "</div>";
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(html -> {
                    showRefreshing(false);
                    if (StringUtils.isBlank(html)) {
                        notShown();
                        return;
                    }
                    shown = true;
                    emptyLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    webView.setMdSource(html, null, true);
                }, error -> {
                    showRefreshing(false);
                    String message = StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
                    if (!shown) showEmpty(getString(R.string.wiki_page_failed, message));
                    else showErrorToast(message);
                }));
    }

    /** The page is not here (or not Markdown): a link goes on to the browser. */
    private void notShown() {
        if (fromLink && !shown) {
            AppOpener.openInCustomTabsOrBrowser(this, pageUrl());
            finish();
            return;
        }
        showEmpty(isSidebar() ? getString(R.string.wiki_no_contents)
                : "Home".equals(page) ? getString(R.string.wiki_no_pages)
                : getString(R.string.wiki_page_missing, wiki().title()));
    }

    private void showEmpty(@NonNull String text) {
        webView.setVisibility(View.GONE);
        emptyText.setText(text);
        emptyLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!"Home".equals(page)) menu.add(0, MENU_HOME, 0, R.string.wiki_home);
        if (!isSidebar()) menu.add(0, MENU_CONTENTS, 1, R.string.wiki_contents);
        menu.add(0, MENU_RECENT, 2, R.string.recent_wiki_updates);
        menu.add(0, MENU_SHARE, 3, R.string.share);
        menu.add(0, MENU_BROWSER, 4, R.string.open_in_browser);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case MENU_HOME:
                startActivity(createIntent(this, owner, repo, "Home", false));
                return true;
            case MENU_CONTENTS:
                startActivity(createIntent(this, owner, repo, SIDEBAR, false));
                return true;
            case MENU_RECENT:
                WikiActivity.show(this, owner, repo);
                return true;
            case MENU_SHARE:
                AppOpener.shareText(this, pageUrl());
                return true;
            case MENU_BROWSER:
                AppOpener.openInCustomTabsOrBrowser(this, pageUrl());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        refreshLayout = root.findViewById(R.id.refresh_layout);
        webView = root.findViewById(R.id.web_view);
        emptyLayout = root.findViewById(R.id.empty_layout);
        emptyText = root.findViewById(R.id.empty_text);
    }
}
