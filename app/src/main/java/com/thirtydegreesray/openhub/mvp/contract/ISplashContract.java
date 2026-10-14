

package com.thirtydegreesray.openhub.mvp.contract;

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
    }

    interface Presenter extends IBaseContract.Presenter<ISplashContract.View>{

        void getUser();

    }

}
