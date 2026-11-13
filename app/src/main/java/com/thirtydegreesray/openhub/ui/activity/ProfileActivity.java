

package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.IProfileContract;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.presenter.ProfilePresenter;
import com.thirtydegreesray.openhub.ui.activity.base.PagerActivity;
import com.thirtydegreesray.openhub.ui.adapter.base.FragmentPagerModel;
import com.thirtydegreesray.openhub.ui.fragment.ActivityFragment;
import com.thirtydegreesray.openhub.ui.fragment.ProfileInfoFragment;
import com.thirtydegreesray.openhub.ui.fragment.RepositoriesFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;


/**
 * Created by ThirtyDegreesRay on 2017/8/23 11:39:13
 */

public class ProfileActivity extends PagerActivity<ProfilePresenter>
        implements IProfileContract.View {

    public static void show(@NonNull Activity activity, @NonNull String loginId) {
        show(activity, loginId, null);
    }

    public static void show(@NonNull Activity activity,
                            @NonNull String loginId, @Nullable String userAvatar) {
        show(activity, null, loginId, userAvatar);
    }

    public static void show(@NonNull Activity activity, @Nullable View userAvatarView,
                            @NonNull String loginId, @Nullable String userAvatar) {
        Intent intent = createIntent(activity, loginId, userAvatar);
        if (userAvatarView != null) {
            ActivityOptionsCompat optionsCompat = ActivityOptionsCompat
                    .makeSceneTransitionAnimation(activity, userAvatarView, "userAvatar");
            activity.startActivity(intent, optionsCompat.toBundle());
        } else {
            activity.startActivity(intent);
        }

    }

    public static Intent createIntent(@NonNull Activity activity, @NonNull String loginId) {
        return createIntent(activity, loginId, null);
    }

    public static Intent createIntent(@NonNull Activity activity, @NonNull String loginId,
                                      @Nullable String userAvatar) {
        return new Intent(activity, ProfileActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("loginId", loginId)
                        .put("userAvatar", userAvatar)
                        .build());
    }

    private boolean isAvatarSetted = false;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(getActivity()))
                .build()
                .inject(this);
    }

    ImageView userImageViewBg;
    ImageView userImageView;
    ProgressBar loader;
    TextView joinedTime;
    TextView location;
    TextView profileName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    protected int getContentView() {
        return R.layout.activity_profile;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mPresenter.getUser() != null) {
            getMenuInflater().inflate(R.menu.menu_profile, menu);
            MenuItem followItem = menu.findItem(R.id.action_follow);
            MenuItem bookmark = menu.findItem(R.id.action_bookmark);
            followItem.setVisible(mPresenter.isUser() && !mPresenter.isMe());
            followItem.setTitle(mPresenter.isFollowing() ? R.string.unfollow : R.string.follow);
            bookmark.setTitle(mPresenter.isBookmarked() ?
                    getString(R.string.remove_bookmark) : getString(R.string.bookmark));
        }
        return true;
    }

    /** "My Profile" in the Matrix bar of the own profile, the login of anyone else. */
    @Override
    protected String matrixBarTitle() {
        String login = mPresenter == null ? null : mPresenter.getLoginId();
        com.thirtydegreesray.openhub.mvp.model.User me =
                com.thirtydegreesray.openhub.AppData.INSTANCE.getLoggedUser();
        if (login != null && me != null && login.equalsIgnoreCase(me.getLogin())) {
            return getString(R.string.my_profile);
        }
        return null;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(mPresenter.getLoginId());
        // the name is a text view in the header block (even gaps to location and date);
        // the header's own title only shows once it is collapsed into the toolbar
        if (toolbarLayout != null) toolbarLayout.setExpandedTitleColor(android.graphics.Color.TRANSPARENT);
        profileName.setText(mPresenter.getLoginId());
        keepHeaderBelowToolbar();
        setUserAvatar();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_follow) {
            mPresenter.followUser(!mPresenter.isFollowing());
            invalidateOptionsMenu();
            showSuccessToast(mPresenter.isFollowing() ?
                    getString(R.string.followed) : getString(R.string.unfollowed));
        } else if (itemId == R.id.action_bookmark) {
            mPresenter.bookmark(!mPresenter.isBookmarked());
            invalidateOptionsMenu();
            showSuccessToast(mPresenter.isBookmarked() ?
                    getString(R.string.bookmark_saved) : getString(R.string.bookmark_removed));
        } else if (itemId == R.id.action_share) {
            AppOpener.shareText(getActivity(), mPresenter.getUser().getHtmlUrl());
        } else if (itemId == R.id.action_open_in_browser) {
            AppOpener.openInCustomTabsOrBrowser(getActivity(), mPresenter.getUser().getHtmlUrl());
        } else if (itemId == R.id.action_copy_url) {
            AppUtils.copyToClipboard(getActivity(), mPresenter.getUser().getHtmlUrl());
        }
        return super.onOptionsItemSelected(item);
    }

    /** The header block starts a small gap below the toolbar (status bar height varies). */
    private void keepHeaderBelowToolbar() {
        final View headerInfo = findViewById(R.id.profile_header_info);
        if (toolbar == null || toolbarLayout == null || headerInfo == null) return;
        final int gap = getResources().getDimensionPixelSize(R.dimen.spacing_small);
        toolbarLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            androidx.core.view.WindowInsetsCompat insets =
                    androidx.core.view.ViewCompat.getRootWindowInsets(v);
            int statusTop = insets == null ? 0 : insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout()).top;
            int top = statusTop + toolbar.getHeight() + gap;
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) headerInfo.getLayoutParams();
            if (toolbar.getHeight() > 0 && lp.topMargin != top) {
                lp.topMargin = top;
                headerInfo.setLayoutParams(lp);
            }
        });
    }

    @Override
    public void showProfileInfo(User user) {
        invalidateOptionsMenu();
        setUserAvatar();
        joinedTime.setText(getString(R.string.joined_at).concat(" ")
                .concat(StringUtils.getDateStr(user.getCreatedAt())));
        location.setText(user.getLocation());
        location.setVisibility(StringUtils.isBlank(user.getLocation()) ? View.GONE : View.VISIBLE);

        if (pagerAdapter.getCount() == 0) {
            pagerAdapter.setPagerList(FragmentPagerModel.createProfilePagerList(getActivity(), user, getFragments()));
            tabLayout.setVisibility(View.VISIBLE);
            setupPager();
            showFirstPager();
        } else {
            notifyUserInfoUpdated(user);
        }
    }

    /** Loads the profile again from GitHub (pull down on the Info tab). */
    public void reloadProfile() {
        if (mPresenter != null) mPresenter.reloadProfile();
    }

    private void notifyUserInfoUpdated(User user){
        for(Fragment fragment : getFragments()){
            if(fragment != null && fragment instanceof ProfileInfoFragment){
                ((ProfileInfoFragment)fragment).updateProfileInfo(user);
            }
        }
    }

    @Override
    public void showLoading() {
        super.showLoading();
        loader.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        loader.setVisibility(View.GONE);
    }

    @Override
    public void finishActivity() {
        supportFinishAfterTransition();
    }

    private void setUserAvatar() {
        if (isAvatarSetted || StringUtils.isBlank(mPresenter.getUserAvatar())) return;
        isAvatarSetted = true;
        GlideApp.with(getActivity())
                .load(mPresenter.getUserAvatar())
                .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                .into(userImageViewBg);
        GlideApp.with(getActivity())
                .load(mPresenter.getUserAvatar())
                .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                .into(userImageView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //TODO Don't know why loader showImage automatic when resume from other page, conflict with screen transition
//        loader.setVisibility(View.GONE);
    }

    @Override
    public int getPagerSize() {
        return 3;
    }

    @Override
    protected int getFragmentPosition(Fragment fragment) {
        if (fragment instanceof ProfileInfoFragment) {
            return 0;
        } else if (fragment instanceof ActivityFragment) {
            return 1;
        } else if (fragment instanceof RepositoriesFragment) {
            return 2;
        } else
            return -1;
    }

    public void onUserAvatarClick() {
        if (!StringUtils.isBlank(mPresenter.getUserAvatar())) {
            ViewerActivity.showImage(getActivity(), mPresenter.getLoginId(),
                    mPresenter.getUserAvatar());
        }
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        userImageViewBg = root.findViewById(R.id.user_avatar_bg);
        userImageView = root.findViewById(R.id.user_avatar);
        loader = root.findViewById(R.id.loader);
        joinedTime = root.findViewById(R.id.joined_time);
        location = root.findViewById(R.id.location);
        profileName = root.findViewById(R.id.profile_name);
        ViewUtils.setOnClick(root, R.id.user_avatar, v -> onUserAvatarClick());
    }
}
