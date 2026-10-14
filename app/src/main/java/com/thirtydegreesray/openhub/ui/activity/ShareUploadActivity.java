package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.ReleaseService;
import com.thirtydegreesray.openhub.http.RepoAdminService;
import com.thirtydegreesray.openhub.http.RepoService;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.ReleaseAsset;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.model.UploadItem;
import com.thirtydegreesray.openhub.mvp.presenter.ReleaseInfoPresenter;
import com.thirtydegreesray.openhub.service.UploadService;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.mvp.model.FileModel;
import com.thirtydegreesray.openhub.ui.widget.RepoFileActions;
import com.thirtydegreesray.openhub.ui.widget.TextInputDialog;
import com.thirtydegreesray.openhub.util.ContentFiles;
import com.thirtydegreesray.openhub.util.RepoCommitter;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Target of "Share" from other apps (file managers, Termux, Telegram...): uploads the
 * shared files to a release. Pick one of your repositories (the last used one comes
 * first), then an existing release or a new one.
 */
public class ShareUploadActivity extends BaseActivity<IBaseContract.Presenter> {

    private static final String PREFS = "share_upload";
    private static final String KEY_LAST_REPO = "last_repo";
    /** + full name: the folder files were last added to in that repository */
    private static final String KEY_LAST_FOLDER = "last_folder_";
    private static final int MAX_PAGES = 3;

    private TextView filesText;
    private TextInputEditText searchEdit;
    private ListView repoList;
    private TextView emptyText;
    private ProgressBar loader;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ArrayList<UploadItem> files;
    private final ArrayList<Repository> repos = new ArrayList<>();
    private final ArrayList<Repository> shown = new ArrayList<>();
    private RepoAdapter adapter;

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_share_upload;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(getString(R.string.share_upload_title), getString(R.string.share_upload_subtitle));
        files = ContentFiles.fromShareIntent(this, getIntent());
        if (files.isEmpty()) {
            showErrorToast(getString(R.string.share_upload_no_files));
            finish();
            return;
        }
        StringBuilder names = new StringBuilder();
        for (UploadItem item : files) {
            if (names.length() > 0) names.append(", ");
            names.append(item.getName());
            if (item.getSize() >= 0) {
                names.append(" (").append(StringUtils.getSizeString(item.getSize())).append(")");
            }
        }
        filesText.setText(getResources().getQuantityString(R.plurals.share_upload_files,
                files.size(), files.size(), names.toString()));

