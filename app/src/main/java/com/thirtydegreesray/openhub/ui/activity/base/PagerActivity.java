

package com.thirtydegreesray.openhub.ui.activity.base;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import android.view.KeyEvent;

import com.thirtydegreesray.openhub.util.AppLog;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.ui.adapter.base.FragmentViewPagerAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;

import java.util.ArrayList;

import javax.inject.Inject;
import android.view.View;


/**
 * Created by ThirtyDegreesRay on 2017/8/14 17:51:00
 */

public abstract class PagerActivity<P extends BasePresenter> extends BaseDrawerActivity<P>
        implements IBaseContract.View,
        TabLayout.OnTabSelectedListener{

    @Inject protected FragmentViewPagerAdapter pagerAdapter;

    protected ViewPager2 viewPager;
    protected TabLayout tabLayout;

    private ArrayList<Fragment> fragments ;

    private int prePosition = 0;

    /** ViewPager2 also reports the page it starts on; the old pager did not. */
    private boolean firstPageReported;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ViewPager2.OnPageChangeCallback pageChangeCallback =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    PagerActivity.this.onPageSelected(position);
                }
            };

    @Override
    protected void initActivity() {
        super.initActivity();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        viewPager.registerOnPageChangeCallback(pageChangeCallback);
        tabLayout.addOnTabSelectedListener(this);
    }

    @Nullable
    private IFragmentBackHandler getBackHandlerFragment() {
        Fragment fragment = pagerAdapter == null ? null : pagerAdapter.getCurFragment();
        return fragment instanceof IFragmentBackHandler ? (IFragmentBackHandler) fragment : null;
    }

    @Override
    protected boolean interceptsBack() {
        if (super.interceptsBack()) return true;
        IFragmentBackHandler handler = getBackHandlerFragment();
        return handler != null && handler.canHandleBack();
    }

    @Override
    protected void handleBack() {
        if (super.interceptsBack()) {
            super.handleBack();
            return;
        }
        IFragmentBackHandler handler = getBackHandlerFragment();
        if (handler != null) handler.handleBack();
    }

    @Override
    protected void onToolbarDoubleClick() {
        super.onToolbarDoubleClick();
        scrollToTop();
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        scrollToTop();
    }

    private void scrollToTop(){
        Fragment fragment = pagerAdapter.getCurFragment();
        if(fragment != null && fragment instanceof BaseFragment){
            ((BaseFragment)fragment).scrollToTop();
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (viewPager != null) viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        super.onDestroy();
    }

    /**
     * A page fragment that consumes Back itself (e.g. going up a folder in Files).
     * It calls {@link #invalidateBack()} on its activity when canHandleBack() changes.
     */
    public interface IFragmentBackHandler {
        boolean canHandleBack();

        void handleBack();
    }

    /**
     * Shows the pages of {@link #pagerAdapter} with their tabs. Every page stays alive (as
     * with the old pager), so switching tabs never reloads a page or loses its place.
     */
    protected void setupPager() {
        viewPager.setOffscreenPageLimit(Math.max(1, pagerAdapter.getItemCount() - 1));
        viewPager.setAdapter(pagerAdapter);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(pagerAdapter.getPageTitle(position))).attach();
    }

    public void onPageSelected(final int position) {
        pagerAdapter.setCurPosition(position);
        if (!firstPageReported) {
            // the page shown when the screen opens (or comes back): nothing changed
            firstPageReported = true;
            if (position == prePosition) {
                viewPager.post(this::invalidateBack);
                return;
            }
        }
        postNotifyFragmentStatus(prePosition, false, 100);
        postNotifyFragmentStatus(position, true, 500);
        prePosition = position;
        viewPager.post(this::invalidateBack);
        AppLog.d("PagerActivity", "onPageSelected " + position);
    }

    /**
     * Notify first pager selected, only for first launch
     */
    protected void showFirstPager(){
        prePosition = 0;
        postNotifyFragmentStatus(0, true, 100);

    }

    private void postNotifyFragmentStatus(final int position, final boolean isShow, long delay){
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                if(isShow){
                    pagerAdapter.getPagerList().get(position).getFragment().onFragmentShowed();
                }else{
                    pagerAdapter.getPagerList().get(position).getFragment().onFragmentHided();
                }
            }
        }, delay);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        int fragmentPosition = getFragmentPosition(fragment);
        if(fragmentPosition != -1) getFragments().set(fragmentPosition, fragment);
        AppLog.d("PagerActivity", "onAttachFragment " + fragment);
    }

    @NonNull
    public ArrayList<Fragment> getFragments() {
        if(fragments == null){
            fragments = new ArrayList<>();
            for(int i = 0; i < getPagerSize(); i++){
                fragments.add(null);
            }
        }
        return fragments;
    }

    public abstract int getPagerSize();

    protected abstract int getFragmentPosition(Fragment fragment);



    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        viewPager = root.findViewById(R.id.view_pager);
        tabLayout = root.findViewById(R.id.tab_layout);
    }
}
