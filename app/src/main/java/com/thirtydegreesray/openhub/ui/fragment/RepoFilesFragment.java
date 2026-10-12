

package com.thirtydegreesray.openhub.ui.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;

import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerFragmentComponent;
import com.thirtydegreesray.openhub.inject.module.FragmentModule;
import com.thirtydegreesray.openhub.mvp.contract.IRepoFilesContract;
import com.thirtydegreesray.openhub.mvp.model.Branch;
import com.thirtydegreesray.openhub.mvp.model.FileModel;
import com.thirtydegreesray.openhub.mvp.model.FilePath;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.RepoFilesPresenter;
import com.thirtydegreesray.openhub.ui.activity.RepositoryActivity;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.activity.base.PagerActivity;
import com.thirtydegreesray.openhub.ui.adapter.FilePathAdapter;
import com.thirtydegreesray.openhub.ui.adapter.RepoFilesAdapter;
import com.thirtydegreesray.openhub.ui.fragment.base.ListFragment;
import com.thirtydegreesray.openhub.util.BundleHelper;

import android.content.Intent;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thirtydegreesray.openhub.mvp.presenter.ReleaseInfoPresenter;
import com.thirtydegreesray.openhub.ui.activity.EditFileActivity;
import com.thirtydegreesray.openhub.ui.widget.RepoFileActions;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.ContentFiles;
import com.thirtydegreesray.openhub.util.RepoChanges;

import java.util.ArrayList;

import javax.inject.Inject;


/**
 * Created by ThirtyDegreesRay on 2017/8/14 16:13:20
 */

