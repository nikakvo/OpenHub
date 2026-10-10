package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.DaoSession;
import com.thirtydegreesray.openhub.mvp.contract.ICollectionsContract;
import com.thirtydegreesray.openhub.mvp.model.Collection;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;


import java.util.ArrayList;

import javax.inject.Inject;

import com.thirtydegreesray.openhub.http.GitHubExplore;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by ThirtyDegreesRay on 2017/12/25 15:16:34
 */

public class CollectionsPresenter extends BasePresenter<ICollectionsContract.View>
        implements ICollectionsContract.Presenter {

    private ArrayList<Collection> collections;

    @Inject
    public CollectionsPresenter(DaoSession daoSession) {
        super(daoSession);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        loadCollections(false);
    }

    @Override
    public void loadCollections(boolean isReload) {
        mView.showLoading();
        // github/explore is the source of github.com/collections, so this does not
        // break when GitHub redesigns the web page.
        Observable.just(isReload)
                .map(reload -> {
                    try {
                        return GitHubExplore.getCollections(reload);
                    } catch (Exception e) {
                        throw Exceptions.propagate(e);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    collections = results;
                    mView.showCollections(collections);
                }, error -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    mView.showLoadError(getErrorTip(error));
                });
    }

}
