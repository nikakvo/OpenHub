package com.thirtydegreesray.openhub.mvp.presenter;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.dao.DaoSession;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.ReleaseService;
import com.thirtydegreesray.openhub.http.core.HttpObserver;
import com.thirtydegreesray.openhub.http.core.HttpResponse;
import com.thirtydegreesray.openhub.http.error.HttpPageNoFoundError;
import com.thirtydegreesray.openhub.mvp.contract.IReleaseInfoContract;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.ReleaseAsset;
import com.thirtydegreesray.openhub.mvp.model.Repository;
import com.thirtydegreesray.openhub.mvp.presenter.base.BasePresenter;
import com.thirtydegreesray.openhub.util.ReleaseChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.state.AutoAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Response;

/**
 * Created by ThirtyDegreesRay on 2017/9/16 13:11:46
 *
 * Shows one release; with push access also publishes, edits and deletes it and
 * renames or deletes its assets.
 */
public class ReleaseInfoPresenter extends BasePresenter<IReleaseInfoContract.View>
        implements IReleaseInfoContract.Presenter{

    @AutoAccess String owner;
    @AutoAccess String repoName;
    @AutoAccess String tagName;
    /** Opens a release by id, e.g. a draft (drafts have no tag page) from an upload notification. */
    @AutoAccess String releaseId;
    @AutoAccess Release release;
    /** Open the newest release instead of a given tag. */
    @AutoAccess boolean latest;
    /** The user has push access: may edit, publish and delete. */
    @AutoAccess boolean canEdit;
    /** Id of the repository's Latest release, for the badge. */
    @AutoAccess String latestId;

    private long loadedStamp;
    private final CompositeDisposable actions = new CompositeDisposable();

    @Inject
    public ReleaseInfoPresenter(DaoSession daoSession) {
        super(daoSession);
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        if(release != null){
            loadedStamp = ReleaseChanges.stamp();
            mView.showReleaseInfo(release);
            // The list loads releases as rendered HTML only; fetch the full, current copy.
            if (release.getBody() == null) reload();
        } else {
            loadReleaseInfo();
        }
        checkPermission();
        loadLatestId();
    }

    @Override
    public void detachView() {
        actions.clear();
        super.detachView();
    }

    /** Called when the screen comes back; reloads if a release changed meanwhile. */
    public void onResume() {
        if (release != null && ReleaseChanges.changedSince(loadedStamp)) {
            reload();
            loadLatestId();
        }
    }

    private void loadReleaseInfo(){
        mView.showLoading();
        HttpObserver<Release> httpObserver = new HttpObserver<Release>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
                if (latest && error instanceof HttpPageNoFoundError) {
                    mView.onNoRelease();
                } else if (error instanceof HttpPageNoFoundError) {
                    mView.onReleaseGone();
                } else {
                    mView.showErrorToast(getErrorTip(error));
                }
            }

            @Override
            public void onSuccess(HttpResponse<Release> response) {
                release = response.body();
                loadedStamp = ReleaseChanges.stamp();
                mView.showReleaseInfo(release);
                mView.hideLoading();
            }
        };
        final boolean byId = !StringUtils.isBlank(releaseId);
        generalRxHttpExecute(new IObservableCreator<Release>() {
            @Override
            public Observable<Response<Release>> createObservable(boolean forceNetWork) {
                if (byId) return releaseService().getRelease(true, owner, repoName, releaseId);
                return latest && tagName == null
                        ? getRepoService().getLatestRelease(forceNetWork, owner, repoName)
                        : getRepoService().getReleaseByTagName(forceNetWork, owner, repoName, tagName);
            }
        }, httpObserver, !byId);
    }

    /** Fetches the current state of the shown release from the network. */
    public void reload() {
        if (release == null) {
            loadReleaseInfo();
            return;
        }
        final String id = release.getId();
        mView.showLoading();
        generalRxHttpExecute(new IObservableCreator<Release>() {
            @Override
            public Observable<Response<Release>> createObservable(boolean forceNetWork) {
                return releaseService().getRelease(true, owner, repoName, id);
            }
        }, new HttpObserver<Release>() {
            @Override
            public void onError(Throwable error) {
                mView.hideLoading();
                if (error instanceof HttpPageNoFoundError) {
                    mView.onReleaseGone();
                } else {
                    mView.showErrorToast(getErrorTip(error));
                }
            }

            @Override
            public void onSuccess(HttpResponse<Release> response) {
                mView.hideLoading();
                release = response.body();
                loadedStamp = ReleaseChanges.stamp();
                mView.showReleaseInfo(release);
            }
        });
    }

    private void checkPermission() {
        generalRxHttpExecute(new IObservableCreator<Repository>() {
            @Override
            public Observable<Response<Repository>> createObservable(boolean forceNetWork) {
                return getRepoService().getRepoInfo(forceNetWork, owner, repoName);
            }
        }, new HttpObserver<Repository>() {
            @Override
            public void onError(Throwable error) {
            }

            @Override
            public void onSuccess(HttpResponse<Repository> response) {
                boolean edit = hasPushAccess(response.body());
                if (edit != canEdit) {
                    canEdit = edit;
                    mView.onPermissionChecked(canEdit);
                }
            }
        }, true);
    }

    public static boolean hasPushAccess(@Nullable Repository repository) {
        return repository != null && repository.getPermissions() != null
                && (repository.getPermissions().isPush() || repository.getPermissions().isAdmin());
    }

    private void loadLatestId() {
        generalRxHttpExecute(new IObservableCreator<Release>() {
            @Override
            public Observable<Response<Release>> createObservable(boolean forceNetWork) {
                return releaseService().getLatestRelease(true, owner, repoName);
            }
        }, new HttpObserver<Release>() {
            @Override
            public void onError(Throwable error) {
                // 404: the repository has no published, non pre-release release
            }

            @Override
            public void onSuccess(HttpResponse<Release> response) {
                String id = response.body() == null ? null : response.body().getId();
                if (!Objects.equals(id, latestId)) {
                    latestId = id;
                    if (release != null) mView.showReleaseInfo(release);
                }
            }
        });
    }

    // ---------------------------------------------------------------------------------
    // Write actions (push access)
    // ---------------------------------------------------------------------------------

    /** Publishes a draft. It becomes the Latest release unless it is a pre-release. */
    public void publish() {
        if (release == null) return;
        final String id = release.getId();
        final boolean preRelease = release.isPreRelease();
        run(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("draft", false);
            body.put("make_latest", preRelease ? "false" : "true");
            return GitHubApi.call(releaseService().updateRelease(owner, repoName, id, body));
        }, updated -> {
            mView.showSuccessToast(getContext().getString(R.string.release_published,
                    updated.getTagName()));
            ReleaseChanges.markChanged();
            reload();
            loadLatestId();
        });
    }

    /** Makes the shown published, non pre-release release the repository's Latest. */
    public void makeLatest() {
        if (release == null) return;
        final String id = release.getId();
        run(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("make_latest", "true");
            return GitHubApi.call(releaseService().updateRelease(owner, repoName, id, body));
        }, updated -> {
            mView.showSuccessToast(getContext().getString(R.string.release_now_latest,
                    updated.getTagName()));
            ReleaseChanges.markChanged();
            loadLatestId();
        });
    }

    public void delete(final boolean alsoDeleteTag) {
        if (release == null) return;
        final String id = release.getId();
        final String tag = release.getTagName();
        final boolean draft = release.isDraft();
        run(() -> {
            GitHubApi.call(releaseService().deleteRelease(owner, repoName, id));
            if (alsoDeleteTag && !draft && !StringUtils.isBlank(tag)) {
                GitHubApi.call(releaseService().deleteTag(owner, repoName, Uri.encode(tag, "/")));
            }
            return Boolean.TRUE;
        }, done -> {
            ReleaseChanges.markChanged();
            mView.onReleaseDeleted();
        });
    }

    public void deleteAsset(@NonNull final ReleaseAsset asset) {
        run(() -> {
            GitHubApi.call(releaseService().deleteAsset(owner, repoName, asset.getId()));
            return Boolean.TRUE;
        }, done -> {
            mView.showSuccessToast(getContext().getString(R.string.release_asset_deleted,
                    asset.getName()));
            ReleaseChanges.markChanged();
            reload();
        });
    }

    public void renameAsset(@NonNull final ReleaseAsset asset, @NonNull final String name) {
        run(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            return GitHubApi.call(releaseService().updateAsset(owner, repoName, asset.getId(), body));
        }, updated -> {
            ReleaseChanges.markChanged();
            reload();
        });
    }

    private <T> void run(@NonNull Callable<T> work, @NonNull Consumer<T> onSuccess) {
        mView.showProgressDialog(getContext().getString(R.string.please_wait));
        actions.add(Single.fromCallable(work)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    mView.dismissProgressDialog();
                    onSuccess.accept(result);
                }, error -> {
                    mView.dismissProgressDialog();
                    mView.showErrorToast(getErrorTip(error));
                }));
    }

    @NonNull
    private ReleaseService releaseService() {
        return getServices(ReleaseService.class, AppConfig.GITHUB_API_BASE_URL, true);
    }

    // ---------------------------------------------------------------------------------

    public String getTagName(){
        return  release == null ? tagName : release.getTagName();
    }

    public String getRepoName() {
        return repoName;
    }

    public Release getRelease(){
        return release;
    }

    public String getOwner() {
        return owner;
    }

    public boolean canEdit() {
        return canEdit;
    }

    public boolean isLatest() {
        return release != null && latestId != null && latestId.equals(release.getId());
    }
}