public class RepoFilesFragment extends ListFragment<RepoFilesPresenter, RepoFilesAdapter>
        implements IRepoFilesContract.View, PagerActivity.IFragmentBackHandler,
        RepositoryActivity.RepositoryListener{

    public static RepoFilesFragment create(Repository repository) {
        RepoFilesFragment fragment = new RepoFilesFragment();
        fragment.setArguments(BundleHelper.builder().put("repo", repository).build());
        return fragment;
    }

    RecyclerView pathRecyclerView;
    FloatingActionButton addButton;
    private long loadedStamp = RepoChanges.stamp();

    private final ActivityResultLauncher<Intent> uploadPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK || getActivity() == null) return;
                RepoFileActions.upload(getActivity(), owner(), repoName(), mPresenter.getBranch(),
                        mPresenter.getCurPath(), ContentFiles.fromPickerResult(getActivity(), result.getData()),
                        mPresenter.getShownNames(), this::reloadAfterChange);
            });
    @Inject FilePathAdapter filePathAdapter;

    @Override
    public void showFiles(ArrayList<FileModel> files) {
        adapter.setData(files);
        postNotifyDataSetChanged();
    }

    @Override
    public void showFilePath(final ArrayList<FilePath> filePath) {
        if (getActivity() instanceof BaseActivity) ((BaseActivity) getActivity()).invalidateBack();
        filePathAdapter.setData(filePath);
        filePathAdapter.notifyDataSetChanged();
        if(filePath.size() != 0)
            pathRecyclerView.post(new Runnable() {
                @Override
                public void run() {
                    pathRecyclerView.smoothScrollToPosition(filePath.size() - 1);
                }
            });
    }

    @Override
    public void showLoading() {
        refreshLayout.setRefreshing(true);
    }

    @Override
    public void hideLoading() {
        refreshLayout.setRefreshing(false);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_repo_files;
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
        super.initFragment(savedInstanceState);
        pathRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        pathRecyclerView.setAdapter(filePathAdapter);
        filePathAdapter.setOnItemClickListener((position, view) ->
                mPresenter.loadFiles(filePathAdapter.getData().get(position).getFullPath(), false)
        );
        updateAddButton();
    }

    // ---------------------------------------------------------------------------------
    // Writing (push access)
    // ---------------------------------------------------------------------------------

    private boolean canEdit() {
        return mPresenter != null && mPresenter.getRepo() != null
                && ReleaseInfoPresenter.hasPushAccess(mPresenter.getRepo());
    }

    private String owner() {
        return mPresenter.getRepo().getOwner().getLogin();
    }

    private String repoName() {
        return mPresenter.getRepo().getName();
    }

    private void updateAddButton() {
        if (addButton == null) return;
        if (canEdit()) addButton.show();
        else addButton.hide();
    }

    private void showAddMenu(View anchor) {
        if (!canEdit()) return;
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.file_create);
        popup.getMenu().add(0, 2, 1, R.string.file_upload);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(EditFileActivity.createIntent(requireContext(), owner(), repoName(),
                        mPresenter.getBranch(), mPresenter.getCurPath()));
            } else {
                uploadPicker.launch(ContentFiles.pickerIntent());
            }
            return true;
        });
        popup.show();
    }

    private void showFileMenu(@NonNull View anchor, @NonNull FileModel file) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        boolean edit = canEdit();
        if (edit) popup.getMenu().add(0, 1, 0, R.string.file_edit);
        if (edit) popup.getMenu().add(0, 2, 1, R.string.file_rename);
        if (edit) popup.getMenu().add(0, 3, 2, R.string.file_delete);
        if (file.getDownloadUrl() != null) popup.getMenu().add(0, 4, 3, R.string.download);
        popup.setOnMenuItemClickListener(item -> {
            String branch = mPresenter.getBranch();
            if (item.getItemId() == 1) {
                startActivity(EditFileActivity.editIntent(requireContext(), owner(), repoName(),
                        branch, file.getPath()));
            } else if (item.getItemId() == 2) {
                RepoFileActions.rename(requireActivity(), owner(), repoName(), branch, file.getPath(),
                        file.getSha(), this::reloadAfterChange);
            } else if (item.getItemId() == 3) {
                RepoFileActions.delete(requireActivity(), owner(), repoName(), branch, file.getPath(),
                        this::reloadAfterChange);
            } else if (item.getItemId() == 4) {
                AppOpener.startDownload(requireContext(), file.getDownloadUrl(), file.getName());
            }
            return true;
        });
        popup.show();
    }

    private void reloadAfterChange() {
        loadedStamp = RepoChanges.stamp();
        if (mPresenter != null) mPresenter.loadFiles(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPresenter != null && RepoChanges.changedSince(loadedStamp)) reloadAfterChange();
    }

    @Override
    public boolean onItemLongClick(int position, @NonNull View view) {
        FileModel model = adapter.getData().get(position);
        if (model.isDir()) return false;
        showFileMenu(view, model);
        return true;
    }

    @Override
    protected void onReLoadData() {
        mPresenter.loadFiles(true);
    }

    @Override
    protected String getEmptyTip() {
        return getString(R.string.no_file);
    }

    @Override
    public void onItemClick(int position, @NonNull View view) {
        super.onItemClick(position, view);
        FileModel model = adapter.getData().get(position);
        if(model.isDir()){
            mPresenter.loadFiles(model.getPath(), false);
        }else{
            ViewerActivity.show(getActivity(), model, mPresenter.getRepoName());
        }
    }

    @Override
    public boolean canHandleBack() {
        return mPresenter != null && mPresenter.canGoBack();
    }

    @Override
    public void handleBack() {
        mPresenter.goBack();
    }

    @Override
    public void onFragmentShowed() {
        super.onFragmentShowed();
        if(mPresenter != null) mPresenter.prepareLoadData();
    }

    @Override
    public void onRepositoryInfoUpdated(Repository repository) {
        if (mPresenter != null && repository != null) {
            mPresenter.setRepo(repository);
            updateAddButton();
        }
    }

    @Override
    public void onBranchChanged(Branch branch) {
        if(mPresenter == null){
            getArguments().putString("branch", branch.getName());
        } else {
            mPresenter.setCurBranch(branch.getName());
            mPresenter.setLoaded(false);
            mPresenter.prepareLoadData();
        }
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        pathRecyclerView = root.findViewById(R.id.path_recycler_view);
        addButton = root.findViewById(R.id.fragment_fab);
        if (addButton != null) addButton.setOnClickListener(this::showAddMenu);
    }
}
