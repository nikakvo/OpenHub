package com.thirtydegreesray.openhub.mvp.presenter;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IGistsContract;
import com.thirtydegreesray.openhub.mvp.model.Gist;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Gists of a user, newest first. For the logged in user the list comes from /gists,
 * which includes secret gists.
 */
public class GistsPresenter extends BasePresenter<IGistsContract.View>
        implements IGistsContract.Presenter {

    @AutoAccess String user;

    private ArrayList<Gist> gists;

    @Inject
    public GistsPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if (gists == null) {
            loadGists(1, false);
        } else {
            mView.showGists(gists);
            mView.hideLoading();
        }
    }

    private boolean isMe() {
        return AppData.INSTANCE.getLoggedUser() != null
                && AppData.INSTANCE.getLoggedUser().getLogin().equalsIgnoreCase(user);
    }

    @Override
    public void loadGists(final int page, final boolean isReload) {
        mView.showLoading();
        final boolean readCacheFirst = page == 1 && !isReload;
        HttpObserver<ArrayList<Gist>> observer = new HttpObserver<ArrayList<Gist>>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
                if (!StringUtils.isBlankList(gists) && page > 1) {
                    mView.showErrorToast(getErrorTip(error));
                } else if (error instanceof HttpPageNoFoundError) {
                    gists = new ArrayList<>();
                    mView.showGists(gists);
                } else {
                    mView.showLoadError(getErrorTip(error));
                }
            }

            @Override
            public void onSuccess(HttpResponse<ArrayList<Gist>> response) {
                mView.hideLoading();
                ArrayList<Gist> pageGists = response.body() == null ? new ArrayList<>() : response.body();
                if (isReload || gists == null || readCacheFirst || page == 1) {
                    gists = pageGists;
                } else {
                    gists.addAll(pageGists);
                }
                if (pageGists.size() == 0 && gists.size() != 0) {
                    mView.setCanLoadMore(false);
                } else {
                    mView.showGists(gists);
                }
            }
        };
        final boolean me = isMe();
        generalRxHttpExecute(forceNetWork -> me
                ? getUserService().getMyGists(forceNetWork, page)
                : getUserService().getUserGists(forceNetWork, user, page), observer, readCacheFirst);
    }

    @Override
    public void openGist(@NonNull Gist gist) {
        mView.showProgressDialog(getLoadTip());
        HttpObserver<Gist> observer = new HttpObserver<Gist>() {
            @Override
            public void onError(Throwable error) {
                mView.dismissProgressDialog();
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<Gist> response) {
                mView.dismissProgressDialog();
                mView.showGistFiles(response.body() == null ? gist : response.body());
            }
        };
        generalRxHttpExecute(forceNetWork -> getUserService().getGist(true, gist.getId()), observer);
    }

    public String getUser() {
        return user;
    }
}
