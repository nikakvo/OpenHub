

package com.thirtydegreesray.openhub.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.Menu;
import android.view.MenuItem;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.mvp.model.CommitFile;
import com.thirtydegreesray.openhub.mvp.model.FileModel;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.ViewerFragment;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.StringUtils;

/**
 * Created by ThirtyDegreesRay on 2017/8/19 15:05:44
 */

public class ViewerActivity extends SingleFragmentActivity<IBaseContract.Presenter, ViewerFragment> {

    public enum ViewerType{
        RepoFile, MarkDown, DiffFile, Image, HtmlSource, Code
    }

    public static void showHtmlSource(@NonNull Context context, @NonNull String title,
                                    @NonNull String htmlSource){
        Intent intent = new Intent(context, ViewerActivity.class);
        intent.putExtras(BundleHelper.builder().put("viewerType", ViewerType.HtmlSource)
                .put("title", title).put("source", htmlSource).build());
        context.startActivity(intent);
    }

    public static void showMdSource(@NonNull Context context, @NonNull String title,
                                    @NonNull String mdSource){
        Intent intent = new Intent(context, ViewerActivity.class);
        intent.putExtras(BundleHelper.builder().put("viewerType", ViewerType.MarkDown)
                .put("title", title).put("source", mdSource).build());
        context.startActivity(intent);
    }

    /** Highlighted source text; the language comes from the file name's extension. */
    public static void showCode(@NonNull Context context, @NonNull String fileName,
                                @NonNull String source){
        Intent intent = new Intent(context, ViewerActivity.class);
        intent.putExtras(BundleHelper.builder().put("viewerType", ViewerType.Code)
                .put("title", fileName).put("source", source).build());
        context.startActivity(intent);
    }

    public static void showImage(@NonNull Context context, @NonNull String imageUrl){
        String title = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
        showImage(context, title, imageUrl);
    }

    public static void showImage(@NonNull Context context, @NonNull String title,
                                 @NonNull String imageUrl){
        Intent intent = new Intent(context, ViewerActivity.class);
        intent.putExtras(BundleHelper.builder().put("viewerType", ViewerType.Image)
                .put("title", title).put("imageUrl", imageUrl).build());
        context.startActivity(intent);
    }

    public static void show(@NonNull Context context, @NonNull String url
            , @Nullable String htmlUrl){
        show(context, url, htmlUrl, null);
    }

    public static void show(@NonNull Context context, @NonNull String url
            , @Nullable String htmlUrl, @Nullable String downloadUrl){
        FileModel fileModel = new FileModel();
        fileModel.setHtmlUrl(htmlUrl);
        fileModel.setDownloadUrl(downloadUrl);
        fileModel.setUrl(url);
        fileModel.setName(url.substring(url.lastIndexOf("/") + 1,
                url.contains("?") ? url.indexOf("?") : url.length()));
        show(context, fileModel);
    }

    public static void show(@NonNull Context context, @NonNull FileModel fileModel){
        show(context, fileModel, null);
    }

    public static void show(@NonNull Context context, @NonNull FileModel fileModel
            , @Nullable String repoName){
        Intent intent = new Intent(context, ViewerActivity.class);
        intent.putExtras(BundleHelper.builder().put("viewerType", ViewerType.RepoFile)
                .put("fileModel", fileModel).put("repoName", repoName).build());
        context.startActivity(intent);
    }

    public static void showForDiff(@NonNull Context context, @NonNull CommitFile commitFile){
        Intent intent = new Intent(context, ViewerActivity.class);
        intent.putExtras(BundleHelper.builder().put("viewerType", ViewerType.DiffFile)
                .put("commitFile", commitFile).build());
        context.startActivity(intent);
    }

    @AutoAccess ViewerType viewerType;

    @AutoAccess FileModel fileModel;
    @AutoAccess CommitFile commitFile;
    @AutoAccess String repoName;

    @AutoAccess String title;
    @AutoAccess String source;

