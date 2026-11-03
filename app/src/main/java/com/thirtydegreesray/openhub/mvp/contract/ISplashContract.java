

package com.thirtydegreesray.openhub.mvp.contract;

import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;

/**
 * Created on 2017/7/12.
 *
 * @author ThirtyDegreesRay
 */

public interface ISplashContract{

    interface View extends IBaseContract.View{
        void showMainPage();

        /** The account could not be checked (no network): offer to try again. */
        void showRetry(String message);

        /**
         * The saved sign-in of {@code login} is not accepted (or, when {@code unreadable},
         * cannot be read on this phone): offer to sign in again; the account is kept.
         */
        void showSignInAgain(@Nullable String login, boolean unreadable);
    }

    interface Presenter extends IBaseContract.Presenter<ISplashContract.View>{

        void getUser();

    }

}
