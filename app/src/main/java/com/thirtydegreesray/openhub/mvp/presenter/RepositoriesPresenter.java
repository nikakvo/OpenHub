

package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orhanobut.logger.Logger;
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

import org.greenrobot.eventbus.Subscribe;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

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
                } else {
                    mView.showRepositories(repos);
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
        String fullName = element.select("h2 > a").attr("href");
        fullName = fullName.substring(1);
        String owner = fullName.substring(0, fullName.lastIndexOf("/"));
        String repoName = fullName.substring(fullName.lastIndexOf("/") + 1);

        Element descElement = element.getElementsByClass("col-9 color-fg-muted my-1 pr-4").first();
        Element numElement = element.getElementsByClass("f6 color-fg-muted mt-2").first();
        StringBuilder desc = new StringBuilder();
        String language = "unknown";
        String starNumStr = "0";
        String forkNumStr = "0";
        String periodNumStr = "0";

        try{
            if(null != descElement){
                for(TextNode textNode : descElement.textNodes()){
                    desc.append(textNode.getWholeText());
                }
            }

            if(null != numElement){
                Elements languageElements = numElement.select("span > span");
                if(null != languageElements && languageElements.size() > 0){
                    language = numElement.select("span > span").get(1).textNodes().get(0).toString().trim();
                }

                for (Element e : numElement.getElementsByClass("Link Link--muted d-inline-block mr-3")) {
                    if (e.attr("href").endsWith("stargazers")) {
                        starNumStr = e.textNodes().get(0).toString()
                                .replaceAll(" ", "").replaceAll(",", "");
                    } else if (e.attr("href").endsWith("forks")) {
                        forkNumStr = e.textNodes().get(0).toString()
                                .replaceAll(" ", "").replaceAll(",", "");
                    }
                }

                Element periodElement =  numElement.getElementsByClass("d-inline-block float-sm-right").first();
                if(periodElement != null){
                    periodNumStr = periodElement.childNodes().get(2).toString().trim();
                    periodNumStr = periodNumStr.substring(0, periodNumStr.indexOf(" "))
                            .replaceAll(",", "");
                }
            }
        }catch (Exception e){
            desc = new StringBuilder("desc parse error.");
            Logger.e("Trending repo desc or num info parse error.", e);
        }

        Repository repo = new Repository();
        repo.setFullName(fullName);
        repo.setName(repoName);
        User user = new User();
        user.setLogin(owner);
        repo.setOwner(user);

        repo.setDescription(desc.toString().trim()
                .replaceAll("\n", ""));
        repo.setStargazersCount(Integer.parseInt(starNumStr));
        repo.setForksCount(Integer.parseInt(forkNumStr));
        repo.setSinceStargazersCount(Integer.parseInt(periodNumStr));
        repo.setLanguage(language);
        repo.setSince(since);

        return repo;
    }

    public TrendingLanguage getLanguage() {
        return language;
    }
}