    @AutoAccess String imageUrl;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(fileModel != null || commitFile != null || imageUrl != null)
            getMenuInflater().inflate(R.menu.menu_viewer, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // ---------------------------------------------------------------------------------
    // Editing a repository file (push access)
    // ---------------------------------------------------------------------------------

    /** owner, repo, path, branch of a repository file, from its contents API url */
    @Nullable private String[] fileRef;
    private boolean canEdit;
    @Nullable private io.reactivex.rxjava3.disposables.Disposable permissionCheck;

    @Nullable
    private static String[] parseFileRef(@Nullable String url) {
        if (url == null) return null;
        android.net.Uri uri = android.net.Uri.parse(url);
        java.util.List<String> parts = uri.getPathSegments();
        // repos/{owner}/{repo}/contents/{path...}
        if (parts.size() < 5 || !"repos".equals(parts.get(0)) || !"contents".equals(parts.get(3))) return null;
        String path = String.join("/", parts.subList(4, parts.size()));
        String ref = uri.getQueryParameter("ref");
        return new String[]{parts.get(1), parts.get(2), path, ref};
    }

    private void checkCanEdit() {
        if (!ViewerType.RepoFile.equals(viewerType) || fileModel == null) return;
        fileRef = parseFileRef(fileModel.getUrl());
        if (fileRef == null) return;
        final String owner = fileRef[0];
        final String repo = fileRef[1];
        permissionCheck = io.reactivex.rxjava3.core.Single.fromCallable(() ->
                com.thirtydegreesray.openhub.http.GitHubApi.call(
                        com.thirtydegreesray.openhub.http.GitHubApi.service(
                                com.thirtydegreesray.openhub.http.RepoService.class)
                                .getRepoInfo(false, owner, repo)))
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(repository -> {
                    canEdit = com.thirtydegreesray.openhub.mvp.presenter.ReleaseInfoPresenter
                            .hasPushAccess(repository);
                    if (fileRef[3] == null) fileRef[3] = repository.getDefaultBranch();
                    invalidateOptionsMenu();
                }, error -> { });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean edit = canEdit && fileRef != null && fileRef[3] != null;
        MenuItem item = menu.findItem(R.id.action_edit_file);
        if (item != null) item.setVisible(edit);
        item = menu.findItem(R.id.action_rename_file);
        if (item != null) item.setVisible(edit && fileModel.getSha() != null);
        item = menu.findItem(R.id.action_delete_file);
        if (item != null) item.setVisible(edit);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        if (permissionCheck != null) permissionCheck.dispose();
        super.onDestroy();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        String title;
        if(ViewerType.RepoFile.equals(viewerType)){
            title = fileModel.getName();
        } else if(ViewerType.DiffFile.equals(viewerType)){
            title = commitFile.getShortFileName();
        } else {
            title = this.title;
        }
        setToolbarTitle(title);
        checkCanEdit();
    }

    @Override
    protected ViewerFragment createFragment() {
        ViewerFragment fragment;
        if(ViewerType.RepoFile.equals(viewerType)){
            fragment = ViewerFragment.create(fileModel);
        } else if(ViewerType.DiffFile.equals(viewerType)){
            fragment = ViewerFragment.createForDiff(commitFile);
        } else if(ViewerType.Image.equals(viewerType)){
            fragment = ViewerFragment.createForImage(title, imageUrl);
        } else if(ViewerType.HtmlSource.equals(viewerType)){
            fragment = ViewerFragment.createForHtml(title, source);
        } else if(ViewerType.Code.equals(viewerType)){
            fragment = ViewerFragment.createForCode(title, source);
        } else {
            fragment = ViewerFragment.createForMd(title, source);
        }
        return fragment;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home)
            return super.onOptionsItemSelected(item);
        if(item.getItemId() == R.id.action_fullscreen){
            intoFullScreen();
            return true;
        }
        if (fileRef != null && item.getItemId() == R.id.action_edit_file) {
            startActivity(EditFileActivity.editIntent(this, fileRef[0], fileRef[1], fileRef[3], fileRef[2]));
            finish();
            return true;
        } else if (fileRef != null && item.getItemId() == R.id.action_rename_file) {
            com.thirtydegreesray.openhub.ui.widget.RepoFileActions.rename(this, fileRef[0], fileRef[1],
                    fileRef[3], fileRef[2], fileModel.getSha(), this::finish);
            return true;
        } else if (fileRef != null && item.getItemId() == R.id.action_delete_file) {
            com.thirtydegreesray.openhub.ui.widget.RepoFileActions.delete(this, fileRef[0], fileRef[1],
                    fileRef[3], fileRef[2], this::finish);
            return true;
        }
        String htmlUrl = null;
        if(fileModel != null) htmlUrl = fileModel.getHtmlUrl();
        if(commitFile != null) htmlUrl = commitFile.getBlobUrl();
        if(imageUrl != null) htmlUrl = imageUrl;
        if(!StringUtils.isBlank(htmlUrl)){
            int itemId = item.getItemId();
            if (itemId == R.id.action_open_in_browser) {
                AppOpener.openInCustomTabsOrBrowser(getActivity(), htmlUrl);
                return true;
            } else if (itemId == R.id.action_share) {
                AppOpener.shareText(getActivity(), htmlUrl);
                return true;
            } else if (itemId == R.id.action_copy_url) {
                AppUtils.copyToClipboard(getActivity(), htmlUrl);
                return true;
            }
        }

        int selectedItemId = item.getItemId();
        if (selectedItemId == R.id.action_view_file) {
            ViewerActivity.show(getActivity(), commitFile.getContentsUrl(),
                    commitFile.getBlobUrl(), commitFile.getRawUrl());
            return true;
        } else if (selectedItemId == R.id.action_download) {
            String downloadUrl ;
            String fileName ;
            if(fileModel != null){
                downloadUrl = fileModel.getDownloadUrl();
                fileName = fileModel.getName();
                if(!StringUtils.isBlank(repoName)) fileName = repoName.concat("-").concat(fileName);
            } else {
                downloadUrl = imageUrl;
                fileName = title;
            }
            AppOpener.startDownload(getActivity(), downloadUrl, fileName);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
