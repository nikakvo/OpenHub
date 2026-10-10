package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.GistsFragment;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

public class GistsActivity extends SingleFragmentActivity<IBaseContract.Presenter, GistsFragment> {

    public static void show(@NonNull Activity activity, @NonNull String user) {
        activity.startActivity(new Intent(activity, GistsActivity.class)
                .putExtras(BundleHelper.builder().put("user", user).build()));
    }

    @AutoAccess String user;

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarTitle(getString(R.string.gists), user);
    }

    @Override
    protected GistsFragment createFragment() {
        return GistsFragment.create(user);
    }
}
