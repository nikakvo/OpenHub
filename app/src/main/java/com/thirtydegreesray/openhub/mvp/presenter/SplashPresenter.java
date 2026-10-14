

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.TokenCrypto;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.http.error.UnauthorizedError;
import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.ISplashContract;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created on 2017/7/12.
 *
 * @author ThirtyDegreesRay
 */

public class SplashPresenter extends BasePresenter<ISplashContract.View>
        implements ISplashContract.Presenter{

    private final String TAG = "SplashPresenter";

    private boolean isMainPageShowwed = false;

    @Inject
    public SplashPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void getUser() {
        // accounts saved by older versions: encrypt their tokens now (once)
        TokenCrypto.migrate(db);

        // the selected account; if none is selected, the first one
        AuthUser selectedUser = db.authUserDao().loadSelectedOrFirst();

        if (selectedUser != null && StringUtils.isBlank(selectedUser.getAccessToken())) {
            // the token cannot be decrypted any more (Keystore keys gone): sign in again
            db.authUserDao().deleteByLogin(selectedUser.getLoginId());
            selectedUser = null;
        }

        if (selectedUser != null && isExpired(selectedUser)) {
            db.authUserDao().delete(selectedUser);
            selectedUser = null;
        }

        if (selectedUser != null) {
            AppData.INSTANCE.setAuthUser(selectedUser);
            getUserInfo(selectedUser.getAccessToken());
        } else {
            mView.showLoginPage();
        }

    }

    private void getUserInfo(final String accessToken) {

        HttpObserver<User> httpObserver = new HttpObserver<User>() {
            @Override
            public void onError(@NonNull Throwable error) {
                if (error instanceof UnauthorizedError) {
                    // GitHub rejected the token (revoked or expired): sign in again
                    db.authUserDao().delete(AppData.INSTANCE.getAuthUser());
                    AppData.INSTANCE.setAuthUser(null);
                    mView.showErrorToast(getErrorTip(error));
                    mView.showLoginPage();
                    return;
                }
                // no network, roaming, GitHub down: the account stays, try again
                AppData.INSTANCE.setAuthUser(null);
                mView.showRetry(getErrorTip(error));
            }

            @Override
            public void onSuccess(@NonNull HttpResponse<User> response) {
                AppData.INSTANCE.setLoggedUser(response.body());
                if(!isMainPageShowwed) {
                    isMainPageShowwed = true;
                    mView.showMainPage();
                }
            }
        };

        generalRxHttpExecute(new IObservableCreator<User>() {
            @Override
            public Observable<Response<User>> createObservable(boolean forceNetWork) {
                return getUserService().getPersonInfo(forceNetWork);
            }
        }, httpObserver, true);

    }

    private boolean isExpired(AuthUser selectedUser){
        return selectedUser.getAuthTime().getTime() + selectedUser.getExpireIn() * 1000
                < System.currentTimeMillis();
    }

}
