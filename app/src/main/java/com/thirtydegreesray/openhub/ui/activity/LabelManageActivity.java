package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.view.View;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.LabelManageFragment;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ViewUtils;


/**
 * Created by ThirtyDegreesRay on 2018/1/11 10:50:09
 */

public class LabelManageActivity extends SingleFragmentActivity<IBaseContract.Presenter, LabelManageFragment> {

    public static void show(@NonNull Activity activity, @NonNull String owner, @NonNull String repo){
        Intent intent = new Intent(activity, LabelManageActivity.class);
        intent.putExtras(BundleHelper.builder().put("owner", owner).put("repo", repo).build());
        activity.startActivity(intent);
    }

    @AutoAccess String owner;
    @AutoAccess String repo;
    FloatingActionButton floatingActionButton;


    @Override
    protected LabelManageFragment createFragment() {
        return LabelManageFragment.create(owner, repo);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarTitle(getString(R.string.labels), owner + "/" + repo);
        floatingActionButton.setVisibility(View.VISIBLE);
        setToolbarScrollAble(true);
    }

    public void onCreateLabelClick(){
        getFragment().onCreateLabelClick();
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        floatingActionButton = root.findViewById(R.id.float_action_bn);
        ViewUtils.setOnClick(root, R.id.float_action_bn, v -> onCreateLabelClick());
    }
}
