

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.SignOut;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.mvp.contract.ISettingsContract;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;

import javax.inject.Inject;

/**
 * Created on 2017/8/1.
 *
 * @author ThirtyDegreesRay
 */

public class SettingsPresenter extends BasePresenter<ISettingsContract.View>
        implements ISettingsContract.Presenter{

    @Inject
    public SettingsPresenter(AppDatabase db) {
        super(db);
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
        mView.showLoginPage();
    }

}
