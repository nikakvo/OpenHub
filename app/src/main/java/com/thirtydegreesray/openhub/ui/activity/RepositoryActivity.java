

package com.thirtydegreesray.openhub.ui.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.thirtydegreesray.openhub.util.ReleaseWatcher;
import com.thirtydegreesray.openhub.util.RepoChanges;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.thirtydegreesray.openhub.AppData;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.IRepositoryContract;
import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.RepositoryPresenter;
import com.thirtydegreesray.openhub.ui.activity.base.PagerActivity;
import com.thirtydegreesray.openhub.ui.adapter.BranchesAdapter;
import com.thirtydegreesray.openhub.ui.adapter.base.BaseViewHolder;
import com.thirtydegreesray.openhub.ui.adapter.base.FragmentPagerModel;
import com.thirtydegreesray.openhub.ui.fragment.ActivityFragment;
import com.thirtydegreesray.openhub.ui.fragment.CommitsFragment;
import com.thirtydegreesray.openhub.ui.fragment.RepoFilesFragment;
import com.thirtydegreesray.openhub.ui.fragment.RepoInfoFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StarWishesHelper;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.Locale;


/**
 * Created by ThirtyDegreesRay on 2017/8/9 21:39:20
 */
//FIXME fix fragment not showImage from background
public class RepositoryActivity extends PagerActivity<RepositoryPresenter>
        implements IRepositoryContract.View {

    public static void show(@NonNull Context activity, @NonNull String owner,
                            @NonNull String repoName) {
        Intent intent = createIntent(activity, owner, repoName);
        activity.startActivity(intent);
    }

    public static void show(@NonNull Context activity, @NonNull Repository repository) {
        Intent intent = new Intent(activity, RepositoryActivity.class);
        intent.putExtra("repository", repository);
        activity.startActivity(intent);
    }

    public static Intent createIntent(@NonNull Context activity, @NonNull String owner,
                            @NonNull String repoName) {
        return new Intent(activity, RepositoryActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("owner", owner)
                        .put("repoName", repoName)
                        .build());
    }

    ImageView userImageViewBg;
    ProgressBar loader;
    TextView desc;
    TextView info;

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
        return R.layout.activity_repository;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(mPresenter.getRepository() != null){
            getMenuInflater().inflate(R.menu.menu_repository, menu);
            MenuItem starItem = menu.findItem(R.id.action_star);
            MenuItem bookmark = menu.findItem(R.id.action_bookmark);
            starItem.setTitle(mPresenter.isStarred() ? R.string.unstar : R.string.star);
            starItem.setIcon(mPresenter.isStarred() ?
                    R.drawable.ic_star_title : R.drawable.ic_un_star_title);
            menu.findItem(R.id.action_watch).setTitle(mPresenter.isWatched() ?
                    R.string.unwatch : R.string.watch);
            menu.findItem(R.id.action_fork).setTitle(mPresenter.isFork() ?
                    R.string.forked : R.string.fork);
            menu.findItem(R.id.action_fork).setVisible(mPresenter.isForkEnable());
            menu.findItem(R.id.action_watch_releases).setChecked(ReleaseWatcher.isWatched(this,
                    getRepoFullName()));
            bookmark.setTitle(mPresenter.isBookmarked() ?
                    getString(R.string.remove_bookmark) : getString(R.string.bookmark));
            menu.findItem(R.id.action_wiki).setVisible(mPresenter.getRepository().isHasWiki());
            menu.findItem(R.id.action_discussions).setVisible(mPresenter.getRepository().isHasDiscussions());
            Repository repository = mPresenter.getRepository();
            menu.findItem(R.id.action_repo_settings).setVisible(repository.getPermissions() != null
                    && repository.getPermissions().isAdmin());
            // alerts are for people who can push; secrets for admins
            menu.findItem(R.id.action_security).setVisible(repository.getPermissions() != null
                    && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin()));
        }
        return true;
    }

    /** Reopens the repository after its settings changed; closes it when it is gone. */
    private final androidx.activity.result.ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                int code = result.getResultCode();
                if (code == RepoSettingsActivity.RESULT_GONE) {
                    finish();
                } else if (code == RepoSettingsActivity.RESULT_MOVED && result.getData() != null) {
                    RepositoryActivity.show(this, result.getData().getStringExtra("owner"),
                            result.getData().getStringExtra("repo"));
                    finish();
                } else if (code == RESULT_OK) {
                    RepositoryActivity.show(this, mPresenter.getRepository().getOwner().getLogin(),
                            mPresenter.getRepository().getName());
                    finish();
                }
            });

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        toolbar.setTitleTextAppearance(getActivity(), R.style.Toolbar_TitleText);
        toolbar.setSubtitleTextAppearance(getActivity(), R.style.Toolbar_Subtitle);
        setToolbarBackEnable();
        setToolbarTitle(mPresenter.getRepoName());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_star) {
            starRepo(!mPresenter.isStarred());
            return true;
        } else if (itemId == R.id.action_branch) {
            mPresenter.loadBranchesAndTags();
            return true;
        } else if (itemId == R.id.action_share) {
            AppOpener.shareText(getActivity(), mPresenter.getRepository().getHtmlUrl());
            return true;
        } else if (itemId == R.id.action_open_in_browser) {
            AppOpener.openInCustomTabsOrBrowser(getActivity(), mPresenter.getRepository().getHtmlUrl());
            return true;
        } else if (itemId == R.id.action_copy_url) {
            AppUtils.copyToClipboard(getActivity(), mPresenter.getRepository().getHtmlUrl());
            return true;
        } else if (itemId == R.id.action_copy_clone_url) {
            AppUtils.copyToClipboard(getActivity(), mPresenter.getRepository().getCloneUrl());
            return true;
        } else if (itemId == R.id.action_watch) {
            mPresenter.watchRepo(!mPresenter.isWatched());
            invalidateOptionsMenu();
            showSuccessToast(mPresenter.isWatched() ?
                    getString(R.string.watched) : getString(R.string.unwatched));
            return true;
        } else if (itemId == R.id.action_fork) {
            if(!mPresenter.getRepository().isFork()) forkRepo();
            return true;
        } else if (itemId == R.id.action_watch_releases) {
            toggleReleaseNotifications(item);
            return true;
        } else if (itemId == R.id.action_latest_release) {
            ReleaseInfoActivity.showLatest(getActivity(),
                    mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName());
            return true;
        } else if (itemId == R.id.action_releases) {
            showReleases();
            return true;
        } else if (itemId == R.id.action_actions) {
            Repository repository = mPresenter.getRepository();
            boolean canRun = repository.getPermissions() != null
                    && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
            ActionsActivity.show(getActivity(), repository.getOwner().getLogin(),
                    repository.getName(), repository.getDefaultBranch(), canRun);
            return true;
        } else if (itemId == R.id.action_security) {
            Repository repository = mPresenter.getRepository();
            startActivity(SecurityActivity.createIntent(this, repository.getOwner().getLogin(),
                    repository.getName(), repository.getPermissions() != null
                            && repository.getPermissions().isAdmin()));
            return true;
        } else if (itemId == R.id.action_pulls) {
            Repository repository = mPresenter.getRepository();
            boolean canCreate = repository.getPermissions() != null
                    && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
            startActivity(PullRequestsActivity.createIntent(this, repository.getOwner().getLogin(),
                    repository.getName(), canCreate));
            return true;
        } else if (itemId == R.id.action_search_code) {
            startActivity(CodeSearchActivity.createIntent(this, mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName()));
            return true;
        } else if (itemId == R.id.action_discussions) {
            startActivity(DiscussionsActivity.createIntent(this, mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName()));
            return true;
        } else if (itemId == R.id.action_branches) {
            startActivity(BranchesActivity.createIntent(this, mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName()));
            return true;
        } else if (itemId == R.id.action_repo_settings) {
            settingsLauncher.launch(RepoSettingsActivity.createIntent(this,
                    mPresenter.getRepository().getOwner().getLogin(), mPresenter.getRepository().getName()));
            return true;
        } else if (itemId == R.id.action_wiki) {
            WikiActivity.show(getActivity(), mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName());
            return true;
        } else if (itemId == R.id.action_download_source_zip) {
            AppOpener.startDownload(getActivity(), mPresenter.getZipSourceUrl(),
                    mPresenter.getZipSourceName());
            return true;
        } else if (itemId == R.id.action_download_source_tar) {
            AppOpener.startDownload(getActivity(), mPresenter.getTarSourceUrl(),
                    mPresenter.getTarSourceName());
            return true;
        } else if (itemId == R.id.action_bookmark) {
            mPresenter.bookmark(!mPresenter.isBookmarked());
            invalidateOptionsMenu();
            showSuccessToast(mPresenter.isBookmarked() ?
                    getString(R.string.bookmark_saved) : getString(R.string.bookmark_removed));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void starRepo(boolean star){
        mPresenter.starRepo(star);
        invalidateOptionsMenu();
        showSuccessToast(mPresenter.isStarred() ?
                getString(R.string.starred) : getString(R.string.unstarred));
    }

    @Override
    public void showRepo(Repository repo) {
//        setToolbarTitle(repo.getFullName(), repo.getDefaultBranch());
        desc.setText(repo.getDescription());
        String language = StringUtils.isBlank(repo.getLanguage()) ?
                getString(R.string.unknown) : repo.getLanguage();
        // GitHub counts the size of a new repository later (it reports 0 until then)
        String size = repo.getSize() == 0 ? getString(R.string.repo_size_pending)
                : StringUtils.getSizeString(repo.getSize() * 1024L);
        info.setText(getString(R.string.repo_info_line, language, size));

        if (pagerAdapter.getCount() == 0) {
            pagerAdapter.setPagerList(FragmentPagerModel
                    .createRepoPagerList(getActivity(), repo, getFragments()));
            tabLayout.setVisibility(View.VISIBLE);
            tabLayout.setupWithViewPager(viewPager);
            viewPager.setAdapter(pagerAdapter);
            showFirstPager();

            GlideApp.with(getActivity())
                    .load(repo.getOwner().getAvatarUrl())
                    .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                    .into(userImageViewBg);
        } else {
            noticeRepositoryUpdated(repo);
        }
        invalidateOptionsMenu();
    }

    @Override
    public void showBranchesAndTags(final ArrayList<Branch> list, Branch curBranch) {
        BranchesAdapter branchesAdapter = new BranchesAdapter(getActivity(), curBranch.getName());
        branchesAdapter.setData(list);

        final RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(branchesAdapter);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setTitle(getString(R.string.select_branch))
                .setView(recyclerView)
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();

        branchesAdapter.setOnItemClickListener(new BaseViewHolder.OnItemClickListener() {
            @Override
            public void onItemClick(int position, @NonNull View view) {
                Branch branch = list.get(position);
                mPresenter.getRepository().setDefaultBranch(branch.getName());
                mPresenter.setCurBranch(branch);
                noticeBranchChanged(branch);
//                showRepo(mPresenter.getRepository());
                alertDialog.dismiss();
            }
        });

    }

    @Override
    public void showStarWishes() {
        String message = getString(R.string.star_wishes);
        String user = AppData.INSTANCE.getLoggedUser().getName();
        user = StringUtils.isBlank(user) ? AppData.INSTANCE.getLoggedUser().getLogin() : user;
        message = String.format(message, user, StarWishesHelper.getInstalledDays());
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.openhub_wishes)
                .setMessage(message)
                .setNegativeButton(R.string.star_next_time, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.star_me, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        starRepo(true);
                        showSuccessToast(getString(R.string.star_thanks));
                    }
                })
                .setCancelable(false)
                .show();
        StarWishesHelper.addStarWishesTipTimes();
    }

    private void forkRepo(){
        new AlertDialog.Builder(getActivity())
                .setCancelable(true)
                .setTitle(R.string.warning_dialog_tile)
                .setMessage(R.string.fork_warning_msg)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setPositiveButton(R.string.fork, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mPresenter.createFork();
                    }
                }).show();
    }

    private String getRepoFullName() {
        return ReleaseWatcher.key(mPresenter.getRepository().getOwner().getLogin(),
                mPresenter.getRepository().getName());
    }

    private void toggleReleaseNotifications(MenuItem item) {
        String fullName = getRepoFullName();
        boolean watch = !ReleaseWatcher.isWatched(this, fullName);
        ReleaseWatcher.setWatched(this, fullName, watch);
        item.setChecked(watch);
        if (watch) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
            showSuccessToast(getString(R.string.watch_releases_on, fullName));
        } else {
            showInfoToast(getString(R.string.watch_releases_off, fullName));
        }
    }

    private void showReleases(){
        ReleasesActivity.show(getActivity(), mPresenter.getRepository().getOwner().getLogin(),
                mPresenter.getRepository().getName());
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

    private void noticeBranchChanged(Branch branch){
        for(FragmentPagerModel pagerModel : pagerAdapter.getPagerList()){
            if(pagerModel.getFragment() instanceof RepositoryListener){
                ((RepositoryListener)pagerModel.getFragment()).onBranchChanged(branch);
            }
        }
    }

    private void noticeRepositoryUpdated(Repository repository){
        for(FragmentPagerModel pagerModel : pagerAdapter.getPagerList()){
            if(pagerModel.getFragment() instanceof RepositoryListener){
                ((RepositoryListener)pagerModel.getFragment()).onRepositoryInfoUpdated(repository);
            }
        }
    }

    public interface RepositoryListener{
        void onRepositoryInfoUpdated(Repository repository);
        void onBranchChanged(Branch branch);
    }

    /** RepoChanges when this screen last showed the repository. */
    private long repoStamp = RepoChanges.stamp();

    @Override
    protected void onResume() {
        super.onResume();
        // a commit on another screen (file edited, uploaded, deleted, PR merged): the header
        // ("Latest commit", size, language) and the Info tab show the new state
        if (RepoChanges.changedSince(repoStamp) && mPresenter != null) {
            repoStamp = RepoChanges.stamp();
            mPresenter.refreshRepository();
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
    }

    @Override
    public int getPagerSize() {
        return 4;
    }

    @Override
    protected int getFragmentPosition(Fragment fragment) {
        if(fragment instanceof RepoInfoFragment){
            return 0;
        }else if(fragment instanceof RepoFilesFragment){
            return 1;
        }else if(fragment instanceof CommitsFragment){
            return 2;
        }else if(fragment instanceof ActivityFragment){
            return 3;
        }else
            return -1;
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        userImageViewBg = root.findViewById(R.id.user_avatar_bg);
        loader = root.findViewById(R.id.loader);
        desc = root.findViewById(R.id.desc);
        info = root.findViewById(R.id.info);
    }
}