        adapter = new RepoAdapter(this, shown);
        repoList.setAdapter(adapter);
        repoList.setOnItemClickListener((parent, view, position, id) -> onRepoChosen(shown.get(position)));
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter();
            }
        });
        loadRepos();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------
    // Repositories
    // ---------------------------------------------------------------------------------

    private void loadRepos() {
        showLoading();
        background(() -> {
            RepoAdminService service = GitHubApi.service(RepoAdminService.class);
            ArrayList<Repository> all = new ArrayList<>();
            for (int page = 1; page <= MAX_PAGES; page++) {
                List<Repository> list = GitHubApi.call(service.getMyRepos(true, page));
                if (list == null || list.isEmpty()) break;
                for (Repository repository : list) {
                    if (ReleaseInfoPresenter.hasPushAccess(repository)) all.add(repository);
                }
                if (list.size() < 100) break;
            }
            return all;
        }, (ArrayList<Repository> all) -> {
            hideLoading();
            repos.clear();
            repos.addAll(all);
            // the last used repository first
            String last = prefs().getString(KEY_LAST_REPO, null);
            for (int i = 0; i < repos.size(); i++) {
                if (repos.get(i).getFullName().equals(last)) {
                    repos.add(0, repos.remove(i));
                    break;
                }
            }
            filter();
        }, error -> hideLoading());
    }

    private void filter() {
        String query = searchEdit.getText() == null ? ""
                : searchEdit.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Repository repository : repos) {
            if (query.isEmpty() || repository.getFullName().toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(repository);
            }
        }
        adapter.notifyDataSetChanged();
        emptyText.setText(repos.isEmpty() ? R.string.share_upload_no_repos : R.string.share_upload_no_match);
        emptyText.setVisibility(shown.isEmpty() && loader.getVisibility() != View.VISIBLE
                ? View.VISIBLE : View.GONE);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------------------------
    // Releases
    // ---------------------------------------------------------------------------------

    private void onRepoChosen(@NonNull Repository repository) {
        prefs().edit().putString(KEY_LAST_REPO, repository.getFullName()).apply();
        final String owner = repository.getOwner().getLogin();
        final String repo = repository.getName();
        showLoading();
        background(() -> {
            ArrayList<Release> list = GitHubApi.call(GitHubApi.service(ReleaseService.class)
                    .getReleases(true, owner, repo));
            return list == null ? new ArrayList<Release>() : list;
        }, (ArrayList<Release> releases) -> {
            hideLoading();
            chooseRelease(repository, releases);
        }, error -> hideLoading());
    }

    private void chooseRelease(@NonNull Repository repository, @NonNull List<Release> releases) {
        final String owner = repository.getOwner().getLogin();
        final String repo = repository.getName();
        final int first = 2; // + New release, + Add to the files
        String[] items = new String[releases.size() + first];
        items[0] = "+ " + getString(R.string.release_new);
        items[1] = "+ " + getString(R.string.share_files_add, branchOf(repository));
        for (int i = 0; i < releases.size(); i++) {
            Release release = releases.get(i);
            String label = StringUtils.isBlank(release.getTagName())
                    ? getString(R.string.release_draft_badge) : release.getTagName();
            if (release.isDraft() && !StringUtils.isBlank(release.getTagName())) {
                label += " · " + getString(R.string.release_draft_badge);
            } else if (release.isPreRelease()) {
                label += " · " + getString(R.string.release_prerelease_badge);
            }
            if (!StringUtils.isBlank(release.getName()) && !release.getName().equals(release.getTagName())) {
                label += "\n" + release.getName();
            }
            items[i + first] = label;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.share_upload_choose_release, owner + "/" + repo))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(EditReleaseActivity.createIntent(this, owner, repo, files));
                        finish();
                    } else if (which == 1) {
                        addToFiles(repository);
                    } else {
                        uploadTo(owner, repo, releases.get(which - first));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // Repository files (a commit, like the Files tab's Upload)
    // ---------------------------------------------------------------------------------

    @NonNull
    private static String branchOf(@NonNull Repository repository) {
        return StringUtils.isBlank(repository.getDefaultBranch()) ? "main" : repository.getDefaultBranch();
    }

    private void addToFiles(@NonNull Repository repository) {
        final String owner = repository.getOwner().getLogin();
        final String repo = repository.getName();
        final String branch = branchOf(repository);
        final String key = KEY_LAST_FOLDER + repository.getFullName();
        TextInputDialog.show(this, R.string.share_files_folder_title, R.string.share_files_folder_hint,
                prefs().getString(key, ""), false, true, text -> {
                    String dir = RepoCommitter.cleanFolder(text);
                    if (dir == null) {
                        showErrorToast(getString(R.string.share_files_folder_invalid));
                        return;
                    }
                    prefs().edit().putString(key, dir).apply();
                    showLoading();
                    background(() -> existingNames(owner, repo, dir, branch), (Set<String> existing) -> {
                        hideLoading();
                        // the commit dialog: message, this branch or a new one, pull request
                        RepoFileActions.upload(this, owner, repo, branch, dir, files, existing, this::finish);
                    }, error -> hideLoading());
                });
    }

    /** Names in the folder (to ask before replacing); empty for a new folder or repository. */
    @NonNull
    private static Set<String> existingNames(@NonNull String owner, @NonNull String repo,
                                             @NonNull String dir, @NonNull String branch) throws Exception {
        Set<String> names = new HashSet<>();
        try {
            ArrayList<FileModel> list = GitHubApi.call(GitHubApi.service(RepoService.class)
                    .getRepoFiles(owner, repo, RepoCommitter.encode(dir), branch));
            if (list != null) for (FileModel file : list) names.add(file.getName());
        } catch (GitHubApi.ApiException e) {
            // 404: the folder does not exist yet; 409: the repository is empty
            if (e.code != 404 && e.code != 409) throw e;
        }
        return names;
    }

    private void uploadTo(@NonNull String owner, @NonNull String repo, @NonNull Release release) {
        Set<String> names = new HashSet<>();
        for (UploadItem item : files) {
            if (!names.add(item.getName())) {
                showErrorToast(getString(R.string.upload_name_exists, item.getName()));
                return;
            }
        }
        final ArrayList<ReleaseAsset> clashes = new ArrayList<>();
        if (release.getAssets() != null) {
            for (ReleaseAsset asset : release.getAssets()) {
                if (names.contains(asset.getName())) clashes.add(asset);
            }
        }
        if (clashes.isEmpty()) {
            startUpload(owner, repo, release);
            return;
        }
        StringBuilder list = new StringBuilder();
        for (ReleaseAsset asset : clashes) list.append("\n• ").append(asset.getName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.share_upload_replace_title)
                .setMessage(getString(R.string.share_upload_replace_message) + "\n" + list)
                .setPositiveButton(R.string.share_upload_replace, (d, w) -> {
                    showProgressDialog(getString(R.string.please_wait));
                    background(() -> {
                        ReleaseService service = GitHubApi.service(ReleaseService.class);
                        for (ReleaseAsset asset : clashes) {
                            GitHubApi.call(service.deleteAsset(owner, repo, asset.getId()));
                        }
                        return Boolean.TRUE;
                    }, done -> {
                        dismissProgressDialog();
                        startUpload(owner, repo, release);
                    }, error -> dismissProgressDialog());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startUpload(@NonNull String owner, @NonNull String repo, @NonNull Release release) {
        UploadService.start(this, owner, repo, release, files, false, null);
        showInfoToast(getResources().getQuantityString(R.plurals.upload_started, files.size(), files.size()));
        finish();
    }

    // ---------------------------------------------------------------------------------

    private <T> void background(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess,
                                @Nullable Consumer<Throwable> onError) {
        disposables.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess::accept, error -> {
                    showErrorToast(StringUtils.isBlank(error.getMessage())
                            ? error.toString() : error.getMessage());
                    if (onError != null) onError.accept(error);
                }));
    }

    @Override
    public void showLoading() {
        loader.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
    }

    @Override
    public void hideLoading() {
        loader.setVisibility(View.GONE);
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        filesText = root.findViewById(R.id.files_text);
        searchEdit = root.findViewById(R.id.search_edit);
        repoList = root.findViewById(R.id.repo_list);
        emptyText = root.findViewById(R.id.empty_text);
        loader = root.findViewById(R.id.loader);
    }

    private static class RepoAdapter extends ArrayAdapter<Repository> {

        RepoAdapter(@NonNull Context context, @NonNull List<Repository> items) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView != null ? convertView : LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            Repository repository = getItem(position);
            TextView title = view.findViewById(android.R.id.text1);
            TextView subtitle = view.findViewById(android.R.id.text2);
            title.setText(repository == null ? "" : repository.getFullName());
            String description = repository == null ? null : repository.getDescription();
            String visibility = repository != null && repository.isRepPrivate()
                    ? getContext().getString(R.string.share_upload_private) : null;
            String sub = StringUtils.isBlank(description) ? "" : description;
            if (visibility != null) sub = sub.isEmpty() ? visibility : visibility + " · " + sub;
            subtitle.setText(sub);
            subtitle.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
            subtitle.setMaxLines(1);
            return view;
        }
    }
}
