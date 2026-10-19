package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.filter.IssuesFilter;
import com.thirtydegreesray.openhub.mvp.model.filter.SortDirection;
import com.thirtydegreesray.openhub.mvp.presenter.IssuesActPresenter;
import com.thirtydegreesray.openhub.ui.activity.base.PagerActivity;
import com.thirtydegreesray.openhub.ui.adapter.base.FragmentPagerModel;
import com.thirtydegreesray.openhub.ui.fragment.IssuesFragment;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.ui.widget.ZoomAbleFloatingActionButton;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by ThirtyDegreesRay on 2017/9/20 14:42:31
 */

public class IssuesActivity extends PagerActivity<IssuesActPresenter>
    implements ListFragment.ListScrollListener{

    public static void showForRepo(@NonNull Activity activity, @NonNull String userId,
                                   @NonNull String repoName) {
        Intent intent = createIntentForRepo(activity, userId, repoName);
        activity.startActivity(intent);
    }

    public static void showForUser(@NonNull Activity activity) {
        Intent intent = new Intent(activity, IssuesActivity.class);
        intent.putExtras(BundleHelper.builder()
                .put("issuesType", IssuesFilter.Type.User).build());
        activity.startActivity(intent);
    }

    public static Intent createIntentForRepo(@NonNull Activity activity, @NonNull String userId,
                                             @NonNull String repoName) {
        return new Intent(activity, IssuesActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("issuesType", IssuesFilter.Type.Repo)
                        .put("userId", userId)
                        .put("repoName", repoName).build());
    }

    /** The issues of one milestone of a repository (open and closed tabs). */
    public static Intent createIntentForMilestone(@NonNull Context context, @NonNull String owner,
                                                  @NonNull String repo, int milestone,
                                                  @NonNull String milestoneTitle) {
        return new Intent(context, IssuesActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("issuesType", IssuesFilter.Type.Repo)
                        .put("userId", owner)
                        .put("repoName", repo)
                        .put("milestone", String.valueOf(milestone))
                        .put("milestoneTitle", milestoneTitle).build());
    }

    private final static int ADD_ISSUE_REQUEST_CODE = 100;

    @AutoAccess String userId;
    @AutoAccess String repoName;
    @AutoAccess IssuesFilter.Type issuesType;
    @AutoAccess String milestone;
    @AutoAccess String milestoneTitle;
    ZoomAbleFloatingActionButton addBn;

    private ArrayList<IssuesListListener> listeners;

    @Override
    protected void initActivity() {
        super.initActivity();
        setEndDrawerEnable(true);
    }

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(getActivity()))
                .build()
                .inject(this);
    }

    @Nullable
    @Override
    protected int getContentView() {
        return R.layout.activity_view_pager_with_drawer;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        updateEndDrawerContent(R.menu.drawer_menu_issues);
        setToolbarScrollAble(true);
        setToolbarBackEnable();
        if (IssuesFilter.Type.Repo.equals(issuesType)) {
            setToolbarTitle(getString(R.string.issues), userId.concat("/").concat(repoName)
                    .concat(StringUtils.isBlank(milestoneTitle) ? "" : " · " + milestoneTitle));
        } else {
            setToolbarTitle(getString(R.string.issues));
        }
        addBn.setVisibility(IssuesFilter.Type.Repo.equals(issuesType) ? View.VISIBLE : View.GONE);

        if (IssuesFilter.Type.User.equals(issuesType)) {
            pagerAdapter.setPagerList(FragmentPagerModel
                    .createUserIssuesPagerList(getActivity(), getFragments()));
        } else {
            List<FragmentPagerModel> fragmentPagerModels = FragmentPagerModel
                    .createRepoIssuesPagerList(getActivity(), userId, repoName, milestone, getFragments());
            pagerAdapter.setPagerList(fragmentPagerModels);
            navViewEnd.getMenu().findItem(R.id.nav_type_chooser).setVisible(false);
            ((ListFragment)fragmentPagerModels.get(0).getFragment()).setListScrollListener(this);
        }
        listeners = new ArrayList<>();
        for (FragmentPagerModel pagerModel : pagerAdapter.getPagerList()) {
            listeners.add((IssuesListListener) pagerModel.getFragment());
        }
        tabLayout.setVisibility(View.VISIBLE);
        tabLayout.setupWithViewPager(viewPager);
        viewPager.setAdapter(pagerAdapter);
        showFirstPager();
    }

    @Override
    protected boolean isEndDrawerMultiSelect() {
        return true;
    }

    @Override
    protected int getEndDrawerToggleMenuItemId() {
        return R.id.nav_sort;
    }

    @Override
    protected void onNavItemSelected(@NonNull MenuItem item, boolean isStartDrawer) {
        listeners.get(0).onIssuesFilterChanged(getIssuesFilter(true));
        listeners.get(1).onIssuesFilterChanged(getIssuesFilter(false));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sort, menu);
        return true;
    }

    public void onAddIssueClick() {
        EditIssueActivity.showForAdd(getActivity(), userId, repoName, ADD_ISSUE_REQUEST_CODE);
    }

    private IssuesFilter getIssuesFilter(boolean open) {
        IssuesFilter issuesFilter = new IssuesFilter(issuesType, open ? Issue.IssueState.open
                : Issue.IssueState.closed).setMilestone(milestone);
        if (IssuesFilter.Type.User.equals(issuesType)) {
            MenuItem selectedUserFilterMenu = ViewUtils.getSelectedMenu(
                    navViewEnd.getMenu().findItem(R.id.nav_type_chooser));
            if (selectedUserFilterMenu != null) {
                IssuesFilter.UserIssuesFilterType userFilterType =
                        getUserIssuesFilterType(selectedUserFilterMenu.getItemId());
                issuesFilter.setUserIssuesFilterType(userFilterType);
            }
        }
        MenuItem sortMenu = ViewUtils.getSelectedMenu(navViewEnd.getMenu().findItem(R.id.nav_sort));
        IssuesFilter.SortType sortType = IssuesFilter.SortType.Created;
        SortDirection sortDirection = SortDirection.Desc;
        if (sortMenu != null) {
            int selectedItemId = sortMenu.getItemId();
            if (selectedItemId == R.id.nav_recently_created) {
                sortType = IssuesFilter.SortType.Created;
                sortDirection = SortDirection.Desc;
            } else if (selectedItemId == R.id.nav_previously_created) {
                sortType = IssuesFilter.SortType.Created;
                sortDirection = SortDirection.Asc;
            } else if (selectedItemId == R.id.nav_recently_updated) {
                sortType = IssuesFilter.SortType.Updated;
                sortDirection = SortDirection.Desc;
            } else if (selectedItemId == R.id.nav_least_recently_updated) {
                sortType = IssuesFilter.SortType.Updated;
                sortDirection = SortDirection.Asc;
            } else if (selectedItemId == R.id.nav_most_comments) {
                sortType = IssuesFilter.SortType.Comments;
                sortDirection = SortDirection.Desc;
            } else if (selectedItemId == R.id.nav_fewest_comments) {
                sortType = IssuesFilter.SortType.Comments;
                sortDirection = SortDirection.Asc;
            }
        }
        issuesFilter.setSortType(sortType);
        issuesFilter.setSortDirection(sortDirection);
        return issuesFilter;
    }

    private IssuesFilter.UserIssuesFilterType getUserIssuesFilterType(int itemId) {
        if (itemId == R.id.nav_all) {
            return IssuesFilter.UserIssuesFilterType.All;
        } else if (itemId == R.id.nav_created) {
            return IssuesFilter.UserIssuesFilterType.Created;
        } else if (itemId == R.id.nav_assigned) {
            return IssuesFilter.UserIssuesFilterType.Assigned;
        } else if (itemId == R.id.nav_mentioned) {
            return IssuesFilter.UserIssuesFilterType.Mentioned;
        } else {
            return IssuesFilter.UserIssuesFilterType.All;
        }
    }

    @Override
    public void onScrollUp() {
        addBn.zoomIn();
    }

    @Override
    public void onScrollDown() {
        addBn.zoomOut();
    }

    public interface IssuesListListener {
        void onIssuesFilterChanged(@NonNull IssuesFilter issuesFilter);

        void onCreateIssue(@NonNull Issue issue);
    }

    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        if(IssuesFilter.Type.Repo.equals(issuesType)){
            if(position == 0){
                addBn.zoomOut();
            } else {
                addBn.zoomIn();
            }
        }
    }

    @Override
    public int getPagerSize() {
        return 2;
    }

    @Override
    protected int getFragmentPosition(Fragment fragment) {
        if (fragment instanceof IssuesFragment) {
            IssuesFilter issuesFilter = fragment.getArguments().getParcelable("issuesFilter");
            if (issuesFilter == null) {
                return -1;
            } else {
                return Issue.IssueState.open.equals(issuesFilter.getIssueState()) ? 0 : 1;
            }
        } else
            return -1;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == ADD_ISSUE_REQUEST_CODE) {
            Issue issue = data.getParcelableExtra("issue");
            listeners.get(0).onCreateIssue(issue);
        }
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        addBn = root.findViewById(R.id.add_issue_bn);
        ViewUtils.setOnClick(root, R.id.add_issue_bn, v -> onAddIssueClick());
    }
}
