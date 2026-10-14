

package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IActivityContract;
import com.thirtydegreesray.openhub.mvp.model.ActivityRedirectionModel;
import com.thirtydegreesray.openhub.mvp.model.CommitsComparison;
import com.thirtydegreesray.openhub.mvp.model.Event;
import com.thirtydegreesray.openhub.mvp.model.EventPayload;
import com.thirtydegreesray.openhub.mvp.model.PushEventCommit;
import com.thirtydegreesray.openhub.mvp.model.RepoCommit;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePagerPresenter;
import com.thirtydegreesray.openhub.ui.fragment.ActivityFragment;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;

import javax.inject.Inject;

import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/23 21:53:51
 */

public class ActivityPresenter extends BasePagerPresenter<IActivityContract.View>
        implements IActivityContract.Presenter{

    @AutoAccess ActivityFragment.ActivityType type ;
    @AutoAccess String user ;
    @AutoAccess String repo ;

    ArrayList<Event> events;

    @Inject
    public ActivityPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
    }

    @Override
    protected void loadData() {
        if(events != null){
            mView.showEvents(events);
            mView.hideLoading();
            return;
        }
        loadEvents(false, 1);
    }

    @Override
    public void loadEvents(final boolean isReload, final int page) {
        mView.showLoading();
        final boolean readCacheFirst = !isReload && page == 1;
        HttpObserver<ArrayList<Event>> httpObserver = new HttpObserver<ArrayList<Event>>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
                if(!StringUtils.isBlankList(events)){
                    mView.showErrorToast(getErrorTip(error));
                } else if(error instanceof HttpPageNoFoundError){
                    mView.showEvents(new ArrayList<Event>());
                }else{
                    mView.showLoadError(getErrorTip(error));
                }
            }

            @Override
            public void onSuccess(HttpResponse<ArrayList<Event>> response) {
                mView.hideLoading();
                correctEvent(response.body());
                if(events == null || isReload || readCacheFirst){
                    events = response.body();
                } else {
                    events.addAll(response.body());
                }
                if(response.body().size() == 0 && events.size() != 0){
                    mView.setCanLoadMore(false);
                } else {
                    for (Event e: events) {
                        if (e.getType() == Event.EventType.PullRequestEvent && e.getRepo() != null) {
                            continue;
                        } else if (e.getType() == Event.EventType.PushEvent && e.getRepo() != null) {
                            EventPayload payload = e.getPayload();
                            if (payload.getCommits() != null)
                                continue;

                            generalRxHttpExecute(new IObservableCreator<CommitsComparison>() {
                                @Override
                                public Observable<Response<CommitsComparison>> createObservable(boolean forceNetWork) {
                                    return getCommitService().compareTwoCommits(forceNetWork, user, e.getRepo().getName(), payload.getBefore(), payload.getHead());
                                }
                            }, new HttpObserver<CommitsComparison>() {
                                @Override
                                public void onError(Throwable error) {
                                    payload.setCommits(null);
                                }

                                @Override
                                public void onSuccess(HttpResponse<CommitsComparison> response) {
                                    CommitsComparison compare = response.body();
                                    if (compare == null) return;
                                    ArrayList<PushEventCommit> commits = new ArrayList<>(compare.getTotalCommits());

                                    for (RepoCommit commit: compare.getCommits()) {
                                        PushEventCommit pushCommit = new PushEventCommit();
                                        pushCommit.setAuthor(commit.getAuthor());
                                        pushCommit.setSha(commit.getSha());
                                        pushCommit.setUrl(commit.getCommit().getUrl());
                                        pushCommit.setMessage(commit.getCommit().getMessage());
                                        commits.add(pushCommit);
                                    }

                                    payload.setCommits(commits);
                                    mView.showEvents(events);
                                }
                            }, readCacheFirst);
                        }
                    }

                    mView.showEvents(events);
                }
            }
        };
        generalRxHttpExecute(new IObservableCreator<ArrayList<Event>>() {
            @Override
            public Observable<Response<ArrayList<Event>>> createObservable(boolean forceNetWork) {
                return getObservable(forceNetWork, page);
            }
        }, httpObserver, readCacheFirst);
    }

    @Override
    public ArrayList<ActivityRedirectionModel> getRedirectionList(@NonNull Event event) {
        ArrayList<ActivityRedirectionModel> list = new ArrayList<>();
        list.add(ActivityRedirectionModel.generateForUser(event));
        list.add(ActivityRedirectionModel.generateForRepo(event));
        switch (event.getType()){
            case ForkEvent:
                list.add(ActivityRedirectionModel.generateForFork(event));
                break;
            case ReleaseEvent:
                list.add(ActivityRedirectionModel.generateRepoInfo(event, ActivityRedirectionModel.Type.Releases));
                list.add(ActivityRedirectionModel.generateForRelease(event));
                break;
            case IssueCommentEvent:
            case IssuesEvent:
                list.add(ActivityRedirectionModel.generateRepoInfo(event, ActivityRedirectionModel.Type.Issues));
                list.add(ActivityRedirectionModel.generateForIssues(event));
                break;
            case PushEvent:
                list.add(ActivityRedirectionModel.generateForCommits(event));
                if(event.getPayload().getCommits() == null) break;
                if(event.getPayload().getCommits().size() != 1){
                    list.add(ActivityRedirectionModel.generateForCommitCompare(event));
                }
                for(int i = 0; i < event.getPayload().getCommits().size(); i++){
                    list.add(ActivityRedirectionModel.generateForCommit(event, i));
                }
                break;
        }
        return list;
    }

    private Observable<Response<ArrayList<Event>>> getObservable(boolean forceNetWork, int page){
        if(type.equals(ActivityFragment.ActivityType.News)){
            return getUserService().getNewsEvent(forceNetWork, user, page);
        } else if(type.equals(ActivityFragment.ActivityType.User)){
            return getUserService().getUserEvents(forceNetWork, user, page);
        } else if(type.equals(ActivityFragment.ActivityType.Repository)){
            return getRepoService().getRepoEvent(forceNetWork, user, repo, page);
        } else if(type.equals(ActivityFragment.ActivityType.PublicNews)){
            return getUserService().getPublicEvent(forceNetWork, page);
        } else {
            return null;
        }
    }

    private void correctEvent(ArrayList<Event> events){
        for(Event event : events){
            if(event.getActor() != null) event.getActor().setType(User.UserType.User);
            if(event.getOrg() != null) event.getOrg().setType(User.UserType.Organization);
            if(event.getRepo() != null){
                String fullName = event.getRepo().getName();
                event.getRepo().setFullName(fullName);
                event.getRepo().setName(fullName.substring(fullName.indexOf("/") + 1));
            }
        }
    }

}

