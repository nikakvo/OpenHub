package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IGistsContract;
import com.thirtydegreesray.openhub.mvp.model.Gist;
import com.thirtydegreesray.openhub.mvp.presenter.GistsPresenter;
import com.thirtydegreesray.openhub.ui.activity.GistActivity;
import com.thirtydegreesray.openhub.ui.adapter.GistsAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.GistChanges;

import java.util.ArrayList;

/**
 * Gists of a user, or the gists the logged in user starred. Tap: the gist screen,
 * long tap: open the gist on GitHub.
 */
public class GistsFragment extends ListFragment<GistsPresenter, GistsAdapter>
        implements IGistsContract.View {

    public static GistsFragment create(@NonNull String user, boolean starred) {
        GistsFragment fragment = new GistsFragment();
        fragment.setArguments(BundleHelper.builder().put("user", user).put("starred", starred).build());
        return fragment;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_list;
    }

    @Override
    protected void setupFragmentComponent(AppComponent appComponent) {
        DaggerFragmentComponent.builder()
                .appComponent(appComponent)
                .fragmentModule(new FragmentModule(this))
                .build()
                .inject(this);
    }

    @Override
    protected void initFragment(Bundle savedInstanceState) {
        super.initFragment(savedInstanceState);
        setLoadMoreEnable(true);
    }

    @Override
    public void showGists(ArrayList<Gist> gists) {
        adapter.setData(gists);
        postNotifyDataSetChanged();
    }

    @Override
    protected void onReLoadData() {
        mPresenter.loadGists(1, true);
    }

    @Override
    protected void onLoadMore(int page) {
        super.onLoadMore(page);
        mPresenter.loadGists(page, false);
    }

    @Override
    protected String getEmptyTip() {
        return getString(R.string.no_gists);
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        startActivity(GistActivity.createIntent(requireContext(), adapter.getData().get(position).getId()));
    }

    @Override
    protected long changeStamp() {
        // created, edited, deleted, starred or forked on the gist screens
        return GistChanges.stamp();
    }

    @Override
    public boolean onItemLongClick(int position, @NonNull View view) {
        AppOpener.openInCustomTabsOrBrowser(getActivity(), adapter.getData().get(position).getHtmlUrl());
        return true;
    }
}
