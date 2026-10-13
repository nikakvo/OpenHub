

package com.thirtydegreesray.openhub.ui.activity;

import com.thirtydegreesray.openhub.util.HelpPage;

import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.dao.AuthUser;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.IMainContract;
import com.thirtydegreesray.openhub.mvp.model.User;
import com.thirtydegreesray.openhub.mvp.model.filter.RepositoriesFilter;
import com.thirtydegreesray.openhub.mvp.presenter.MainPresenter;
import com.thirtydegreesray.openhub.ui.activity.base.BaseDrawerActivity;
import com.thirtydegreesray.openhub.ui.fragment.ActivityFragment;
import com.thirtydegreesray.openhub.ui.fragment.BookmarksFragment;
import com.thirtydegreesray.openhub.ui.fragment.CollectionsFragment;
import com.thirtydegreesray.openhub.ui.fragment.RepositoriesFragment;
import com.thirtydegreesray.openhub.ui.fragment.TopicsFragment;
import com.thirtydegreesray.openhub.ui.fragment.TraceFragment;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.util.AppUpdater;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends BaseDrawerActivity<MainPresenter>
        implements IMainContract.View {

    Toolbar toolbar;
    TabLayout tabLayout;
    FrameLayout frameLayoutContent;

    private AppCompatImageView toggleAccountBn;

    private final Map<Integer, String> TAG_MAP = new HashMap<>();

    private final int SETTINGS_REQUEST_CODE = 100;

    @AutoAccess int selectedPage ;
    private int homePage;
    private boolean isAccountsAdded = false;

    private final List<Integer> FRAGMENT_NAV_ID_LIST = Arrays.asList(
            R.id.nav_news, R.id.nav_owned, R.id.nav_starred, R.id.nav_bookmarks,
            R.id.nav_trace, R.id.nav_public_news, R.id.nav_collections, R.id.nav_topics
    );

    private final List<String> FRAGMENT_TAG_LIST = Arrays.asList(
            ActivityFragment.ActivityType.News.name(),
            RepositoriesFragment.RepositoriesType.OWNED.name(),
            RepositoriesFragment.RepositoriesType.STARRED.name(),
            BookmarksFragment.class.getSimpleName(),
            TraceFragment.class.getSimpleName(),
            ActivityFragment.ActivityType.PublicNews.name(),
            CollectionsFragment.class.getSimpleName(),
            TopicsFragment.class.getSimpleName()
    );

    private final List<Integer> FRAGMENT_TITLE_LIST = Arrays.asList(
            R.string.news, R.string.my_repos, R.string.starred_repos, R.string.bookmarks,
            R.string.trace, R.string.public_news, R.string.repo_collections, R.string.topics
    );

    {
        for(int i = 0; i < FRAGMENT_NAV_ID_LIST.size(); i++){
            TAG_MAP.put(FRAGMENT_NAV_ID_LIST.get(i), FRAGMENT_TAG_LIST.get(i));
        }
    }


    /**
     * 依赖注入的入口
     *
     * @param appComponent appComponent
     */
    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(getActivity()))
                .build()
                .inject(this);
    }

    @Override
    protected void initActivity() {
        super.initActivity();
        if (AppData.INSTANCE.getLoggedUser() != null)

        setStartDrawerEnable(true);
        setEndDrawerEnable(true);
    }

    /**
     * 获取ContentView id
     *
     * @return
     */
    @Override
    protected int getContentView() {
        return R.layout.activity_main;
    }

    /**
     * 初始化view
     *
     * @param savedInstanceState
     */
    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);

        setToolbarScrollAble(true);
        if (savedInstanceState == null) AppUpdater.checkOnStart(this);
        updateStartDrawerContent(R.menu.activity_main_drawer);
        removeEndDrawer();
        if (mPresenter.isFirstUseAndNoNewsUser()) {
            selectedPage = R.id.nav_public_news;
            homePage = selectedPage;
            updateFragmentByNavId(selectedPage);
        } else if(selectedPage != 0){
            homePage = getHomePageNavId();
            updateFragmentByNavId(selectedPage);
        } else {
            int startPageNavId = getStartPageNavId();
            homePage = getHomePageNavId();
            if(FRAGMENT_NAV_ID_LIST.contains(startPageNavId)){
                selectedPage = startPageNavId;
                updateFragmentByNavId(selectedPage);
            } else {
                selectedPage = R.id.nav_news;
                updateFragmentByNavId(selectedPage);
                updateFragmentByNavId(startPageNavId);
            }
        }
        navViewStart.setCheckedItem(selectedPage);
        com.thirtydegreesray.openhub.util.DrawerIcons.apply(navViewStart);

        ImageView avatar = navViewStart.getHeaderView(0).findViewById(R.id.avatar);
        TextView name = navViewStart.getHeaderView(0).findViewById(R.id.name);
        TextView mail = navViewStart.getHeaderView(0).findViewById(R.id.mail);

        toggleAccountBn = navViewStart.getHeaderView(0).findViewById(R.id.toggle_account_bn);
        toggleAccountBn.setOnClickListener(v -> {
            toggleAccountLay();
        });

        User loginUser = AppData.INSTANCE.getLoggedUser();
        GlideApp.with(getActivity())
                .load(loginUser.getAvatarUrl())
                .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                .into(avatar);
        name.setText(StringUtils.isBlank(loginUser.getName()) ? loginUser.getLogin() : loginUser.getName());
        String joinTime = getString(R.string.joined_at).concat(" ")
                .concat(StringUtils.getDateStr(loginUser.getCreatedAt()));
        mail.setText(StringUtils.isBlank(loginUser.getBio()) ? joinTime : loginUser.getBio());

        tabLayout.setVisibility(View.GONE);
    }

    /**
     * Nav id of the start page chosen in Settings (can be a page that opens its own
     * activity, e.g. Trending).
     */
    private int getStartPageNavId() {
        String startPageId = PrefUtils.getStartPage();
        int startPageIndex = Arrays.asList(getResources().getStringArray(R.array.start_pages_id))
                .indexOf(startPageId);
        if (startPageIndex < 0) return R.id.nav_news;
        TypedArray typedArray = getResources().obtainTypedArray(R.array.start_pages_nav_id);
        int startPageNavId = typedArray.getResourceId(startPageIndex, 0);
        typedArray.recycle();
        return startPageNavId;
    }

    /**
     * Page that Back returns to before leaving the app: the start page when it lives in
     * this activity, News otherwise.
     */
    private int getHomePageNavId() {
        int startPageNavId = getStartPageNavId();
        return FRAGMENT_NAV_ID_LIST.contains(startPageNavId) ? startPageNavId : R.id.nav_news;
    }

    private boolean isAwayFromHomePage() {
        return homePage != 0 && selectedPage != homePage
                && FRAGMENT_NAV_ID_LIST.contains(homePage);
    }

    @Override
    protected boolean interceptsBack() {
        return super.interceptsBack() || isAwayFromHomePage();
    }

    @Override
    protected void handleBack() {
        if (super.interceptsBack()) {
            super.handleBack();
        } else {
            updateFragmentByNavId(homePage);
            if (navViewStart != null) navViewStart.setCheckedItem(homePage);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sort, menu);
        MenuItem menuItem = menu.findItem(R.id.nav_sort);
        menuItem.setVisible(selectedPage == R.id.nav_owned || selectedPage == R.id.nav_starred);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        invalidateMainMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected boolean isEndDrawerMultiSelect() {
        return true;
    }

    @Override
    protected int getEndDrawerToggleMenuItemId() {
        return R.id.nav_sort;
    }

    protected void onNavItemSelected(@NonNull MenuItem item, boolean isStartDrawer) {
        super.onNavItemSelected(item, isStartDrawer);
        if (!isStartDrawer) {
            handlerEndDrawerClick(item);
            return;
        }
        int id = item.getItemId();
        updateFragmentByNavId(id);
    }

    private void updateFragmentByNavId(int id){
        if(FRAGMENT_NAV_ID_LIST.contains(id)){
            updateTitle(id);
            loadFragment(id);
            updateFilter(id);
            return;
        }
        if (id == R.id.nav_profile) {
            ProfileActivity.show(getActivity(), AppData.INSTANCE.getLoggedUser().getLogin(),
                    AppData.INSTANCE.getLoggedUser().getAvatarUrl());
        } else if (id == R.id.nav_new_repo) {
            CreateRepositoryActivity.show(getActivity());
        } else if (id == R.id.nav_issues) {
            IssuesActivity.showForUser(getActivity());
        } else if (id == R.id.nav_gists) {
            GistsActivity.show(getActivity(), AppData.INSTANCE.getLoggedUser().getLogin());
        } else if (id == R.id.nav_notifications) {
            NotificationsActivity.show(getActivity());
        } else if (id == R.id.nav_trending) {
            TrendingActivity.show(getActivity());
        } else if (id == R.id.nav_search) {
            SearchActivity.show(getActivity());
        } else if (id == R.id.nav_settings) {
            SettingsActivity.show(getActivity(), SETTINGS_REQUEST_CODE);
        } else if (id == R.id.nav_help) {
            HelpPage.show(getActivity());
        } else if (id == R.id.nav_about) {
            AboutActivity.show(getActivity());
        } else if (id == R.id.nav_logout) {
            logout();
        } else if (id == R.id.nav_add_account) {
            startActivity(new Intent(getActivity(), LoginActivity.class));
        }
    }

    private void updateFilter(int itemId) {
        if (itemId == R.id.nav_owned) {
            updateEndDrawerContent(R.menu.menu_repositories_filter);
            RepositoriesFilter.initDrawer(navViewEnd, RepositoriesFragment.RepositoriesType.OWNED);
        } else if (itemId == R.id.nav_starred) {
            updateEndDrawerContent(R.menu.menu_repositories_filter);
            RepositoriesFilter.initDrawer(navViewEnd, RepositoriesFragment.RepositoriesType.STARRED);
        } else {
            removeEndDrawer();
        }
        invalidateOptionsMenu();
    }

    private void updateTitle(int itemId) {
        int titleId = FRAGMENT_TITLE_LIST.get(FRAGMENT_NAV_ID_LIST.indexOf(itemId));
        setToolbarTitle(getString(titleId));
    }

    private void loadFragment(int itemId) {
        selectedPage = itemId;
        invalidateBack();
        String fragmentTag = TAG_MAP.get(itemId);
        Fragment showFragment = getSupportFragmentManager().findFragmentByTag(fragmentTag);
        boolean isExist = true;
        if (showFragment == null) {
            isExist = false;
            showFragment = getFragment(itemId);
        }
        if (showFragment.isVisible()) {
            return;
        }

        Fragment visibleFragment = getVisibleFragment();
        if (isExist) {
            showAndHideFragment(showFragment, visibleFragment);
        } else {
            addAndHideFragment(showFragment, visibleFragment, fragmentTag);
        }
    }

    @NonNull
    private Fragment getFragment(int itemId) {
        if (itemId == R.id.nav_news) {
            return ActivityFragment.create(ActivityFragment.ActivityType.News,
                    AppData.INSTANCE.getLoggedUser().getLogin());
        } else if (itemId == R.id.nav_public_news) {
            return ActivityFragment.create(ActivityFragment.ActivityType.PublicNews,
                    AppData.INSTANCE.getLoggedUser().getLogin());
        } else if (itemId == R.id.nav_owned) {
            return RepositoriesFragment.create(RepositoriesFragment.RepositoriesType.OWNED,
                    AppData.INSTANCE.getLoggedUser().getLogin());
        } else if (itemId == R.id.nav_starred) {
            return RepositoriesFragment.create(RepositoriesFragment.RepositoriesType.STARRED,
                    AppData.INSTANCE.getLoggedUser().getLogin());
        } else if (itemId == R.id.nav_bookmarks) {
            return BookmarksFragment.create();
        } else if (itemId == R.id.nav_trace) {
            return TraceFragment.create();
        } else if (itemId == R.id.nav_collections) {
            return CollectionsFragment.create();
        } else if (itemId == R.id.nav_topics) {
            return TopicsFragment.create();
        }
        return null;
    }

    private void showAndHideFragment(@NonNull Fragment showFragment, @Nullable Fragment hideFragment) {
        if (hideFragment == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .show(showFragment)
                    .commit();
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .show(showFragment)
                    .hide(hideFragment)
                    .commit();
        }

    }

    private void addAndHideFragment(@NonNull Fragment showFragment,
                                    @Nullable Fragment hideFragment, @NonNull String addTag) {
        if (hideFragment == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.frame_layout_content, showFragment, addTag)
                    .commit();
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.frame_layout_content, showFragment, addTag)
                    .hide(hideFragment)
                    .commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            recreate();
        }
    }

    @Override
    protected void onToolbarDoubleClick() {
        super.onToolbarDoubleClick();
        Fragment fragment = getVisibleFragment();
        if (fragment != null && fragment instanceof BaseFragment) {
            ((BaseFragment) fragment).scrollToTop();
        }
    }

    private void handlerEndDrawerClick(MenuItem item) {
        Fragment fragment = getVisibleFragment();
        if (fragment != null && fragment instanceof RepositoriesFragment
                && (selectedPage == R.id.nav_owned || selectedPage == R.id.nav_starred)) {
            ((RepositoriesFragment) fragment).onDrawerSelected(navViewEnd, item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private boolean isManageAccount = false;
    private void toggleAccountLay(){
        isManageAccount = !isManageAccount;
        toggleAccountBn.setImageResource(isManageAccount ? R.drawable.ic_arrow_drop_up : R.drawable.ic_arrow_drop_down);
        invalidateMainMenu();
    }

    private void invalidateMainMenu(){
        if(navViewStart == null){
            return ;
        }
        Menu menu = navViewStart.getMenu();

        if(!isAccountsAdded){
            isAccountsAdded = true;
            List<AuthUser> users = mPresenter.getLoggedUserList();
            for(AuthUser user : users){
                MenuItem menuItem = menu.add(R.id.manage_accounts, Menu.NONE, 1, user.getLoginId())
                        .setIcon(R.drawable.ic_menu_person)
                        .setOnMenuItemClickListener(item -> {
                            mPresenter.toggleAccount(item.getTitle().toString());
                            return true;
                        });
            }
        }

        menu.setGroupVisible(R.id.my_account, isManageAccount);
        menu.setGroupVisible(R.id.manage_accounts, isManageAccount);

        menu.setGroupVisible(R.id.my, !isManageAccount);
        menu.setGroupVisible(R.id.repositories, !isManageAccount);
        menu.setGroupVisible(R.id.search, !isManageAccount);
        menu.setGroupVisible(R.id.setting, !isManageAccount);

    }

    @Override
    public void restartApp() {
        getActivity().finishAffinity();
        Intent intent = new Intent(getActivity(), SplashActivity.class);
        startActivity(intent);
    }

    private void logout() {
        new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setTitle(R.string.warning_dialog_tile)
                .setMessage(R.string.logout_warning)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    mPresenter.logout();
                })
                .show();
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        toolbar = root.findViewById(R.id.toolbar);
        tabLayout = root.findViewById(R.id.tab_layout);
        frameLayoutContent = root.findViewById(R.id.frame_layout_content);
    }
}
