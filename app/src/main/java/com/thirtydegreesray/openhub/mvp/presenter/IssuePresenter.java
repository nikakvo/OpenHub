package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IIssuesContract;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.SearchResult;
import com.thirtydegreesray.openhub.mvp.model.filter.IssuesFilter;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePagerPresenter;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;

import javax.inject.Inject;

import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/9/20 14:56:49
 */

public class IssuePresenter extends BasePagerPresenter<IIssuesContract.View>
        implements IIssuesContract.Presenter {

    @AutoAccess IssuesFilter issuesFilter;
    @AutoAccess String userId;
    @AutoAccess String repoName;

    private ArrayList<Issue> issues;

    @Inject
    public IssuePresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
    }

    @Override
    protected void loadData() {
        loadIssues(1, false);
    }

    @Override
    public void loadIssues(final int page, final boolean isReload) {
        boolean readCacheFirst = page == 1 && !isReload;
        if(issuesFilter.getType().equals(IssuesFilter.Type.Repo)){
            loadRepoIssues(page, isReload, readCacheFirst);
        } else {
            loadUserIssues(page, isReload, readCacheFirst);
        }

    }

    @Override
    public void loadIssues(IssuesFilter issuesFilter, int page, boolean isReload) {
        this.issuesFilter = issuesFilter;
        setLoaded(false);
        prepareLoadData();
    }

    private void loadUserIssues(final int page, final boolean isReload, final boolean readCacheFirst){

//        HttpSubscriber<ResponseBody> subscriber = new HttpSubscriber<ResponseBody>(
//                new HttpObserver<ResponseBody>() {
//                    @Override
//                    public void onError(Throwable error) {
//                        error.toString();
//                    }
//
//                    @Override
//                    public void onSuccess(HttpResponse<ResponseBody> response) {
//                        response.body();
//                    }
//                }
//        );
//        generalRxHttpExecute(getSearchService().searchIssues(false, "state:open", "created", "desc", page), subscriber);

        mView.showLoading();
        HttpObserver<SearchResult<Issue>> httpObserver =
                new HttpObserver<SearchResult<Issue>>() {
                    @Override
                    public void onError(Throwable error) {
                        mView.hideLoading();
                        handleError(error);
                    }

                    @Override
                    public void onSuccess(HttpResponse<SearchResult<Issue>> response) {
                        mView.hideLoading();
                        handleSuccess(response.body().getItems(), isReload, readCacheFirst);
                    }
                };
        final String sortStr = issuesFilter.getSortType().name().toLowerCase();
        final String sortDirectionStr = issuesFilter.getSortDirection().name().toLowerCase();
        final String queryStr = getUserQueryStr();

        generalRxHttpExecute(new IObservableCreator<SearchResult<Issue>>() {
            @Override
            public Observable<Response<SearchResult<Issue>>> createObservable(boolean forceNetWork) {
                return getSearchService().searchIssues(forceNetWork, queryStr, sortStr, sortDirectionStr, page);
            }
        }, httpObserver, readCacheFirst);
    }

    /**
     * GET repos/o/r/issues returns pull requests too. They are left out of the issue lists
     * (milestones keep them: "Issues and pull requests"). A page that loses most of its items
     * would end the list early or look empty, so the next pages of GitHub are read at once until
     * enough issues are there; GitHub's page to go on with is kept here.
     */
    private static final int GITHUB_PAGE_SIZE = 30;
    private static final int MIN_ISSUES_PER_LOAD = 10;
    private static final int MAX_PAGES_PER_LOAD = 20;
    private int nextGitHubPage = 1;

    private boolean keepsPullRequests() {
        return issuesFilter.getMilestone() != null;
    }

    private void loadRepoIssues(final int page, final boolean isReload, final boolean readCacheFirst){
        mView.showLoading();
        HttpObserver<ArrayList<Issue>> httpObserver =
                new HttpObserver<ArrayList<Issue>>() {
                    @Override
                    public void onError(Throwable error) {
                        mView.hideLoading();
                        handleError(error);
                    }

                    @Override
                    public void onSuccess(HttpResponse<ArrayList<Issue>> response) {
                        mView.hideLoading();
                        handleSuccess(response.body(), isReload, readCacheFirst);
                    }
                };
        final String statusStr = issuesFilter.getIssueState().name().toLowerCase();
        final String sortStr = issuesFilter.getSortType().name().toLowerCase();
        final String sortDirectionStr = issuesFilter.getSortDirection().name().toLowerCase();
        final int firstGitHubPage = page == 1 ? 1 : nextGitHubPage;
        final boolean keepPulls = keepsPullRequests();

        generalRxHttpExecute(new IObservableCreator<ArrayList<Issue>>() {
            @Override
            public Observable<Response<ArrayList<Issue>>> createObservable(boolean forceNetWork) {
                return repoIssuesFrom(forceNetWork, statusStr, sortStr, sortDirectionStr,
                        firstGitHubPage, keepPulls, new ArrayList<>(), null, 0);
            }
        }, httpObserver, readCacheFirst);
    }

    /**
     * Reads GitHub's page {@code gitHubPage} and, while fewer than MIN_ISSUES_PER_LOAD issues are
     * collected and GitHub has more, the following ones. The answer carries the first page's
     * response (cache or network, used by the cache-first reload) with all collected issues.
     */
    private Observable<Response<ArrayList<Issue>>> repoIssuesFrom(final boolean forceNetWork,
            final String statusStr, final String sortStr, final String sortDirectionStr,
            final int gitHubPage, final boolean keepPulls, final ArrayList<Issue> collected,
            final Response<ArrayList<Issue>> first, final int pagesRead) {
        return getIssueService().getRepoIssues(forceNetWork, userId, repoName, statusStr,
                sortStr, sortDirectionStr, gitHubPage, issuesFilter.getMilestone())
                .flatMap(response -> {
                    Response<ArrayList<Issue>> head = first == null ? response : first;
                    if (!response.isSuccessful() || response.body() == null) {
                        // the first page failing is the screen's error; a later one ends this load
                        if (first == null) return Observable.just(response);
                        nextGitHubPage = gitHubPage;
                        return Observable.just(Response.success(collected, head.raw()));
                    }
                    ArrayList<Issue> items = response.body();
                    for (Issue issue : items) {
                        if (keepPulls || !issue.isPullRequest()) collected.add(issue);
                    }
                    nextGitHubPage = gitHubPage + 1;
                    boolean gitHubHasMore = items.size() >= GITHUB_PAGE_SIZE;
                    if (gitHubHasMore && collected.size() < MIN_ISSUES_PER_LOAD
                            && pagesRead + 1 < MAX_PAGES_PER_LOAD) {
                        return repoIssuesFrom(forceNetWork, statusStr, sortStr, sortDirectionStr,
                                gitHubPage + 1, keepPulls, collected, head, pagesRead + 1);
                    }
                    return Observable.just(Response.success(collected, head.raw()));
                });
    }

    private void handleError(Throwable error){
        if(!StringUtils.isBlankList(issues)){
            mView.showErrorToast(getErrorTip(error));
        } else if(error instanceof HttpPageNoFoundError){
            mView.showIssues(new ArrayList<Issue>());
        }else{
            mView.showLoadError(getErrorTip(error));
        }
    }

    private void handleSuccess(ArrayList<Issue> resultIssues, boolean isReload, boolean readCacheFirst){
        if (isReload || issues == null || readCacheFirst) {
            issues = resultIssues;
        } else {
            issues.addAll(resultIssues);
        }
        if (resultIssues.size() == 0 && issues.size() != 0) {
            mView.setCanLoadMore(false);
        } else {
            mView.setCanLoadMore(resultIssues.size() > 0);
            mView.showIssues(issues);
        }
    }

    public IssuesFilter getIssuesFilter() {
        return issuesFilter;
    }

    public String getUserQueryStr(){
        String queryStr = "";
        String action = "";
        switch (issuesFilter.getUserIssuesFilterType()){
            case All:
                action = "involves";
                break;
            case Created:
                action = "author";
                break;
            case Assigned:
                action = "assignee";
                break;
            case Mentioned:
                action = "mentions";
                break;
        }
        // without is:issue the issue search returns pull requests too
        queryStr = queryStr + action + ":" + AppData.INSTANCE.getLoggedUser().getLogin()
                + "+" + "state:" + issuesFilter.getIssueState().name().toLowerCase()
                + "+is:issue";
        return queryStr;
    }

}
