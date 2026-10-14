package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.dao.LocalRepo;
import com.thirtydegreesray.openhub.dao.LocalUser;
import com.thirtydegreesray.openhub.dao.Trace;
import com.thirtydegreesray.openhub.mvp.contract.ITraceContract;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.TraceExt;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by ThirtyDegreesRay on 2017/11/23 10:55:30
 */

public class TracePresenter extends BasePresenter<ITraceContract.View>
        implements ITraceContract.Presenter {

    private ArrayList<TraceExt> traceList;
    private TraceExt removedTrace;
    private int removedPosition;

    private static final int PAGE_SIZE = 30;

    @Inject
    public TracePresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        loadTraceList(1);
    }

    @Override
    public void loadTraceList(int page) {
        mView.showLoading();
        final ArrayList<TraceExt> tempTraceList = new ArrayList<>();
        Completable.fromAction(() -> db.runInTransaction(() -> {
                    List<Trace> traceListDb =
                            db.traceDao().page((page - 1) * PAGE_SIZE, PAGE_SIZE);
                    for (Trace trace : traceListDb) {
                        TraceExt ext = TraceExt.generate(trace);
                        if ("user".equals(trace.getType())) {
                            LocalUser localUser = db.localUserDao().load(ext.getUserId());
                            if (localUser == null) continue; // no local copy: cannot be shown
                            ext.setUser(User.generateFromLocalUser(localUser));
                        } else {
                            LocalRepo localRepo = ext.getRepoId() == null ? null
                                    : db.localRepoDao().load(ext.getRepoId());
                            if (localRepo == null) continue;
                            ext.setRepository(Repository.generateFromLocalRepo(localRepo));
                        }
                        tempTraceList.add(ext);
                    }
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    if (mView == null) return;
                    if (traceList == null || page == 1) {
                        traceList = tempTraceList;
                    } else {
                        traceList.addAll(tempTraceList);
                    }
                    mView.showTraceList(traceList);
                    mView.hideLoading();
                }, error -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    mView.showErrorToast(getErrorTip(error));
                });
    }

    @Override
    public void removeTrace(int position) {
        removedTrace = traceList.remove(position);
        removedPosition = position;
        rxDBExecute(() -> db.traceDao().deleteById(removedTrace.getId()));
    }

    @Override
    public void undoRemoveTrace() {
        traceList.add(removedPosition, removedTrace);
        mView.notifyItemAdded(removedPosition);
        rxDBExecute(() -> db.traceDao().insert(removedTrace));
    }
    @Override
    public int getFirstItemByDate(long dateTime) {
        for (TraceExt trace : traceList) {
            if (trace.getLatestDate().getTime() == dateTime) {
                return traceList.indexOf(trace);
            }
        }
        return 0;
    }

}
