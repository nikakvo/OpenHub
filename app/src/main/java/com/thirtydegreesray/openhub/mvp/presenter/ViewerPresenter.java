

package com.thirtydegreesray.openhub.mvp.presenter;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.AppDatabase;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.mvp.contract.IViewerContract;
import com.thirtydegreesray.openhub.mvp.model.CommitFile;
import com.thirtydegreesray.openhub.mvp.model.FileModel;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.ui.activity.ViewerActivity;
import com.thirtydegreesray.openhub.util.GitHubHelper;
import com.thirtydegreesray.openhub.util.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.thirtydegreesray.openhub.http.GitDataService;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.RepoService;
import com.thirtydegreesray.openhub.http.error.HttpError;
import com.thirtydegreesray.openhub.http.error.HttpErrorCode;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.util.BlobRefs;
import com.thirtydegreesray.openhub.util.GitHubLinks;
import com.thirtydegreesray.openhub.util.Json;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import javax.inject.Inject;

import okhttp3.ResponseBody;
import retrofit2.Response;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by ThirtyDegreesRay on 2017/8/19 15:58:43
 */

public class ViewerPresenter extends BasePresenter<IViewerContract.View>
        implements IViewerContract.Presenter{

    @AutoAccess ViewerActivity.ViewerType viewerType;

    @AutoAccess FileModel fileModel;
    private String downloadSource;

    @AutoAccess String title;
    @AutoAccess String source;
    @AutoAccess String imageUrl;

    @AutoAccess CommitFile commitFile;

    @Inject
    public ViewerPresenter(AppDatabase db) {
        super(db);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if(ViewerActivity.ViewerType.RepoFile.equals(viewerType)){
            load(false);
        } else if(ViewerActivity.ViewerType.DiffFile.equals(viewerType)) {
            mView.loadDiffFile(commitFile.getPatch());
        } else if(ViewerActivity.ViewerType.Image.equals(viewerType)) {
            mView.loadImageUrl(imageUrl);
        } else if(ViewerActivity.ViewerType.HtmlSource.equals(viewerType)) {
            mView.loadMdText(source, null);
        } else if(ViewerActivity.ViewerType.Code.equals(viewerType)) {
            mView.loadCode(source, GitHubHelper.getExtension(title));
        } else {
            mView.loadMdText(source, null);
        }
    }

    @Override
    public void load(boolean isReload) {
        final String url = fileModel.getUrl();
        final String htmlUrl = fileModel.getHtmlUrl();
        if(StringUtils.isBlank(url) || StringUtils.isBlank(htmlUrl)){
            mView.showWarningToast(getString(R.string.url_invalid));
            mView.hideLoading();
            return;
        }

        if(GitHubHelper.isArchive(url)){
            mView.showWarningToast(getString(R.string.view_archive_file_error));
            mView.hideLoading();
            return;
        }

        if(GitHubHelper.isImage(url)){
            mView.loadImageUrl(fileModel.getDownloadUrl());
            mView.hideLoading();
            return;
        }

        HttpObserver<ResponseBody> httpObserver =
                new HttpObserver<ResponseBody>() {
                    @Override
                    public void onError(Throwable error) {
                        mView.hideLoading();
                        explainError(error);
                    }

                    @Override
                    public void onSuccess(HttpResponse<ResponseBody> response) {
                        mView.hideLoading();
                        try {
                            downloadSource = response.body().string();
                            if(GitHubHelper.isMarkdown(url)){
                                mView.loadMdText(downloadSource, htmlUrl);
                            }else{
                                mView.loadCode(downloadSource, getExtension());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
        generalRxHttpExecute(new IObservableCreator<ResponseBody>() {
            @Override
            public Observable<Response<ResponseBody>> createObservable(boolean forceNetWork) {
                return GitHubHelper.isMarkdown(url) ?
                        getRepoService().getFileAsHtmlStream(forceNetWork, url) :
                        getRepoService().getFileAsStream(forceNetWork, url);
            }
        }, httpObserver, false);
        mView.showLoading();
    }

    // ---------------------------------------------------------------------------------
    // A file that cannot be loaded: say why in the page
    // ---------------------------------------------------------------------------------

    @Nullable private Disposable resolving;

    /** Network trouble (no answer from GitHub), not an answer that says "no". */
    private static boolean isOffline(@Nullable Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException || t instanceof SocketTimeoutException
                    || t instanceof ConnectException || t instanceof NoRouteToHostException
                    || t instanceof javax.net.ssl.SSLException) {
                return true;
            }
            if (t instanceof HttpError && !(t instanceof HttpPageNoFoundError)
                    && ((HttpError) t).getErrorCode() == HttpErrorCode.NO_CACHE_AND_NETWORK) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    private void explainError(@NonNull Throwable error) {
        final String htmlUrl = fileModel.getHtmlUrl();
        if (isOffline(error)) {
            showOffline();
            return;
        }
        if (!(error instanceof HttpPageNoFoundError)) {
            mView.showFileProblem(getContext().getString(R.string.viewer_load_failed, getErrorTip(error)),
                    getString(R.string.splash_try_again), () -> load(true), htmlUrl);
            return;
        }
        final GitHubLinks.Blob blob = GitHubLinks.blob(htmlUrl);
        if (blob == null) {
            mView.showFileProblem(getContext().getString(R.string.viewer_no_file_plain,
                    fileModel.getPath() != null ? fileModel.getPath() : fileModel.getName()),
                    null, null, htmlUrl);
            return;
        }
        mView.showLoading();
        if (resolving != null) resolving.dispose();
        resolving = Single.fromCallable(() -> resolve(blob))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(answer -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    showAnswer(blob, answer);
                }, e -> {
                    if (mView == null) return;
                    mView.hideLoading();
                    if (isOffline(e)) {
                        showOffline();
                    } else {
                        mView.showFileProblem(getContext().getString(R.string.viewer_load_failed,
                                getErrorTip(e)), getString(R.string.splash_try_again),
                                () -> load(true), htmlUrl);
                    }
                });
    }

    private void showOffline() {
        mView.showFileProblem(getString(R.string.viewer_offline),
                getString(R.string.splash_try_again), () -> load(true), null);
    }

    /** What GitHub says about the repository and the refs of a blob link that answered 404. */
    private static final class Answer {
        boolean noRepo;
        @Nullable String defaultBranch;
        /** leading segments that form an existing ref, 0 = none */
        int refLength;
        List<String> segments;
    }

    @NonNull
    private static Answer resolve(@NonNull GitHubLinks.Blob blob) throws IOException {
        Answer answer = new Answer();
        answer.segments = BlobRefs.segments(blob.ref, blob.path);
        Repository repository;
        try {
            repository = GitHubApi.call(GitHubApi.service(RepoService.class)
                    .getRepoInfo(true, blob.owner, blob.repo));
        } catch (GitHubApi.ApiException e) {
            if (e.code == 404) {
                answer.noRepo = true;
                return answer;
            }
            throw e;
        }
        answer.defaultBranch = repository == null ? null : repository.getDefaultBranch();
        String first = GitHubLinks.encodeSegment(answer.segments.get(0));
        List<String> refs = new ArrayList<>();
        for (String kind : new String[]{"heads/", "tags/"}) {
            JsonArray list = GitHubApi.call(GitHubApi.service(GitDataService.class)
                    .getMatchingRefs(blob.owner, blob.repo, kind + first));
            if (list == null) continue;
            for (JsonElement element : list) {
                if (element.isJsonObject()) refs.add(Json.str(element.getAsJsonObject(), "ref"));
            }
        }
        answer.refLength = BlobRefs.longestRef(answer.segments, BlobRefs.shortNames(refs));
        return answer;
    }

    private void showAnswer(@NonNull GitHubLinks.Blob blob, @NonNull Answer answer) {
        String repoName = blob.owner + "/" + blob.repo;
        String htmlUrl = blob.htmlUrl();
        if (answer.noRepo) {
            mView.showFileProblem(getContext().getString(R.string.viewer_no_repo, repoName),
                    null, null, htmlUrl);
            return;
        }
        List<String> s = answer.segments;
        if (answer.refLength > 0) {
            String ref = BlobRefs.join(s, 0, answer.refLength);
            String path = BlobRefs.join(s, answer.refLength, s.size());
            if (!ref.equals(blob.ref)) {
                // the branch name has a "/" in it: the same link, read correctly
                mView.openFile(fileAt(blob, ref, path));
                return;
            }
        }
        boolean refExists = answer.refLength > 0 || BlobRefs.looksLikeSha(blob.ref);
        String text = refExists
                ? getContext().getString(R.string.viewer_no_file, blob.path, blob.ref, repoName)
                : getContext().getString(R.string.viewer_no_ref, blob.ref + "/" + blob.path, repoName);
        String defaultBranch = answer.defaultBranch;
        if (defaultBranch != null && !defaultBranch.equals(blob.ref)) {
            final FileModel onDefault = fileAt(blob, defaultBranch, blob.path);
            mView.showFileProblem(text, getString(R.string.viewer_open_default_branch),
                    () -> mView.openFile(onDefault), htmlUrl);
        } else {
            mView.showFileProblem(text, null, null, htmlUrl);
        }
    }

    @NonNull
    private static FileModel fileAt(@NonNull GitHubLinks.Blob blob, @NonNull String ref, @NonNull String path) {
        String url = "https://github.com/" + blob.owner + "/" + blob.repo + "/blob/"
                + GitHubLinks.encodePath(ref) + "/" + GitHubLinks.encodePath(path);
        // GitHubLinks.blob() would take the first segment as the branch: built by hand
        FileModel file = new FileModel();
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        file.setName(name);
        file.setPath(path);
        file.setUrl("https://api.github.com/repos/" + blob.owner + "/" + blob.repo + "/contents/"
                + GitHubLinks.encodePath(path) + "?ref=" + GitHubLinks.encodeSegment(ref));
        file.setHtmlUrl(url);
        file.setDownloadUrl("https://raw.githubusercontent.com/" + blob.owner + "/" + blob.repo + "/"
                + GitHubLinks.encodePath(ref) + "/" + GitHubLinks.encodePath(path));
        return file;
    }

    @Override
    public void detachView() {
        if (resolving != null) resolving.dispose();
        super.detachView();
    }

    public boolean isCode(){
        String url = fileModel != null ? fileModel.getUrl() : commitFile.getBlobUrl();
        return !GitHubHelper.isArchive(url) &&
                !GitHubHelper.isImage(url) &&
                !GitHubHelper.isMarkdown(url);
    }

    public String getDownloadSource() {
        return downloadSource;
    }

    public String getExtension(){
        return GitHubHelper.getExtension(fileModel.getUrl());
    }

    public FileModel getFileModel() {
        return fileModel;
    }

    public CommitFile getCommitFile() {
        return commitFile;
    }

    public ViewerActivity.ViewerType getViewerType() {
        return viewerType;
    }
}
