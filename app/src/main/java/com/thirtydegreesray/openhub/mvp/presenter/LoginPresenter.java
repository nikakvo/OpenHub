

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.OAuthSession;
import com.thirtydegreesray.openhub.http.core.AppRetrofit;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.core.HttpSubscriber;
import com.thirtydegreesray.openhub.http.model.AuthRequestModel;
import com.thirtydegreesray.openhub.mvp.contract.ILoginContract;
import com.thirtydegreesray.openhub.mvp.model.BasicToken;
import com.thirtydegreesray.openhub.mvp.model.OauthToken;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;

import javax.inject.Inject;

import okhttp3.Credentials;
import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created on 2017/7/12.
 *
 * @author ThirtyDegreesRay
 */

public class LoginPresenter extends BasePresenter<ILoginContract.View>
        implements ILoginContract.Presenter {

    @Inject
    public LoginPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void getToken(String code, String codeVerifier) {
        Observable<Response<OauthToken>> observable =
                getLoginService().getAccessToken(AppConfig.OPENHUB_CLIENT_ID,
                        AppConfig.OPENHUB_CLIENT_SECRET, code, codeVerifier);

        HttpSubscriber<OauthToken> subscriber =
                new HttpSubscriber<>(
                        new HttpObserver<OauthToken>() {
                            @Override
                            public void onError(@NonNull Throwable error) {
                                mView.dismissProgressDialog();
                                mView.showErrorToast(getErrorTip(error));
                            }

                            @Override
                            public void onSuccess(@NonNull HttpResponse<OauthToken> response) {
                                OauthToken token = response.body();
                                if (token != null) {
                                    mView.onGetTokenSuccess(BasicToken.generateFromOauthToken(token));
                                } else {
                                    mView.onGetTokenError(response.getOriResponse().message());
                                }
                            }
                        }
                );
        generalRxHttpExecute(observable, subscriber);
        mView.showProgressDialog(getLoadTip());
    }

    @NonNull
    @Override
    public String getOAuth2Url() {
        // a new random state and PKCE pair for every sign-in (checked in handleOauth)
        OAuthSession.Start start = OAuthSession.begin(getContext());
        return AppConfig.OAUTH2_URL +
                "?client_id=" + AppConfig.OPENHUB_CLIENT_ID +
                "&scope=" + Uri.encode(AppConfig.OAUTH2_SCOPE) +
                "&state=" + start.state +
                "&code_challenge=" + start.codeChallenge +
                "&code_challenge_method=S256";
    }

    @Override
    public void basicLogin(String userName, String password) {
        AuthRequestModel authRequestModel = AuthRequestModel.generate();
        String token = Credentials.basic(userName, password);
        Observable<Response<BasicToken>> observable =
                getLoginService(token).authorizations(authRequestModel);
        HttpSubscriber<BasicToken> subscriber =
                new HttpSubscriber<>(
                        new HttpObserver<BasicToken>() {
                            @Override
                            public void onError(@NonNull Throwable error) {
//                                mView.dismissProgressDialog();
                                mView.onGetTokenError(getErrorTip(error));
                            }

                            @Override
                            public void onSuccess(@NonNull HttpResponse<BasicToken> response) {
                                BasicToken token = response.body();
                                if (token != null) {
                                    mView.onGetTokenSuccess(token);
                                } else {
                                    mView.onGetTokenError(response.getOriResponse().message());
                                }

                            }
                        }
                );
        generalRxHttpExecute(observable, subscriber);
//        mView.showProgressDialog(getLoadTip());
    }

    @Override
    public void handleOauth(Intent intent) {
        Uri uri = intent.getData();
        if (uri == null) return;
        String error = uri.getQueryParameter("error");
        if (!StringUtils.isBlank(error)) {
            // e.g. access_denied when "Cancel" was tapped on GitHub
            OAuthSession.finish(getContext(), null);
            mView.onGetTokenError(StringUtils.isBlank(uri.getQueryParameter("error_description"))
                    ? error : uri.getQueryParameter("error_description"));
            return;
        }
        String code = uri.getQueryParameter("code");
        // only a sign-in started in OpenHub, with the same state, is accepted
        String verifier = OAuthSession.finish(getContext(), uri.getQueryParameter("state"));
        if (StringUtils.isBlank(code) || verifier == null) {
            mView.onGetTokenError(getString(R.string.oauth_state_mismatch));
            return;
        }
        getToken(code, verifier);
    }

    @Override
    public void getUserInfo(final BasicToken basicToken) {
        HttpSubscriber<User> subscriber = new HttpSubscriber<>(
                new HttpObserver<User>() {
                    @Override
                    public void onError(Throwable error) {
                        mView.dismissProgressDialog();
                        mView.onGetTokenError(getErrorTip(error));
                    }

                    @Override
                    public void onSuccess(HttpResponse<User> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            mView.dismissProgressDialog();
                            int code = response.getOriResponse().code();
                            mView.onGetTokenError(code == 401 || code == 403
                                    ? getString(R.string.token_rejected)
                                    : code + " " + response.getOriResponse().message());
                            return;
                        }
                        saveAuthUser(basicToken, response.body());
                        mView.onLoginComplete();
                    }
                }
        );
        Observable<Response<User>> observable = getUserService(basicToken.getToken()).
                getPersonInfo(true);
        generalRxHttpExecute(observable, subscriber);
        mView.showProgressDialog(getLoadTip());

    }

    @Override
    public void loginWithToken(String token) {
        BasicToken basicToken = new BasicToken();
        basicToken.setToken(token.trim());
        basicToken.setScopes(new ArrayList<String>());
        getUserInfo(basicToken);
    }

    @NonNull
    @Override
    public String getCreateTokenUrl() {
        return AppConfig.GITHUB_BASE_URL + "settings/tokens/new?description=OpenHub&scopes="
                + AppConfig.OAUTH2_SCOPE;
    }

    private void saveAuthUser(BasicToken basicToken, User userInfo) {
        // the new account becomes the selected one; an older row of this login is replaced
        db.authUserDao().unselectAll();
        db.authUserDao().deleteByLogin(userInfo.getLogin());
        AppRetrofit.INSTANCE.clearClients();

        AuthUser authUser = new AuthUser();
        String scope = StringUtils.listToString(basicToken.getScopes(), ",");
        Date date = new Date();
        authUser.setAccessToken(basicToken.getToken());
        authUser.setScope(scope);
        authUser.setAuthTime(date);
        authUser.setExpireIn(360 * 24 * 60 * 60);
        authUser.setSelected(true);
        authUser.setLoginId(userInfo.getLogin());
        authUser.setName(userInfo.getName());
        authUser.setAvatar(userInfo.getAvatarUrl());
        db.authUserDao().insert(authUser);

        AppData.INSTANCE.setAuthUser(authUser);
        AppData.INSTANCE.setLoggedUser(userInfo);
    }


}
