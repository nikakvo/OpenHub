

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.Bookmark;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.util.AccountChanges;
import com.thirtydegreesray.openhub.util.LocalChanges;
import com.thirtydegreesray.openhub.dao.Trace;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpProgressSubscriber;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.IRepositoryContract;
import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.util.StarWishesHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import javax.inject.Inject;

import okhttp3.ResponseBody;
import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/9 21:42:47
 */

public class RepositoryPresenter extends BasePresenter<IRepositoryContract.View>
        implements IRepositoryContract.Presenter {

    @AutoAccess(dataName = "repository") Repository repository;

    @AutoAccess String owner;
    @AutoAccess String repoName;

    private ArrayList<Branch> branches;
    @AutoAccess Branch curBranch;
    private boolean starred;
    private boolean watched;

    private boolean isStatusChecked = false;
    @AutoAccess boolean isTraceSaved = false;

    private boolean isBookmarkQueried = false;
    private boolean bookmarked = false;

    @Inject
    public RepositoryPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if (repository != null) {
            owner = repository.getOwner().getLogin();
            repoName = repository.getName();
            initCurBranch();
            mView.showRepo(repository);
            getRepoInfo(false);
            checkStatus();
        } else {
            getRepoInfo(true);
        }
    }

    private long branchesStamp;

    @Override
    public void loadBranchesAndTags() {
        // a branch was created, renamed or deleted elsewhere in the app: load the list again
        if (branches != null && RepoChanges.changedSince(branchesStamp)) branches = null;
        if (branches != null) {
            mView.showBranchesAndTags(branches, curBranch);
            return;
        }
        HttpProgressSubscriber<ArrayList<Branch>> httpProgressSubscriber =
                new HttpProgressSubscriber<>(
                        mView.getProgressDialog(getLoadTip()),
                        new HttpObserver<ArrayList<Branch>>() {
                            @Override
                            public void onError(Throwable error) {
                                mView.showErrorToast(getErrorTip(error));
                            }

                            @Override
                            public void onSuccess(HttpResponse<ArrayList<Branch>> response) {
                                setTags(response.body());
                                branches.addAll(response.body());
                                mView.showBranchesAndTags(branches, curBranch);
                            }
                        }
                );
        branchesStamp = RepoChanges.stamp();
        Observable<Response<ArrayList<Branch>>> observable = getRepoService().getBranches(owner, repoName)
                .flatMap(arrayListResponse -> {
                    branches = arrayListResponse.body();
                    return getRepoService().getTags(owner, repoName);
                });
        generalRxHttpExecute(observable, httpProgressSubscriber);
    }

    @Override
    public void starRepo(boolean star) {
        starred = star;
        Observable<Response<ResponseBody>> observable = starred ?
                getRepoService().starRepo(owner, repoName) :
                getRepoService().unstarRepo(owner, repoName);
        HttpObserver<ResponseBody> httpObserver = new HttpObserver<ResponseBody>() {
            @Override
            public void onError(Throwable error) {
                if (mView != null) mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<ResponseBody> response) {
                // the Starred list reloads when it is shown again
                AccountChanges.markStars();
            }
        };
        generalRxHttpExecute(forceNetWork -> observable, httpObserver);
    }

    @Override
    public void watchRepo(boolean watch) {
        watched = watch;
        Observable<Response<ResponseBody>> observable = watched ?
                getRepoService().watchRepo(owner, repoName) :
                getRepoService().unwatchRepo(owner, repoName);
        executeSimpleRequest(observable);
    }

    @Override
    public void createFork() {
        mView.getProgressDialog(getLoadTip()).show();
        HttpObserver<Repository> httpObserver = new HttpObserver<Repository>() {
            @Override
            public void onError(Throwable error) {
                mView.showErrorToast(getErrorTip(error));
                mView.getProgressDialog(getLoadTip()).dismiss();
            }

            @Override
            public void onSuccess(HttpResponse<Repository> response) {
                if(response.body() != null) {
                    AccountChanges.markRepos();
                    mView.showSuccessToast(getString(R.string.forked));
                    RepositoryActivity.show(getContext(), response.body());
                } else {
                    mView.showErrorToast(getString(R.string.fork_failed));
                }
                mView.getProgressDialog(getLoadTip()).dismiss();
            }
        };
        generalRxHttpExecute(new IObservableCreator<Repository>() {
            @Override
            public Observable<Response<Repository>> createObservable(boolean forceNetWork) {
                return getRepoService().createFork(owner, repoName);
            }
        }, httpObserver);
    }

    @Override
    public boolean isForkEnable() {
        if(repository != null && !repository.isFork() &&
                !repository.getOwner().getLogin().equals(AppData.INSTANCE.getLoggedUser().getLogin())){
            return true;
        }
        return false;
    }

    private void setTags(ArrayList<Branch> list) {
        for (Branch branch : list) {
            branch.setBranch(false);
        }
    }

    @Override
    public void refreshRepository() {
        if (repository != null) getRepoInfo(false, false);
    }

    private void getRepoInfo(final boolean isShowLoading) {
        getRepoInfo(isShowLoading, true);
    }

    private void getRepoInfo(final boolean isShowLoading, final boolean readCacheFirst) {
        if (isShowLoading) mView.showLoading();
        HttpObserver<Repository> httpObserver =
                new HttpObserver<Repository>() {
                    @Override
                    public void onError(Throwable error) {
                        if (isShowLoading) mView.hideLoading();
                        mView.showErrorToast(getErrorTip(error));
                    }

                    @Override
                    public void onSuccess(HttpResponse<Repository> response) {
                        if (isShowLoading) mView.hideLoading();
                        repository = response.body();
                        initCurBranch();
                        mView.showRepo(repository);
                        checkStatus();
                        saveTrace();
                    }
                };

        generalRxHttpExecute(new IObservableCreator<Repository>() {
            @Override
            public Observable<Response<Repository>> createObservable(boolean forceNetWork) {
                return getRepoService().getRepoInfo(forceNetWork, owner, repoName);
            }
        }, httpObserver, readCacheFirst);
    }

    private void checkStatus(){
        if(isStatusChecked) return;
        isStatusChecked = true;
        checkStarred();
        checkWatched();
    }


    private void checkStarred() {
        checkStatus(
                getRepoService().checkRepoStarred(owner, repoName),
                new CheckStatusCallback() {
                    @Override
                    public void onChecked(boolean status) {
                        starred = status;
                        mView.invalidateOptionsMenu();
                    }
                }
        );
    }

    private void checkWatched() {
        checkStatus(
                getRepoService().checkRepoWatched(owner, repoName),
                new CheckStatusCallback() {
                    @Override
                    public void onChecked(boolean status) {
                        watched = status;
                        mView.invalidateOptionsMenu();
                    }
                }
        );
    }

    private void initCurBranch(){
        curBranch = new Branch(repository.getDefaultBranch());
        curBranch.setZipballUrl("https://github.com/".concat(owner).concat("/")
                .concat(repoName).concat("/archive/")
                .concat(curBranch.getName()).concat(".zip"));
        curBranch.setTarballUrl("https://github.com/".concat(owner).concat("/")
                .concat(repoName).concat("/archive/")
                .concat(curBranch.getName()).concat(".tar.gz"));
    }

    public Repository getRepository() {
        return repository;
    }

    public boolean isFork() {
        return repository != null && repository.isFork();
    }

    public boolean isStarred() {
        return starred;
    }

    public boolean isWatched() {
        return watched;
    }

    public String getZipSourceUrl(){
        return curBranch.getZipballUrl();
    }

    public String getZipSourceName(){
        return repoName.concat("-").concat(curBranch.getName()).concat(".zip");
    }

    public String getTarSourceUrl(){
        return curBranch.getTarballUrl();
    }

    public String getTarSourceName(){
        return repoName.concat("-").concat(curBranch.getName()).concat(".tar.gz");
    }

    public void setCurBranch(Branch curBranch) {
        this.curBranch = curBranch;
    }

    public String getRepoName() {
        return repository == null ? repoName : repository.getName();
    }

    private void starWishes(){
        if(!starred && getString(R.string.author_login_id).equals(owner)
                && getString(R.string.app_name).equals(repoName)
                && StarWishesHelper.isStarWishesTipable()){
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(!starred && mView != null){
                        mView.showStarWishes();
                    }
                }
            }, 3000);
        }
    }

    @Override
    public boolean isBookmarked() {
        if(!isBookmarkQueried && repository != null){
            bookmarked = db.bookmarkDao().findByRepo(repository.getId()) != null;
            isBookmarkQueried = true;
        }
        return bookmarked;
    }

    @Override
    public void bookmark(boolean bookmark) {
        if(repository == null) return;
        bookmarked = bookmark;
        Bookmark bookmarkModel = db.bookmarkDao().findByRepo(repository.getId());
        if(bookmark && bookmarkModel == null){
            bookmarkModel = new Bookmark(UUID.randomUUID().toString());
            bookmarkModel.setType("repo");
            bookmarkModel.setRepoId((long) repository.getId());
            bookmarkModel.setMarkTime(new Date());
            db.bookmarkDao().insert(bookmarkModel);
            LocalChanges.markBookmarks();
        } else if(!bookmark && bookmarkModel != null){
            db.bookmarkDao().delete(bookmarkModel);
            LocalChanges.markBookmarks();
        }
    }

    private void saveTrace(){
        db.runInTransaction(() ->{
            if(!isTraceSaved){
                Trace trace = db.traceDao().findByRepo(repository.getId());

                if(trace == null){
                    trace = new Trace(UUID.randomUUID().toString());
                    trace.setType("repo");
                    trace.setRepoId((long) repository.getId());
                    Date curDate = new Date();
                    trace.setStartTime(curDate);
                    trace.setLatestTime(curDate);
                    trace.setTraceNum(1);
                    db.traceDao().insert(trace);
                } else {
                    trace.setTraceNum((trace.getTraceNum() == null ? 0 : trace.getTraceNum()) + 1);
                    trace.setLatestTime(new Date());
                    db.traceDao().update(trace);
                }
            }

            db.localRepoDao().save(repository.toLocalRepo());
        });
        isTraceSaved = true;
        LocalChanges.markHistory();
    }

}
