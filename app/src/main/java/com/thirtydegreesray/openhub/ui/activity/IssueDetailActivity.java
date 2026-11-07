package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.core.util.Pair;
import androidx.appcompat.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.IIssueDetailContract;
import com.thirtydegreesray.openhub.mvp.model.Issue;
import com.thirtydegreesray.openhub.mvp.model.IssueEvent;
import com.thirtydegreesray.openhub.mvp.presenter.IssueDetailPresenter;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.fragment.IssueTimelineFragment;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.ui.widget.SubIssuesBox;
import com.thirtydegreesray.openhub.ui.widget.ZoomAbleFloatingActionButton;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;


/**
 * Created by ThirtyDegreesRay on 2017/9/26 19:27:11
 */

public class IssueDetailActivity extends BaseActivity<IssueDetailPresenter>
        implements IIssueDetailContract.View,
        ListFragment.ListScrollListener, SubIssuesBox.Host,
        com.thirtydegreesray.openhub.ui.widget.IssueFieldsBox.Host {

    public static void show(@NonNull Activity activity, @NonNull View avatarView,
                            @NonNull View titleView, @NonNull Issue issue) {
        Intent intent = new Intent(activity, IssueDetailActivity.class);
        Pair<View, String> avatarPair = Pair.create(avatarView, "userAvatar");
        Pair<View, String> titlePair = Pair.create(titleView, "issueTitle");
        ActivityOptionsCompat optionsCompat = ActivityOptionsCompat
                .makeSceneTransitionAnimation(activity, avatarPair, titlePair);
        intent.putExtras(BundleHelper.builder().put("issue", issue).build());
        activity.startActivity(intent, optionsCompat.toBundle());
    }

    public static void show(@NonNull Activity activity, @NonNull Issue issue) {
        Intent intent = new Intent(activity, IssueDetailActivity.class);
        intent.putExtras(BundleHelper.builder().put("issue", issue).build());
        activity.startActivity(intent);
    }

    public static void show(@NonNull Activity activity, @NonNull String issueUrl) {
        Intent intent = new Intent(activity, IssueDetailActivity.class);
        intent.putExtras(BundleHelper.builder().put("issueUrl", issueUrl).build());
        activity.startActivity(intent);
    }

    public static void show(@NonNull Activity activity, @NonNull String owner,
                            @NonNull String repoName, int issueNumber) {
        Intent intent = createIntent(activity, owner, repoName, issueNumber);
        activity.startActivity(intent);
    }

    public static Intent createIntent(@NonNull Activity activity, @NonNull String owner,
                                      @NonNull String repoName, int issueNumber) {
        return new Intent(activity, IssueDetailActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("owner", owner)
                        .put("repoName", repoName)
                        .put("issueNumber", issueNumber).build());
    }

    ImageView userImageView;
    TextView issueTitle;
    ImageView issueStateImg;
    TextView issueStateText;
    ZoomAbleFloatingActionButton commentBn;
    FloatingActionButton editBn;
    ProgressBar loader;

    private IssueTimelineFragment issueTimelineFragment;
    private SubIssuesBox subIssuesBox;
    private com.thirtydegreesray.openhub.ui.widget.IssueFieldsBox issueFieldsBox;

    private final int ADD_COMMENT_REQUEST_CODE = 100;
    public static final int EDIT_COMMENT_REQUEST_CODE = 101;
    public static final int EDIT_ISSUE_REQUEST_CODE = 102;
    private static final int NEW_SUB_ISSUE_REQUEST_CODE = 103;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(this))
                .build()
                .inject(this);
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_issue_detail;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        if (subIssuesBox == null) subIssuesBox = new SubIssuesBox(this, this);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.issue));
        commentBn.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mPresenter.getIssue() != null) {
            getMenuInflater().inflate(R.menu.menu_issue_detail, menu);
            boolean isCanToggle = AppData.INSTANCE.getLoggedUser().getLogin()
                    .equals(mPresenter.getIssue().getUser().getLogin()) ||
                    AppData.INSTANCE.getLoggedUser().getLogin()
                            .equals(mPresenter.getIssue().getRepoAuthorName());
            boolean isOpen = mPresenter.getIssue().getState().equals(Issue.IssueState.open);
            if (isCanToggle) {
                MenuItem item = menu.findItem(R.id.action_issue_toggle);
                item.setTitle(isOpen ? R.string.close : R.string.reopen);
            } else {
                menu.removeItem(R.id.action_issue_toggle);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            supportFinishAfterTransition();
            return true;
        } else if (itemId == R.id.action_open_in_browser) {
            AppOpener.openInCustomTabsOrBrowser(getActivity(), mPresenter.getIssue().getHtmlUrl());
            return true;
        } else if (itemId == R.id.action_share) {
            AppOpener.shareText(getActivity(), mPresenter.getIssue().getHtmlUrl());
            return true;
        } else if (itemId == R.id.action_copy_url) {
            AppUtils.copyToClipboard(getActivity(), mPresenter.getIssue().getHtmlUrl());
            return true;
        } else if (itemId == R.id.action_issue_toggle) {
            showToggleIssueWarning();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showIssue(final Issue issue) {
        setToolbarTitle(getString(R.string.issue).concat(" #").concat(String.valueOf(issue.getNumber())));
        GlideApp.with(getActivity())
                .load(issue.getUser().getAvatarUrl())
                .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                .into(userImageView);
        issueTitle.setText(issue.getTitle());
        commentBn.setVisibility(issue.isLocked() ? View.GONE : View.VISIBLE);

        String commentStr = String.valueOf(issue.getCommentNum()).concat(" ")
                .concat(getString(R.string.comments).toLowerCase());
        if (Issue.IssueState.open.equals(issue.getState())) {
            issueStateImg.setImageResource(R.drawable.ic_issues);
            issueStateText.setText(getString(R.string.open).concat("    ").concat(commentStr));
        } else {
            issueStateImg.setImageResource(R.drawable.ic_issues_closed);
            issueStateText.setText(getString(R.string.closed).concat("    ").concat(commentStr));
        }
        invalidateOptionsMenu();

        if (issueTimelineFragment == null) {
            issueTimelineFragment = IssueTimelineFragment.create(issue);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isAlive) return;
                    getSupportFragmentManager()
                            .beginTransaction()
                            .add(R.id.container, issueTimelineFragment)
                            .commitAllowingStateLoss();
                }
            }, 500);
            issueTimelineFragment.setListScrollListener(this);
        } else {
            issueTimelineFragment.issueReloaded(issue);
        }

        String loggedUser = AppData.INSTANCE.getLoggedUser().getLogin();
        boolean editAble = loggedUser.equals(issue.getUser().getLogin()) ||
                loggedUser.equals(issue.getRepoAuthorName());
        editBn.setVisibility(editAble ? View.VISIBLE : View.GONE);
        commentBn.setVisibility(View.VISIBLE);
        subIssuesBox().load(issue);
        issueFieldsBox().load(issue);
    }

    /** The issue type and the issue field values shown in the first timeline item. */
    @NonNull
    public com.thirtydegreesray.openhub.ui.widget.IssueFieldsBox issueFieldsBox() {
        if (issueFieldsBox == null) {
            issueFieldsBox = new com.thirtydegreesray.openhub.ui.widget.IssueFieldsBox(this, this);
        }
        return issueFieldsBox;
    }

    @Override
    public void issueFieldsChanged() {
        if (issueTimelineFragment != null) issueTimelineFragment.subIssuesChanged();
    }

    /** The parent and the sub-issues shown in the first timeline item. */
    @NonNull
    public SubIssuesBox subIssuesBox() {
        if (subIssuesBox == null) subIssuesBox = new SubIssuesBox(this, this);
        return subIssuesBox;
    }

    @Override
    public void subIssuesChanged() {
        if (issueTimelineFragment != null) issueTimelineFragment.subIssuesChanged();
    }

    @Override
    public void createSubIssue() {
        if (mPresenter.getOwner() == null || mPresenter.getRepoName() == null) return;
        EditIssueActivity.showForAdd(getActivity(), mPresenter.getOwner(), mPresenter.getRepoName(),
                NEW_SUB_ISSUE_REQUEST_CODE);
    }

    @Override
    public void reloadIssueFromNetwork() {
        reloadIssue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // a sub-issue was closed, added or removed on another screen: GitHub's new progress
        if (subIssuesBox != null && subIssuesBox.isStale() && mPresenter != null
                && mPresenter.getIssue() != null) {
            com.thirtydegreesray.openhub.util.PullRefresh.freshFromNow();
            reloadIssue();
        }
    }

    @Override
    protected void onDestroy() {
        if (subIssuesBox != null) subIssuesBox.destroy();
        if (issueFieldsBox != null) issueFieldsBox.destroy();
        super.onDestroy();
    }

    /** Pull down on the timeline: the title, state and text of the issue too. */
    public void reloadIssue() {
        if (mPresenter != null) mPresenter.reloadIssue();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof IssueTimelineFragment) {
            issueTimelineFragment = (IssueTimelineFragment) fragment;
        }
    }

    @Override
    public void showAddedComment(@NonNull IssueEvent event) {
        issueTimelineFragment.addComment(event);
    }

    @Override
    public void showAddCommentPage(@Nullable String text) {
        MarkdownEditorActivity.show(getActivity(), R.string.comment, ADD_COMMENT_REQUEST_CODE,
                text, issueTimelineFragment == null ? null : issueTimelineFragment.getIssueUsersExceptMe());
    }

    public void onCommentBnClicked() {
        showAddCommentPage(null);
    }

    public void onEditBnClicked() {
        EditIssueActivity.showForEdit(getActivity(), mPresenter.getIssue(), EDIT_ISSUE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) return;
        if (requestCode == ADD_COMMENT_REQUEST_CODE) {
            String text = data.getExtras().getString("text");
            mPresenter.addComment(text);
            return;
        } else if (requestCode == EDIT_COMMENT_REQUEST_CODE) {
            String text = data.getExtras().getString("text");
            issueTimelineFragment.onEditComment(text);
            return;
        } else if (requestCode == EDIT_ISSUE_REQUEST_CODE) {
            Issue issue = data.getParcelableExtra("issue");
            mPresenter.setIssue(issue);
            issueTitle.setText(issue.getTitle());
            issueTimelineFragment.onEditIssue(issue);
        } else if (requestCode == NEW_SUB_ISSUE_REQUEST_CODE) {
            Issue created = data == null ? null : data.getParcelableExtra("issue");
            if (created != null) subIssuesBox().addCreated(created);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void finishAfterTransition() {
        // hide the FAB before the shared element transition back, on Back and on Up
        editBn.setVisibility(View.GONE);
        super.finishAfterTransition();
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

    public void onUserAvatarClick() {
        if (mPresenter.getIssue() != null) {
            Issue issue = mPresenter.getIssue();
            ProfileActivity.show(getActivity(), userImageView,
                    issue.getUser().getLogin(), issue.getUser().getAvatarUrl());
        }
    }

    @Override
    protected void onToolbarDoubleClick() {
        super.onToolbarDoubleClick();
        if (issueTimelineFragment != null) issueTimelineFragment.scrollToTop();
    }

    @Override
    public void onScrollUp() {
        commentBn.zoomIn();
    }

    @Override
    public void onScrollDown() {
        commentBn.zoomOut();
    }

    private void showToggleIssueWarning(){
        String msg = String.format(getString(R.string.toggle_issue_warning),
                mPresenter.getIssue().getState().equals(Issue.IssueState.open) ?
                getString(R.string.close) : getString(R.string.reopen));
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.warning_dialog_tile)
                .setMessage(msg)
                .setPositiveButton(R.string.confirm, (dialog, which) -> mPresenter.toggleIssueState())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        userImageView = root.findViewById(R.id.user_avatar);
        issueTitle = root.findViewById(R.id.issue_title);
        issueStateImg = root.findViewById(R.id.issue_state_img);
        issueStateText = root.findViewById(R.id.issue_state_text);
        commentBn = root.findViewById(R.id.comment_bn);
        editBn = root.findViewById(R.id.edit_bn);
        loader = root.findViewById(R.id.loader);
        ViewUtils.setOnClick(root, R.id.comment_bn, v -> onCommentBnClicked());
        ViewUtils.setOnClick(root, R.id.edit_bn, v -> onEditBnClicked());
        ViewUtils.setOnClick(root, R.id.user_avatar, v -> onUserAvatarClick());
    }
}
