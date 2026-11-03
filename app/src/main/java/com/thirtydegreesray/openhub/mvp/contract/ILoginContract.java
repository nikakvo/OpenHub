

package com.thirtydegreesray.openhub.mvp.contract;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.BasicToken;

/**
 * Created on 2017/7/12.
 *
 * @author ThirtyDegreesRay
 */

public interface ILoginContract {

    interface View extends IBaseContract.View{

        void onGetTokenSuccess(BasicToken basicToken);

        void onGetTokenError(String errorMsg);

        void onLoginComplete();

    }

    interface Presenter extends IBaseContract.Presenter<ILoginContract.View>{

        /** @param codeVerifier the PKCE verifier of the sign-in that was started here */
        void getToken(String code, String codeVerifier);

        String getOAuth2Url();

        /** The account to suggest on GitHub's sign-in page ("Sign in again"), or null. */
        void setLoginHint(@Nullable String login);

        void basicLogin(String userName, String password);

        void handleOauth(Intent intent);

        void getUserInfo(BasicToken basicToken);

        void loginWithToken(String token);

        String getCreateTokenUrl();

    }

}
