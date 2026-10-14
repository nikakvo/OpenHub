

package com.thirtydegreesray.openhub.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import androidx.core.widget.NestedScrollView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IRepoInfoContract;
import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.RepoInfoPresenter;
import com.thirtydegreesray.openhub.ui.activity.EditFileActivity;
import com.thirtydegreesray.openhub.ui.activity.IssuesActivity;
import com.thirtydegreesray.openhub.ui.activity.ProfileActivity;
import com.thirtydegreesray.openhub.ui.activity.PullRequestsActivity;
import com.thirtydegreesray.openhub.ui.activity.RepoListActivity;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.ui.activity.UserListActivity;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullChanges;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;


/**
 * Created by ThirtyDegreesRay on 2017/8/11 11:35:39
 */

public class RepoInfoFragment extends BaseFragment<RepoInfoPresenter>
        implements IRepoInfoContract.View,
        CodeWebView.ContentChangedListener,
        RepositoryActivity.RepositoryListener {

    public static RepoInfoFragment create(Repository repository) {
        RepoInfoFragment fragment = new RepoInfoFragment();
        fragment.setArguments(BundleHelper.builder().put("repository", repository).build());
        return fragment;
    }

    NestedScrollView nestedScrollView;
    TextView repoTitleText;
    TextView forkInfoText;
//    @BindView(R2.id.repo_desc_text) TextView repoDescText;
    TextView repoCreatedInfoText;
    TextView issuesNumText;
    View issueLay;
    TextView pullsNumText;
    private Disposable pullsCount;
    private long pullsStamp;
    TextView stargazersNumText;
    TextView forksNumText;
    TextView watchersNumText;

    TextView readmeTitle;
    ImageView readmeEdit;
    private Disposable readmeLookup;
    /** RepoChanges when the README was loaded; a commit since then reloads it. */
    private long readmeStamp;
    ProgressBar readmeLoader;
    CodeWebView webView;

    private boolean isReadmeSetted = false;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_repo_info;
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
        webView.setContentChangedListener(this);
    }

    @Override
    public void showRepoInfo(Repository repository) {
//        repoDescText.setVisibility(View.GONE);

        issueLay.setVisibility(repository.isHasIssues() ? View.VISIBLE :View.GONE);
        issuesNumText.setText(String.valueOf(repository.getOpenIssuesCount()));
        countPulls(repository);
        updateReadmeEdit(isReadmeSetted);
        stargazersNumText.setText(String.valueOf(repository.getStargazersCount()));
        forksNumText.setText(String.valueOf(repository.getForksCount()));
        watchersNumText.setText(String.valueOf(repository.getSubscribersCount()));
//        repoDescText.setText(repository.getDescription());

//        String language = StringUtils.isBlank(repository.getLanguage()) ?
//                getString(R.string.unknown) : repository.getLanguage();
//        repoCreatedInfoText.setText(String.format(Locale.getDefault(), "Language %s, size %s",
//                language, StringUtils.getSizeString(repository.getSize() * 1024)));

        String createStr = (repository.isFork() ? getString(R.string.forked_at)
                : getString(R.string.created_at)) + " " + StringUtils.getDateStr(repository.getCreatedAt());
        if(repository.getPushedAt() != null){
            String updateStr = getString(R.string.latest_commit) + " "
                    + StringUtils.getNewsTimeStr(getActivity(), repository.getPushedAt());
            repoCreatedInfoText.setText(String.format("%s, %s", createStr, updateStr));
        } else {
            repoCreatedInfoText.setText(createStr);
        }

        if (repository.isFork() && repository.getParent() != null) {
            forkInfoText.setVisibility(View.VISIBLE);
            forkInfoText.setText(getString(R.string.forked_from)
                    .concat(" ").concat(repository.getParent().getFullName()));
        } else {
            forkInfoText.setVisibility(View.GONE);
        }

        String fullName = repository.getFullName();
        SpannableStringBuilder spannable = new SpannableStringBuilder(fullName);
        spannable.setSpan(new ForegroundColorSpan(ViewUtils.getAccentColor(getContext())),
                0, fullName.indexOf("/"), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                ProfileActivity.show(getActivity(), mPresenter.getRepository().getOwner().getLogin(),
                        mPresenter.getRepository().getOwner().getAvatarUrl());
            }

            @Override
            public void updateDrawState(TextPaint ds) {

            }
        }, 0, fullName.indexOf("/"), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        repoTitleText.setMovementMethod(LinkMovementMethod.getInstance());
        repoTitleText.setText(spannable);
    }

    //TODO fix scroll conflict when have tables
    @Override
    public void showReadMe(String source, String baseUrl) {
        if (!isReadmeSetted) {
            isReadmeSetted = true;
            webView.setMdSource(source, baseUrl, true);
            readmeLoader.setVisibility(View.VISIBLE);
            readmeLoader.setIndeterminate(false);
            webView.setVisibility(View.VISIBLE);
        }
        updateReadmeEdit(true);
    }

    /** The pencil next to README, for users who can push to the repository. */
    private void updateReadmeEdit(boolean hasReadme) {
        if (readmeEdit == null) return;
        Repository repository = mPresenter == null ? null : mPresenter.getRepository();
        boolean canPush = repository != null && repository.getPermissions() != null
                && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
        readmeEdit.setVisibility(hasReadme && canPush ? View.VISIBLE : View.GONE);
    }

    /** Asks GitHub where the README is (README.md, docs/README, ...) and opens the editor. */
    private void editReadme() {
        Repository repository = mPresenter.getRepository();
        if (repository == null) return;
        String owner = repository.getOwner().getLogin();
        String name = repository.getName();
        String branch = mPresenter.getBranch();
        if (readmeLookup != null) readmeLookup.dispose();
        readmeEdit.setEnabled(false);
        readmeLookup = Single.fromCallable(() -> {
                    String path = Json.str(GitHubApi.call(GitHubApi.service(GitDataService.class)
                            .getReadme(owner, name, branch)), "path");
                    return path == null ? "" : path;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(path -> {
                    if (readmeEdit == null) return;
                    readmeEdit.setEnabled(true);
                    if (StringUtils.isBlank(path)) return;
                    startActivity(EditFileActivity.editIntent(requireContext(), owner, name, branch, path));
                }, error -> {
                    if (readmeEdit == null) return;
                    readmeEdit.setEnabled(true);
                    showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                });
    }

    @Override
    public void showReadMeLoader() {
        isReadmeSetted = false;
        readmeStamp = RepoChanges.stamp();
        readmeLoader.setVisibility(View.VISIBLE);
        readmeLoader.setIndeterminate(true);
        webView.setVisibility(View.GONE);
    }

    @Override
    public void showNoReadMe() {
        readmeTitle.setText(R.string.no_readme);
        readmeLoader.setVisibility(View.GONE);
        updateReadmeEdit(false);
    }

    /**
     * GitHub counts open pull requests as open issues too, so the Issues tile shows
     * open_issues_count minus the open pull requests, like the tabs on github.com.
     */
    private void countPulls(@NonNull Repository repository) {
        if (pullsCount != null) pullsCount.dispose();
        pullsStamp = PullChanges.stamp();
        String fullName = repository.getFullName();
        int openIssues = repository.getOpenIssuesCount();
        if (pullsNumText.getText().length() == 0) pullsNumText.setText("-");
        pullsCount = Single.fromCallable(() -> Json.integer(GitHubApi.call(GitHubApi.service(PullService.class)
                        .searchCount("repo:" + fullName + " is:pr is:open")), "total_count"))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(count -> {
                    if (pullsNumText == null) return;
                    pullsNumText.setText(String.valueOf(count));
                    issuesNumText.setText(String.valueOf(Math.max(0, openIssues - count)));
                }, error -> {
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        // a commit on another screen (e.g. the README edited in Files): show the new README
        if (readmeStamp != 0 && RepoChanges.changedSince(readmeStamp) && mPresenter != null
                && mPresenter.getRepository() != null) {
            mPresenter.reloadReadMe();
        }
        if (pullsStamp != 0 && PullChanges.changedSince(pullsStamp) && mPresenter != null
                && mPresenter.getRepository() != null) {
            countPulls(mPresenter.getRepository());
        }
    }

    @Override
    public void onDestroyView() {
        if (pullsCount != null) pullsCount.dispose();
        if (readmeLookup != null) readmeLookup.dispose();
        readmeEdit = null;
        super.onDestroyView();
    }

    private void openPulls() {
        Repository repository = mPresenter.getRepository();
        boolean canCreate = repository.getPermissions() != null
                && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
        startActivity(PullRequestsActivity.createIntent(requireContext(), repository.getOwner().getLogin(),
                repository.getName(), canCreate));
    }

    public void onViewClicked(View view) {
        int viewId = view.getId();
        if (viewId == R.id.pulls_lay) {
            openPulls();
        } else if (viewId == R.id.issues_lay) {
            IssuesActivity.showForRepo(getActivity(), mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName());
//                showInfoToast(getString(R.string.developing));
        } else if (viewId == R.id.stargazers_lay) {
            if(mPresenter.getRepository().getStargazersCount() == 0) return;
            UserListActivity.show(getActivity(), UserListFragment.UserListType.STARGAZERS,
                    mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName());
        } else if (viewId == R.id.froks_lay) {
            if(mPresenter.getRepository().getForksCount() == 0) return;
            RepoListActivity.showForks(getContext(),
                    mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName());
        } else if (viewId == R.id.watchers_lay) {
            if(mPresenter.getRepository().getSubscribersCount() == 0) return;
            UserListActivity.show(getActivity(), UserListFragment.UserListType.WATCHERS,
                    mPresenter.getRepository().getOwner().getLogin(),
                    mPresenter.getRepository().getName());
        } else if (viewId == R.id.fork_info_text) {
            RepositoryActivity.show(getActivity(), mPresenter.getRepository().getParent());
        }
    }

    @Override
    public void onContentChanged(int progress) {
        if (readmeLoader != null) {
            readmeLoader.setProgress(progress);
            if (progress == 100) {
                readmeLoader.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onScrollChanged(boolean reachedTop, int scroll) {

    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        if(mPresenter != null) mPresenter.prepareLoadData();
    }

    @Override
    public void onRepositoryInfoUpdated(Repository repository) {
        if(mPresenter == null){
            getArguments().putParcelable("repository", repository);
        }else{
            mPresenter.setRepository(repository);
            mPresenter.setLoaded(false);
            mPresenter.prepareLoadData();
        }
    }

    @Override
    public void onBranchChanged(Branch branch) {
        if(mPresenter == null){
            getArguments().putString("curBranch", branch.getName());
        }else{
            mPresenter.setCurBranch(branch.getName());
            mPresenter.setLoaded(false);
            mPresenter.prepareLoadData();
        }
    }

    @Override
    public void scrollToTop() {
        super.scrollToTop();
        nestedScrollView.scrollTo(0, 0);
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        nestedScrollView = root.findViewById(R.id.scroll_view);
        repoTitleText = root.findViewById(R.id.repo_title_text);
        forkInfoText = root.findViewById(R.id.fork_info_text);
        repoCreatedInfoText = root.findViewById(R.id.repo_created_info_text);
        issuesNumText = root.findViewById(R.id.issues_num_text);
        issueLay = root.findViewById(R.id.issues_lay);
        pullsNumText = root.findViewById(R.id.pulls_num_text);
        stargazersNumText = root.findViewById(R.id.stargazers_num_text);
        forksNumText = root.findViewById(R.id.forks_num_text);
        watchersNumText = root.findViewById(R.id.watchers_num_text);
        readmeTitle = root.findViewById(R.id.readme_title);
        readmeEdit = root.findViewById(R.id.readme_edit);
        readmeEdit.setOnClickListener(v -> editReadme());
        readmeLoader = root.findViewById(R.id.readme_loader);
        webView = root.findViewById(R.id.web_view);
        ViewUtils.setOnClick(root, R.id.issues_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.pulls_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.stargazers_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.froks_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.watchers_lay, v -> onViewClicked(v));
        ViewUtils.setOnClick(root, R.id.fork_info_text, v -> onViewClicked(v));
    }
}
