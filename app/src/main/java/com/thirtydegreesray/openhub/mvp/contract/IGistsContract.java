package com.thirtydegreesray.openhub.mvp.contract;

import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.Gist;

import java.util.ArrayList;

public interface IGistsContract {

    interface View extends IBaseContract.View {
        void showGists(ArrayList<Gist> gists);

        void setCanLoadMore(boolean canLoadMore);

        void showLoadError(String error);
    }

    interface Presenter extends IBaseContract.Presenter<IGistsContract.View> {
        void loadGists(int page, boolean isReload);
    }
}
