

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IRepoInfoContract;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePagerPresenter;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.IOException;

import javax.inject.Inject;

import okhttp3.ResponseBody;
import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/11 11:34:31
 */

public class RepoInfoPresenter extends BasePagerPresenter<IRepoInfoContract.View>
        implements IRepoInfoContract.Presenter{

    @AutoAccess Repository repository;
    @AutoAccess String curBranch = "";
    private String readmeSource;
    private boolean readmeLoading;

    @Inject
    public RepoInfoPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
    }

    @Override
    protected void loadData() {
        mView.showRepoInfo(repository);
        if(readmeSource == null && !readmeLoading){
            loadReadMe();
        }
    }

    @Override
    public void loadReadMe() {
        loadReadMe(true);
    }

    @Override
    public void reloadReadMe() {
        readmeSource = null;
        loadReadMe(false);
    }

    private void loadReadMe(final boolean readCacheFirst) {
        final String readmeFileUrl = AppConfig.GITHUB_API_BASE_URL + "repos/" + repository.getFullName()
                + "/" + "readme" + (StringUtils.isBlank(curBranch) ? "" : "?ref=" + curBranch);

        String branch = StringUtils.isBlank(curBranch) ? repository.getDefaultBranch() : curBranch;
        final String baseUrl = AppConfig.GITHUB_BASE_URL + repository.getFullName()
                + "/blob/" + branch  + "/" + "README.md";

//        if(!StringUtils.isBlank(readmeSource)){
//            mView.showReadMe(readmeSource, baseUrl);
//            return;
//        }

        mView.showReadMeLoader();
        readmeLoading = true;
        HttpObserver<ResponseBody> httpObserver = new HttpObserver<ResponseBody>() {
            @Override
            public void onError(Throwable error) {
                readmeLoading = false;
                if(error instanceof HttpPageNoFoundError){
                    mView.showNoReadMe();
                } else {
                    mView.showErrorToast(getErrorTip(error));
                }
            }

            @Override
            public void onSuccess(HttpResponse<ResponseBody> response) {
                readmeLoading = false;
                try {
                    readmeSource = response.body().string();
                    mView.showReadMe(readmeSource, baseUrl);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        generalRxHttpExecute(new IObservableCreator<ResponseBody>() {
            @Override
            public Observable<Response<ResponseBody>> createObservable(boolean forceNetWork) {
                return getRepoService().getFileAsHtmlStream(forceNetWork, readmeFileUrl);
            }
        }, httpObserver, readCacheFirst);

    }

    public Repository getRepository() {
        return repository;
    }

    /** The branch the README is shown for. */
    public String getBranch() {
        return StringUtils.isBlank(curBranch) ? repository.getDefaultBranch() : curBranch;
    }

    /**
     * check if the string size is too large to save
     */
    private void checkReadmeSourceSize(){
        if(readmeSource != null && readmeSource.getBytes().length > 128 * 1024){
            readmeSource = null;
        }
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setCurBranch(String curBranch) {
        this.curBranch = curBranch;
        readmeSource = null;
    }
}
