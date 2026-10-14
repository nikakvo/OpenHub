package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.IReleasesContract;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.util.ReleaseChanges;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;

import java.util.ArrayList;

import javax.inject.Inject;

import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/9/16 11:31:07
 */

public class ReleasesPresenter extends BasePresenter<IReleasesContract.View>
        implements IReleasesContract.Presenter{

    @AutoAccess String owner;
    @AutoAccess String repo;
    @AutoAccess boolean canEdit;
    private ArrayList<Release> releases;
    private long loadedStamp;

    @Inject
    public ReleasesPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if(releases == null){
            loadReleases(1, false);
        } else {
            mView.showReleases(releases);
            mView.hideLoading();
        }
        if (canEdit) mView.onPermissionChecked(true);
        checkPermission();
    }

    /** Reloads when a release was created, edited or deleted on another screen. */
    public void onResume() {
        if (ReleaseChanges.changedSince(loadedStamp)) loadReleases(1, true);
    }

    private void checkPermission() {
        generalRxHttpExecute(new IObservableCreator<Repository>() {
            @Override
            public Observable<Response<Repository>> createObservable(boolean forceNetWork) {
                return getRepoService().getRepoInfo(forceNetWork, owner, repo);
            }
        }, new HttpObserver<Repository>() {
            @Override
            public void onError(Throwable error) {
            }

            @Override
            public void onSuccess(HttpResponse<Repository> response) {
                boolean edit = ReleaseInfoPresenter.hasPushAccess(response.body());
                if (edit != canEdit) {
                    canEdit = edit;
                    mView.onPermissionChecked(canEdit);
                }
            }
        }, true);
    }

    @Override
    public void loadReleases(final int page, final boolean isReload) {
        mView.showLoading();
        final boolean readCacheFirst = page == 1 && !isReload;
        final HttpObserver<ArrayList<Release>> httpObserver = new HttpObserver<ArrayList<Release>>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
            }

            @Override
            public void onSuccess(HttpResponse<ArrayList<Release>> response) {
                mView.hideLoading();
                if (page == 1) loadedStamp = ReleaseChanges.stamp();
                if (isReload || releases == null || readCacheFirst) {
                    releases = response.body();
                } else {
                    releases.addAll(response.body());
                }
                if(response.body().size() == 0 && releases.size() != 0){
                    mView.setCanLoadMore(false);
                } else {
                    mView.showReleases(releases);
                }
            }
        };

        generalRxHttpExecute(new IObservableCreator<ArrayList<Release>>() {
            @Override
            public Observable<Response<ArrayList<Release>>> createObservable(boolean forceNetWork) {
                return getRepoService().getReleases(forceNetWork, owner, repo, page);
            }
        }, httpObserver, readCacheFirst);

    }

    public String getRepoName(){
        return repo;
    }

    public String getOwner() {
        return owner;
    }

    public boolean canEdit() {
        return canEdit;
    }
}
