package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.mvp.contract.ITopicsContract;
import com.thirtydegreesray.openhub.mvp.model.Topic;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;


import java.util.ArrayList;

import javax.inject.Inject;

import com.thirtydegreesray.openhub.http.GitHubExplore;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by ThirtyDegreesRay on 2017/12/29 11:10:27
 */

public class TopicsPresenter extends BasePresenter<ITopicsContract.View>
        implements ITopicsContract.Presenter {

    private ArrayList<Topic> topics;

    @Inject
    public TopicsPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        loadTopics(false);
    }

    @Override
    public void loadTopics(boolean isReload) {
        mView.showLoading();
        // Featured topics from the REST search API instead of scraping github.com/topics.
        Observable.just(isReload)
                .map(reload -> {
                    try {
                        return GitHubExplore.getFeaturedTopics();
                    } catch (Exception e) {
                        throw Exceptions.propagate(e);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    topics = results;
                    mView.showTopics(topics);
                }, error -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    mView.showLoadError(getErrorTip(error));
                });
    }

}
