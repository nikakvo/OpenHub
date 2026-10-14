

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.SignOut;
import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.mvp.contract.IMainContract;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.PrefUtils;

import java.util.List;

import javax.inject.Inject;

/**
 * Created on 2017/7/18.
 *
 * @author ThirtyDegreesRay
 */

public class MainPresenter extends BasePresenter<IMainContract.View>
        implements IMainContract.Presenter{

    @Inject
    public MainPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public boolean isFirstUseAndNoNewsUser() {
        User user = AppData.INSTANCE.getLoggedUser();
        if(user.getFollowing() == 0
                && user.getPublicRepos() == 0 && user.getPublicGists() == 0
                && PrefUtils.isFirstUse()){
            PrefUtils.set(PrefUtils.FIRST_USE, false);
            return true;
        }
        return false;
    }

    @Override
    public List<AuthUser> getLoggedUserList() {
        List<AuthUser> users = db.authUserDao().loadAll();
        if(users != null){
            for(AuthUser user : users){
                if(AppData.INSTANCE.getLoggedUser().getLogin().equals(user.getLoginId())){
                    users.remove(user);
                    break;
                }
            }
        }
        return users;
    }

    @Override
    public void toggleAccount(@NonNull String loginId) {
        db.authUserDao().select(loginId);
        com.thirtydegreesray.openhub.http.core.AppRetrofit.INSTANCE.clearClients();
        AppData.INSTANCE.setAuthUser(null);
        AppData.INSTANCE.setLoggedUser(null);
        mView.restartApp();
    }

    @Override
    public void logout() {
        String token = AppData.INSTANCE.getAccessToken();
        String login = AppData.INSTANCE.getAuthUser() == null ? null : AppData.INSTANCE.getAuthUser().getLoginId();
        db.authUserDao().delete(AppData.INSTANCE.getAuthUser());
        // also by login name, so no row can stay behind whatever form its token has
        if (login != null) db.authUserDao().deleteByLogin(login);
        SignOut.cleanUp(token);
        AppData.INSTANCE.setAuthUser(null);
        AppData.INSTANCE.setLoggedUser(null);
        mView.restartApp();
    }

}
