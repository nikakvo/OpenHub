

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.SignOut;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.DaoSession;
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
    public SettingsPresenter(DaoSession daoSession) {
        super(daoSession);
    }

    @Override
    public void logout() {
        String token = AppData.INSTANCE.getAccessToken();
        String login = AppData.INSTANCE.getAuthUser() == null ? null : AppData.INSTANCE.getAuthUser().getLoginId();
        daoSession.getAuthUserDao().delete(AppData.INSTANCE.getAuthUser());
        if (login != null) {
            // also by login name, so no row can stay behind whatever form its token has
            daoSession.getAuthUserDao().getDatabase().execSQL("DELETE FROM "
                    + com.thirtydegreesray.openhub.dao.AuthUserDao.TABLENAME + " WHERE "
                    + com.thirtydegreesray.openhub.dao.AuthUserDao.Properties.LoginId.columnName + " = ?",
                    new Object[]{login});
            daoSession.getAuthUserDao().detachAll();
        }
        SignOut.cleanUp(token);
        AppData.INSTANCE.setAuthUser(null);
        AppData.INSTANCE.setLoggedUser(null);
        mView.showLoginPage();
    }

}
