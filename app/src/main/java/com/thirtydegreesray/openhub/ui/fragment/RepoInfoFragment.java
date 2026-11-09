

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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.ForkSyncService;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.PullService;
import com.thirtydegreesray.openhub.http.RepoSettingsService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IRepoInfoContract;
import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.RepoInfoPresenter;
import com.thirtydegreesray.openhub.ui.activity.ActionsActivity;
import com.thirtydegreesray.openhub.ui.activity.CommitsListActivity;
import com.thirtydegreesray.openhub.ui.activity.EditFileActivity;
import com.thirtydegreesray.openhub.ui.activity.IssuesActivity;
import com.thirtydegreesray.openhub.ui.activity.NewPullRequestActivity;
import com.thirtydegreesray.openhub.ui.activity.ProfileActivity;
import com.thirtydegreesray.openhub.ui.activity.PullRequestsActivity;
import com.thirtydegreesray.openhub.ui.activity.RepoListActivity;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.util.PullRefresh;
import com.thirtydegreesray.openhub.ui.activity.UserListActivity;
import com.thirtydegreesray.openhub.ui.fragment.base.BaseFragment;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ButtonStyles;
import com.thirtydegreesray.openhub.util.ForkSync;
import com.thirtydegreesray.openhub.util.Json;
import com.thirtydegreesray.openhub.util.PullChanges;
import com.thirtydegreesray.openhub.util.RepoChanges;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.HashMap;
import java.util.Map;


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

    View forkSyncBox;
    TextView forkSyncText;
    View forkSyncButtons;
    Button forkContributeBn;
    Button forkSyncBn;
    private Disposable forkCheck;
    private Disposable forkAction;
    /** The last comparison of the shown fork branch with upstream (GitHub's numbers). */
    @Nullable private ForkState forkState;

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
        // pull requests switched off in the repository settings: no Pulls tile, like github.com
        View pullsTile = getView() == null ? null : getView().findViewById(R.id.pulls_lay);
        if (pullsTile != null) pullsTile.setVisibility(repository.isHasPullRequests() ? View.VISIBLE : View.GONE);
        // open_issues_count includes open pull requests: until they are counted, show the
        // last known split for this repository, or "-" (it used to show 2, then 0)
        int openIssues = repository.getOpenIssuesCount();
        Integer knownPulls = repository.getFullName().equals(pullsRepo) ? pullsKnown : null;
        issuesNumText.setText(openIssues == 0 ? "0" : knownPulls != null
                ? String.valueOf(Math.max(0, openIssues - knownPulls)) : "-");
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
        checkFork(repository);

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
    /** The last pull request count, and the repository it belongs to. */
    @Nullable private String pullsRepo;
    @Nullable private Integer pullsKnown;

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
                    pullsRepo = fullName;
                    pullsKnown = count;
                    if (pullsNumText == null) return;
                    pullsNumText.setText(String.valueOf(count));
                    issuesNumText.setText(String.valueOf(Math.max(0, openIssues - count)));
                }, error -> {
                    // no count (offline, search limit): the number GitHub gives
                    if (issuesNumText != null && "-".contentEquals(issuesNumText.getText())) {
                        issuesNumText.setText(String.valueOf(openIssues));
                    }
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
        if (forkCheck != null) forkCheck.dispose();
        if (forkAction != null) forkAction.dispose();
        readmeEdit = null;
        forkSyncBox = null;
        super.onDestroyView();
    }

    private void openPulls() {
        Repository repository = mPresenter.getRepository();
        boolean canCreate = repository.isHasPullRequests() && repository.getPermissions() != null
                && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
        startActivity(PullRequestsActivity.createIntent(requireContext(), repository.getOwner().getLogin(),
                repository.getName(), canCreate, repository.getPermissions() != null
                        && repository.getPermissions().canTriage()));
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

    /** Pull down: the repository, its README and the pull request count from GitHub. */
    @Override
    protected PullRefresh createPullRefresh(@NonNull View root) {
        return PullRefresh.wrap(root, this::reloadFromPull).follow(readmeLoader);
    }

    private void reloadFromPull() {
        if (mPresenter == null || mPresenter.getRepository() == null) {
            pullDone();
            return;
        }
        if (getActivity() instanceof RepositoryActivity) {
            ((RepositoryActivity) getActivity()).reloadRepository();
        }
        mPresenter.reloadReadMe();
        countPulls(mPresenter.getRepository());
        checkFork(mPresenter.getRepository());
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
        forkSyncBox = root.findViewById(R.id.fork_sync_box);
        forkSyncText = root.findViewById(R.id.fork_sync_text);
        forkSyncText.setMovementMethod(LinkMovementMethod.getInstance());
        forkSyncButtons = root.findViewById(R.id.fork_sync_buttons);
        forkContributeBn = root.findViewById(R.id.fork_contribute_bn);
        forkSyncBn = root.findViewById(R.id.fork_sync_bn);
        forkContributeBn.setOnClickListener(v -> onContributeClicked());
        forkSyncBn.setOnClickListener(v -> onSyncClicked());
    }

    // ---------------------------------------------------------------------------------
    // Sync fork
    // ---------------------------------------------------------------------------------

    /** A fork branch and its upstream branch, with GitHub's ahead / behind numbers. */
    private static final class ForkState {
        String owner;
        String repo;
        String branch;
        String defaultBranch;
        String parentFullName;
        String upstream;
        int ahead;
        int behind;
        boolean canPush;
        Repository parent;

        String label() {
            return ForkSync.label(parentFullName, upstream);
        }

        String upstreamRef() {
            return ForkSync.ownerOf(parentFullName) + ":" + upstream;
        }
    }

    private static boolean canPush(@NonNull Repository repository) {
        return repository.getPermissions() != null
                && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
    }

    /**
     * Compares the shown branch of a fork with its upstream branch (always from GitHub).
     * The default branches are read from GitHub too: the branch switcher of the repository
     * screen overwrites Repository.defaultBranch with the chosen branch (old OpenHub code),
     * so tears looked like the default branch and was compared with upstream main.
     */
    private void checkFork(@Nullable Repository repository) {
        if (forkSyncBox == null) return;
        if (forkCheck != null) forkCheck.dispose();
        Repository parent = repository == null ? null : repository.getParent();
        if (repository == null || !repository.isFork() || parent == null
                || StringUtils.isBlank(parent.getFullName()) || mPresenter == null) {
            forkState = null;
            forkSyncBox.setVisibility(View.GONE);
            return;
        }
        ForkState state = new ForkState();
        state.owner = repository.getOwner().getLogin();
        state.repo = repository.getName();
        state.branch = mPresenter.getBranch();
        state.parentFullName = parent.getFullName();
        state.parent = parent;
        state.canPush = canPush(repository);
        forkSyncBox.setVisibility(View.VISIBLE);
        forkSyncButtons.setVisibility(View.GONE);
        forkSyncText.setText(getString(R.string.fork_checking, state.parentFullName));
        forkCheck = Single.fromCallable(() -> {
                    JsonObject repoJson = GitHubApi.call(GitHubApi.service(RepoSettingsService.class)
                            .getRepo(state.owner, state.repo));
                    String parentFullName = Json.str(repoJson, "parent.full_name");
                    if (StringUtils.isBlank(parentFullName)) throw new NotAForkException();
                    state.parentFullName = parentFullName;
                    state.defaultBranch = Json.str(repoJson, "default_branch");
                    state.upstream = ForkSync.upstreamBranch(state.branch, state.defaultBranch,
                            Json.str(repoJson, "parent.default_branch"));
                    String basehead = ForkSync.basehead(ForkSync.ownerOf(parentFullName), state.upstream, state.branch);
                    return GitHubApi.call(GitHubApi.service(PullService.class)
                            .compare(state.owner, state.repo, basehead));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    state.ahead = Json.integer(result, "ahead_by");
                    state.behind = Json.integer(result, "behind_by");
                    forkState = state;
                    showForkState(state);
                }, error -> {
                    forkState = null;
                    if (forkSyncBox == null) return;
                    if (state.upstream != null && error instanceof GitHubApi.ApiException
                            && ((GitHubApi.ApiException) error).code == 404) {
                        // no branch with this name upstream: nothing to compare or sync
                        forkSyncText.setText(getString(R.string.fork_no_upstream_branch,
                                state.parentFullName, state.upstream));
                    } else {
                        forkSyncBox.setVisibility(View.GONE);
                    }
                });
    }

    /** GitHub no longer names a parent (the original was deleted or the fork detached). */
    private static final class NotAForkException extends java.io.IOException {
    }

    /** "This branch is 4 commits ahead of and 8 commits behind owner/repo:main.", with links. */
    private void showForkState(@NonNull ForkState state) {
        if (forkSyncBox == null || !isAdded()) return;
        String label = state.label();
        String aheadText = state.ahead > 0 ? getResources().getQuantityString(R.plurals.fork_ahead,
                state.ahead, state.ahead) : null;
        String behindText = state.behind > 0 ? getResources().getQuantityString(R.plurals.fork_behind,
                state.behind, state.behind) : null;
        String text;
        switch (ForkSync.state(state.ahead, state.behind)) {
            case DIVERGED:
                text = getString(R.string.fork_status_both, aheadText, behindText, label);
                break;
            case AHEAD:
                text = getString(R.string.fork_status_one, aheadText, label);
                break;
            case BEHIND:
                text = getString(R.string.fork_status_one, behindText, label);
                break;
            default:
                text = getString(R.string.fork_up_to_date, label);
        }
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        if (aheadText != null) {
            // the fork's own commits
            link(spannable, aheadText, () -> CommitsListActivity.showForCompare(requireActivity(),
                    state.owner, state.repo, state.upstreamRef(), state.branch));
        }
        if (behindText != null) {
            // the new commits upstream
            link(spannable, behindText, () -> CommitsListActivity.showForCompare(requireActivity(),
                    state.owner, state.repo, state.branch, state.upstreamRef()));
        }
        link(spannable, label, () -> RepositoryActivity.show(requireContext(), state.parent));
        forkSyncText.setText(spannable);

        forkSyncButtons.setVisibility(state.canPush ? View.VISIBLE : View.GONE);
        if (state.behind > 0) {
            ButtonStyles.filled(forkSyncBn);
        } else {
            ButtonStyles.tonal(forkSyncBn);
        }
    }

    private void link(@NonNull SpannableStringBuilder spannable, @NonNull String part, @NonNull Runnable onClick) {
        int start = spannable.toString().lastIndexOf(part);
        if (start < 0) return;
        int accent = ViewUtils.getAccentColor(requireContext());
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                onClick.run();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(accent);
                ds.setUnderlineText(false);
            }
        }, start, start + part.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void onSyncClicked() {
        ForkState state = forkState;
        if (state == null) return;
        String label = state.label();
        if (state.behind == 0) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.fork_sync_title)
                    .setMessage(getString(R.string.fork_not_behind, label))
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        String newCommits = getResources().getQuantityString(R.plurals.fork_new_commits,
                state.behind, state.behind);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.fork_sync_title)
                .setPositiveButton(R.string.fork_update_branch, (d, w) -> syncFork(state))
                .setNegativeButton(R.string.cancel, null);
        if (state.ahead == 0) {
            builder.setMessage(getString(R.string.fork_sync_fast_forward, label, newCommits, state.branch));
        } else {
            String own = getResources().getQuantityString(R.plurals.fork_own_commits, state.ahead, state.ahead);
            builder.setMessage(getString(R.string.fork_sync_diverged, label, newCommits, state.branch, own));
            builder.setNeutralButton(getResources().getQuantityString(R.plurals.fork_discard,
                    state.ahead, state.ahead), (d, w) -> confirmDiscard(state));
        }
        builder.show();
    }

    /** POST merge-upstream: GitHub fast-forwards or merges, or answers 409 for a conflict. */
    private void syncFork(@NonNull ForkState state) {
        if (forkAction != null) forkAction.dispose();
        showProgressDialog(getString(R.string.fork_syncing, state.label()));
        Map<String, Object> body = new HashMap<>();
        body.put("branch", state.branch);
        forkAction = Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(ForkSyncService.class)
                        .mergeUpstream(state.owner, state.repo, body)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    hideProgress();
                    if (!isAdded()) return;
                    String message;
                    // GitHub names the branch it synced from ("owner:branch")
                    String from = Json.str(result, "base_branch");
                    String source = StringUtils.isBlank(from) ? state.label() : from;
                    switch (ForkSync.result(Json.str(result, "merge_type"))) {
                        case FAST_FORWARD:
                            message = getString(R.string.fork_synced_fast_forward, state.branch, source);
                            break;
                        case MERGE:
                            message = getString(R.string.fork_synced_merge, state.branch, source);
                            break;
                        case NONE:
                            message = getString(R.string.fork_synced_none, state.branch, source);
                            break;
                        default:
                            String said = Json.str(result, "message");
                            message = getString(R.string.fork_synced_other,
                                    StringUtils.isBlank(said) ? result.toString() : said);
                    }
                    afterSync(state, message);
                }, error -> {
                    hideProgress();
                    if (!isAdded()) return;
                    if (error instanceof GitHubApi.ApiException && ((GitHubApi.ApiException) error).code == 409) {
                        showConflict(state);
                    } else {
                        showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                    }
                });
    }

    private void showConflict(@NonNull ForkState state) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.fork_conflict_title)
                .setMessage(getString(R.string.fork_conflict_message, state.label(), state.branch))
                .setPositiveButton(R.string.fork_open_on_github, (d, w) -> AppOpener.openInBrowser(requireContext(),
                        ForkSync.conflictUrl(state.owner + "/" + state.repo, state.branch,
                                state.parentFullName, state.upstream)))
                .setNegativeButton(R.string.close, null);
        if (state.ahead > 0) {
            builder.setNeutralButton(getResources().getQuantityString(R.plurals.fork_discard,
                    state.ahead, state.ahead), (d, w) -> confirmDiscard(state));
        }
        builder.show();
    }

    private void confirmDiscard(@NonNull ForkState state) {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.fork_discard_title)
                .setMessage(getResources().getQuantityString(R.plurals.fork_discard_message,
                        state.ahead, state.ahead, state.branch, state.label()))
                .setPositiveButton(getResources().getQuantityString(R.plurals.fork_discard,
                        state.ahead, state.ahead), (d, w) -> discard(state))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Discard: the branch is set to the upstream branch's newest commit (forks share their
     * objects with upstream), like github.com and "gh repo sync --force". The commit is read
     * again right before, so the branch gets what upstream has NOW.
     */
    private void discard(@NonNull ForkState state) {
        if (forkAction != null) forkAction.dispose();
        showProgressDialog(getString(R.string.fork_syncing, state.label()));
        String basehead = ForkSync.basehead(ForkSync.ownerOf(state.parentFullName), state.upstream, state.branch);
        forkAction = Single.fromCallable(() -> {
                    String sha = Json.str(GitHubApi.call(GitHubApi.service(PullService.class)
                            .compare(state.owner, state.repo, basehead)), "base_commit.sha");
                    if (StringUtils.isBlank(sha)) throw new java.io.IOException("GitHub did not name the upstream commit.");
                    Map<String, Object> body = new HashMap<>();
                    body.put("sha", sha);
                    body.put("force", true);
                    return GitHubApi.call(GitHubApi.service(GitDataService.class)
                            .updateBranchRef(state.owner, state.repo, RepoCommitter.encode(state.branch), body));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    hideProgress();
                    if (!isAdded()) return;
                    afterSync(state, getString(R.string.fork_synced_discard, state.branch, state.label()));
                }, error -> {
                    hideProgress();
                    if (!isAdded()) return;
                    showErrorToast(StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage());
                });
    }

    /** After a sync: everything reloads from GitHub, then Actions is one tap away (builds). */
    private void afterSync(@NonNull ForkState state, @NonNull String message) {
        RepoChanges.markChanged();
        PullRefresh.freshFromNow();
        if (getActivity() instanceof RepositoryActivity) {
            ((RepositoryActivity) getActivity()).reloadRepository();
        }
        if (mPresenter != null) {
            mPresenter.reloadReadMe();
            checkFork(mPresenter.getRepository());
        }
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.fork_synced_title)
                .setMessage(message)
                .setPositiveButton(R.string.fork_open_actions, (d, w) -> ActionsActivity.show(requireActivity(),
                        state.owner, state.repo, state.defaultBranch, state.canPush))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void onContributeClicked() {
        ForkState state = forkState;
        if (state == null) return;
        if (state.ahead == 0) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.fork_contribute)
                    .setMessage(getString(R.string.fork_not_ahead, state.label()))
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        String own = getResources().getQuantityString(R.plurals.fork_own_commits, state.ahead, state.ahead);
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.fork_contribute)
                .setMessage(getString(R.string.fork_contribute_message, state.branch, own, state.label()))
                .setPositiveButton(R.string.fork_open_pull, (d, w) -> startActivity(
                        NewPullRequestActivity.createIntent(requireContext(), state.owner, state.repo)
                                .putExtra("targetFullName", state.parentFullName)
                                .putExtra("base", state.upstream)
                                .putExtra("head", state.branch)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void hideProgress() {
        try {
            dismissProgressDialog();
        } catch (RuntimeException ignored) {
            // the fragment was rebuilt: no dialog to close
        }
    }
}
