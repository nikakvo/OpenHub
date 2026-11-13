

package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.Event;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IRepositoriesContract;
import com.thirtydegreesray.openhub.mvp.model.Collection;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.SearchModel;
import com.thirtydegreesray.openhub.mvp.model.SearchResult;
import com.thirtydegreesray.openhub.mvp.model.Topic;
import com.thirtydegreesray.openhub.mvp.model.TrendingLanguage;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.model.filter.RepositoriesFilter;
import com.thirtydegreesray.openhub.mvp.model.filter.TrendingSince;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePagerPresenter;
import com.thirtydegreesray.openhub.ui.fragment.RepositoriesFragment;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.TrendingParser;

import com.thirtydegreesray.openhub.common.Subscribe;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.thirtydegreesray.openhub.util.RepoFilter;
import com.thirtydegreesray.openhub.util.AppLog;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import okhttp3.ResponseBody;
import retrofit2.Response;
import com.thirtydegreesray.openhub.http.GitHubExplore;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created on 2017/7/18.
 *
 * @author ThirtyDegreesRay
 */

public class RepositoriesPresenter extends BasePagerPresenter<IRepositoriesContract.View>
        implements IRepositoriesContract.Presenter {

    private ArrayList<Repository> repos;

    @AutoAccess RepositoriesFragment.RepositoriesType type;
    @AutoAccess String user;
    @AutoAccess String repo;

    @AutoAccess SearchModel searchModel;
    @AutoAccess TrendingSince since;

    @AutoAccess RepositoriesFilter filter;

    @AutoAccess TrendingLanguage language;

    @AutoAccess Collection collection;
    @AutoAccess Topic topic;

    /** The filter field: every repository of the list, 100 per page. */
    static final int ALL_PER_PAGE = 100;
    /** At most 5000 repositories are read for the filter. */
    static final int ALL_MAX_PAGES = 50;

    @NonNull private String query = "";
    /** Every repository of the list (complete), null until read. */
    @Nullable private ArrayList<Repository> allRepos;
    /** Read so far while {@link #allLoad} runs. */
    @Nullable private ArrayList<Repository> partialRepos;
    @Nullable private Disposable allLoad;
    private boolean allLoadFailed;
    private boolean allTruncated;

    @Inject
    public RepositoriesPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if (type.equals(RepositoriesFragment.RepositoriesType.SEARCH)) {
            setEventSubscriber(true);
        }
    }

    @Override
    protected void loadData() {
        if (RepositoriesFragment.RepositoriesType.SEARCH.equals(type)) {
            if (searchModel != null) searchRepos(1);
            return;
        }
        if (RepositoriesFragment.RepositoriesType.COLLECTION.equals(type)) {
            loadCollection(false);
            return;
        }
        if (RepositoriesFragment.RepositoriesType.TOPIC.equals(type)) {
            initSearchModelForTopic();
            searchRepos(1);
            return;
        }
        if(RepositoriesFragment.RepositoriesType.TRENDING.equals(type)){
            loadTrending(false);
            return;
        }
        loadRepositories(false, 1);
    }

    @Override
    public void loadRepositories(final boolean isReLoad, final int page) {
        filter = getFilter();
        if ((isReLoad || page == 1) && isFilterable()) resetAllRepos();
        if (type.equals(RepositoriesFragment.RepositoriesType.SEARCH)) {
            searchRepos(page);
            return;
        }
        if (RepositoriesFragment.RepositoriesType.COLLECTION.equals(type)) {
            loadCollection(isReLoad);
            return;
        }
        if (RepositoriesFragment.RepositoriesType.TOPIC.equals(type)) {
            initSearchModelForTopic();
            searchRepos(page);
            return;
        }
        if(RepositoriesFragment.RepositoriesType.TRENDING.equals(type)){
            loadTrending(isReLoad);
            return;
        }
        mView.showLoading();
        final boolean readCacheFirst = !isReLoad && page == 1;

        HttpObserver<ArrayList<Repository>> httpObserver = new HttpObserver<ArrayList<Repository>>() {
            @Override
            public void onError(@NonNull Throwable error) {
                mView.hideLoading();
                handleError(error);
            }

            @Override
            public void onSuccess(@NonNull HttpResponse<ArrayList<Repository>> response) {
                mView.hideLoading();
                if (isReLoad || readCacheFirst || repos == null || page == 1) {
                    repos = response.body();
                } else {
                    repos.addAll(response.body());
                }
                if (response.body().size() == 0 && repos.size() != 0) {
                    mView.setCanLoadMore(false);
                } else if (query.isEmpty()) {
                    mView.showRepositories(repos);
                } else {
                    publishFiltered();
                }
            }
        };

        generalRxHttpExecute(new IObservableCreator<ArrayList<Repository>>() {
            @Nullable
            @Override
            public Observable<Response<ArrayList<Repository>>> createObservable(boolean forceNetWork) {
                return getObservable(forceNetWork, page);
            }
        }, httpObserver, readCacheFirst);

    }

    @Override
    public void loadRepositories(RepositoriesFilter filter) {
        this.filter = filter;
        loadRepositories(false, 1);
    }

    private Observable<Response<ArrayList<Repository>>> getObservable(boolean forceNetWork, int page) {
        switch (type) {
            case OWNED:
                return getRepoService().getUserRepos(forceNetWork, page, filter.getType(),
                        filter.getSort(), filter.getSortDirection());
            case PUBLIC:
                return getRepoService().getUserPublicRepos(forceNetWork, user, page,
                        filter.getType(), filter.getSort(), filter.getSortDirection());
            case STARRED:
                return getRepoService().getStarredRepos(forceNetWork, user, page,
                        filter.getSort(), filter.getSortDirection());
            case FORKS:
                return getRepoService().getForks(forceNetWork, user, repo, page);
            default:
                return null;
        }
    }

    private void searchRepos(final int page) {
        mView.showLoading();

        HttpObserver<SearchResult<Repository>> httpObserver =
                new HttpObserver<SearchResult<Repository>>() {
                    @Override
                    public void onError(@NonNull Throwable error) {
                        mView.hideLoading();
                        handleError(error);
                    }

                    @Override
                    public void onSuccess(@NonNull HttpResponse<SearchResult<Repository>> response) {
                        mView.hideLoading();
                        if (repos == null || page == 1) {
                            repos = response.body().getItems();
                        } else {
                            repos.addAll(response.body().getItems());
                        }
                        if (response.body().getItems().size() == 0 && repos.size() != 0) {
                            mView.setCanLoadMore(false);
                        } else {
                            mView.showRepositories(repos);
                        }
                    }
                };
        generalRxHttpExecute(new IObservableCreator<SearchResult<Repository>>() {
            @Nullable
            @Override
            public Observable<Response<SearchResult<Repository>>> createObservable(boolean forceNetWork) {
                return getSearchService().searchRepos(searchModel.getQuery(), searchModel.getSort(),
                        searchModel.getOrder(), page);
            }
        }, httpObserver);
    }

    @Subscribe
    public void onSearchEvent(@NonNull Event.SearchEvent searchEvent) {
        if (!searchEvent.searchModel.getType().equals(SearchModel.SearchType.Repository)) return;
        setLoaded(false);
        this.searchModel = searchEvent.searchModel;
        prepareLoadData();
    }

    private void handleError(Throwable error) {
        if (!StringUtils.isBlankList(repos)) {
            mView.showErrorToast(getErrorTip(error));
        } else if (error instanceof HttpPageNoFoundError) {
            mView.showRepositories(new ArrayList<Repository>());
        } else {
            mView.showLoadError(getErrorTip(error));
        }
    }

    // ---------------------------------------------------------------------------------
    // Filter field (my, starred and a user's repositories)
    // ---------------------------------------------------------------------------------

    /** Lists that get the filter field. */
    public boolean isFilterable() {
        return RepositoriesFragment.RepositoriesType.OWNED.equals(type)
                || RepositoriesFragment.RepositoriesType.PUBLIC.equals(type)
                || RepositoriesFragment.RepositoriesType.STARRED.equals(type);
    }

    @NonNull
    public String getQuery() {
        return query;
    }

    /** The text of the filter field changed. */
    public void setQuery(@Nullable String text) {
        String next = text == null ? "" : text.trim();
        if (next.equals(query)) return;
        query = next;
        if (mView == null) return;
        mView.setFiltering(!query.isEmpty());
        if (query.isEmpty()) {
            mView.showFilterStatus(null);
            if (repos != null) mView.showRepositories(repos);
            return;
        }
        if (allRepos == null && allLoad == null) loadAllRepos();
        publishFiltered();
    }

    /** Shows the matches and a status with the real counts. */
    private void publishFiltered() {
        if (mView == null || query.isEmpty()) return;
        List<Repository> source = allRepos != null ? allRepos
                : partialRepos != null && (repos == null || partialRepos.size() >= repos.size())
                ? partialRepos : repos;
        ArrayList<Repository> shown = RepoFilter.filter(source, query);
        mView.showRepositories(shown);
        int total = source == null ? 0 : source.size();
        String status;
        if (allLoad != null) {
            status = String.format(getString(R.string.repo_filter_loading), shown.size(), total);
        } else if (allLoadFailed) {
            status = String.format(getString(R.string.repo_filter_partial), shown.size(), total);
        } else if (allTruncated) {
            status = String.format(getString(R.string.repo_filter_first), shown.size(), total);
        } else {
            status = String.format(getString(R.string.repo_filter_count), shown.size(), total);
        }
        mView.showFilterStatus(status);
    }

    /** Reads every page (100 each) in the background, the list updates after each page. */
    private void loadAllRepos() {
        allLoadFailed = false;
        allTruncated = false;
        partialRepos = null;
        final RepositoriesFilter listFilter = getFilter();
        allLoad = Observable.<ArrayList<Repository>>create(emitter -> {
            ArrayList<Repository> all = new ArrayList<>();
            for (int page = 1; page <= ALL_MAX_PAGES && !emitter.isDisposed(); page++) {
                Response<ArrayList<Repository>> response = allReposPage(listFilter, page).blockingFirst();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("HTTP " + response.code());
                }
                all.addAll(response.body());
                boolean last = response.body().size() < ALL_PER_PAGE;
                if (!last && page == ALL_MAX_PAGES) allTruncated = true;
                emitter.onNext(new ArrayList<>(all));
                if (last) break;
            }
            emitter.onComplete();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    partialRepos = list;
                    publishFiltered();
                }, error -> {
                    AppLog.e("RepositoriesPresenter", "loadAllRepos", error);
                    allLoad = null;
                    allLoadFailed = true;
                    publishFiltered();
                }, () -> {
                    allRepos = partialRepos != null ? partialRepos : new ArrayList<>();
                    partialRepos = null;
                    allLoad = null;
                    publishFiltered();
                });
    }

    @NonNull
    private Observable<Response<ArrayList<Repository>>> allReposPage(
            @NonNull RepositoriesFilter listFilter, int page) {
        switch (type) {
            case OWNED:
                return getRepoService().getUserReposPage(true, page, ALL_PER_PAGE,
                        listFilter.getType(), listFilter.getSort(), listFilter.getSortDirection());
            case PUBLIC:
                return getRepoService().getUserPublicReposPage(true, user, page, ALL_PER_PAGE,
                        listFilter.getType(), listFilter.getSort(), listFilter.getSortDirection());
            default:
                return getRepoService().getStarredReposPage(true, user, page, ALL_PER_PAGE,
                        listFilter.getSort(), listFilter.getSortDirection());
        }
    }

    /** The list was loaded again or sorted otherwise: the complete list is read again. */
    private void resetAllRepos() {
        if (allLoad != null) allLoad.dispose();
        allLoad = null;
        allRepos = null;
        partialRepos = null;
        allLoadFailed = false;
        allTruncated = false;
        if (!query.isEmpty()) loadAllRepos();
    }

    @Override
    public void detachView() {
        if (allLoad != null) allLoad.dispose();
        allLoad = null;
        super.detachView();
    }

    public String getUser() {
        return user;
    }

    public RepositoriesFragment.RepositoriesType getType() {
        return type;
    }

    public RepositoriesFilter getFilter() {
        if (filter == null) {
            filter = RepositoriesFragment.RepositoriesType.STARRED.equals(type) ?
                    RepositoriesFilter.DEFAULT_STARRED_REPO : RepositoriesFilter.DEFAULT;
        }
        return filter;
    }

    private void showQueryRepos(ArrayList<Repository> queryRepos, int page){
        if(repos == null || page == 1){
            repos = queryRepos;
        } else {
            repos.addAll(queryRepos);
        }

        mView.showRepositories(repos);
        mView.hideLoading();
    }

    public void setLanguage(TrendingLanguage language) {
        this.language = language;
    }

    private void loadCollection(boolean isReload){
        mView.showLoading();
        Observable.just(collection.getId())
                .map(id -> {
                    try {
                        return GitHubExplore.getCollectionRepos(id);
                    } catch (Exception e) {
                        throw Exceptions.propagate(e);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    repos = results;
                    mView.showRepositories(repos);
                }, error -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    mView.showLoadError(getErrorTip(error));
                });
    }

    private void initSearchModelForTopic(){
        if(searchModel == null){
            searchModel = new SearchModel(SearchModel.SearchType.Repository);
            searchModel.setQuery("topic:" + topic.getId());
        }
    }

    private void loadTrending(boolean isReload){
        mView.showLoading();
        HttpObserver<ResponseBody> httpObserver = new HttpObserver<ResponseBody>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
                mView.showLoadError(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<ResponseBody> response) {
                try {
                    parseTrendingPageData(response.body().string());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        generalRxHttpExecute(forceNetWork -> getGitHubWebPageService()
                        .getTrendingRepos(forceNetWork, language.getSlug(), since.name()),
                httpObserver, !isReload);
    }

    private void parseTrendingPageData(String page){
        Observable.just(page)
                .map(s -> {
                    ArrayList<Repository> repos = new ArrayList<>();
                    try {
                        Document doc = Jsoup.parse(s, AppConfig.GITHUB_BASE_URL);
                        Elements elements = doc.getElementsByClass("Box-row");
                        if(elements.size() != 0){
                            for (Element element : elements) {
                                try{
                                    repos.add(parseTrendingRepositoryData(element));
                                } catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception e){
                        throw Exceptions.propagate(e);
                    }

                    return repos;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    if(mView == null) return;
                    if(results != null){
                        repos = results;
                        mView.hideLoading();
                        mView.showRepositories(repos);
                    } else {
                        String errorTip = String.format(getString(R.string.github_page_parse_error),
                                getString(R.string.trending));
                        mView.showLoadError(errorTip);
                        mView.hideLoading();
                    }
                }, error -> {
                    if(mView == null) return;
                    mView.showLoadError(String.format(getString(R.string.github_page_parse_error),
                            getString(R.string.trending)));
                    mView.hideLoading();
                });
    }

    private Repository parseTrendingRepositoryData(Element element) throws Exception{
        TrendingParser.Item item = TrendingParser.parse(element);
        if (item == null) throw new IllegalStateException("Trending row without a repository link");

        Repository repo = new Repository();
        repo.setFullName(item.fullName);
        repo.setName(item.name);
        User user = new User();
        user.setLogin(item.owner);
        repo.setOwner(user);

        repo.setDescription(item.description.replaceAll("\n", ""));
        repo.setStargazersCount(item.stars);
        repo.setForksCount(item.forks);
        repo.setSinceStargazersCount(item.periodStars);
        repo.setLanguage(item.language);
        repo.setSince(since);

        return repo;
    }

    public TrendingLanguage getLanguage() {
        return language;
    }
}
