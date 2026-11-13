

package com.thirtydegreesray.openhub.ui.adapter.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;

import java.util.List;

import javax.inject.Inject;

/**
 * The pages of a screen with tabs (ViewPager2). The page fragments are made up front by
 * {@link FragmentPagerModel} (the screen talks to them directly), so this adapter only
 * hands them out. The screen keeps every page alive (offscreen page limit = all pages),
 * as the old pager did, so a page never loses its scroll position or loaded content.
 */
public class FragmentViewPagerAdapter extends FragmentStateAdapter {

    private List<FragmentPagerModel> mPagerList;

    private int curPosition;

    @Inject
    public FragmentViewPagerAdapter(@NonNull BaseActivity activity) {
        super(activity);
    }

    public void setPagerList(List<FragmentPagerModel> pagerList) {
        mPagerList = pagerList;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return mPagerList.get(position).getFragment();
    }

    @Override
    public int getItemCount() {
        return mPagerList == null ? 0 : mPagerList.size();
    }

    /** Same as {@link #getItemCount()}. */
    public int getCount() {
        return getItemCount();
    }

    public CharSequence getPageTitle(int position) {
        return mPagerList.get(position).getTitle();
    }

    /** The selected page (set by the screen when a page is selected). */
    public void setCurPosition(int position) {
        curPosition = position;
    }

    @Nullable
    public Fragment getCurFragment() {
        if (mPagerList == null || curPosition < 0 || curPosition >= mPagerList.size()) return null;
        Fragment fragment = mPagerList.get(curPosition).getFragment();
        return fragment != null && fragment.isAdded() ? fragment : null;
    }

    public List<FragmentPagerModel> getPagerList() {
        return mPagerList;
    }

}
