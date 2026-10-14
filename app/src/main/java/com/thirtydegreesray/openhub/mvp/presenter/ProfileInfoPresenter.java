

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.IProfileInfoContract;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePagerPresenter;

import java.util.ArrayList;

import javax.inject.Inject;

import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/23 14:37:51
 */

public class ProfileInfoPresenter extends BasePagerPresenter<IProfileInfoContract.View>
        implements IProfileInfoContract.Presenter{

    @AutoAccess User user;
    private ArrayList<User> orgs;

    @Inject
    public ProfileInfoPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
    }

    @Override
    protected void loadData() {
        mView.showProfileInfo(user);
        if(user.isUser()) loadOrgs();
    }

    public User getUser() {
        return user;
    }

    private void loadOrgs(){
        if(orgs != null && orgs.size() != 0){
            mView.showUserOrgs(orgs);
            return;
        }
        HttpObserver<ArrayList<User>> httpObserver = new HttpObserver<ArrayList<User>>() {
            @Override
            public void onError(Throwable error) {
                mView.showErrorToast(getErrorTip(error));
            }

            @Override
            public void onSuccess(HttpResponse<ArrayList<User>> response) {
                if(response.body().size() != 0){
                    orgs = response.body();
                    mView.showUserOrgs(orgs);
                }
            }
        };
        generalRxHttpExecute(new IObservableCreator<ArrayList<User>>() {
            @Override
            public Observable<Response<ArrayList<User>>> createObservable(boolean forceNetWork) {
                return getUserService().getUserOrgs(forceNetWork, user.getLogin());
            }
        }, httpObserver, true);
    }

    public void setUser(User user) {
        this.user = user;
    }
}
