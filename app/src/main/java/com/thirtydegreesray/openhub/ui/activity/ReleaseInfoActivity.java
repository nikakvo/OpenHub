package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.common.GlideApp;
import com.thirtydegreesray.openhub.inject.component.AppComponent;
import com.thirtydegreesray.openhub.inject.component.DaggerActivityComponent;
import com.thirtydegreesray.openhub.inject.module.ActivityModule;
import com.thirtydegreesray.openhub.mvp.contract.IReleaseInfoContract;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.ReleaseAsset;
import com.thirtydegreesray.openhub.mvp.model.UploadItem;
import com.thirtydegreesray.openhub.mvp.presenter.ReleaseInfoPresenter;
import com.thirtydegreesray.openhub.service.UploadService;
import com.thirtydegreesray.openhub.ui.activity.base.BaseActivity;
import com.thirtydegreesray.openhub.ui.widget.DownloadSourceDialog;
import com.thirtydegreesray.openhub.ui.widget.TextInputDialog;
import com.thirtydegreesray.openhub.ui.widget.webview.CodeWebView;
import com.thirtydegreesray.openhub.util.AppOpener;
import com.thirtydegreesray.openhub.util.AppUtils;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ContentFiles;
import com.thirtydegreesray.openhub.util.MarkdownHtml;
import com.thirtydegreesray.openhub.util.PrefUtils;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Created by ThirtyDegreesRay on 2017/9/16 13:13:46
 *
 * One release: notes, assets, and for maintainers edit / publish / upload / delete.
 */

public class ReleaseInfoActivity extends BaseActivity<ReleaseInfoPresenter>
        implements IReleaseInfoContract.View {

    public static void show(Activity activity, @NonNull String owner, @NonNull String repoName,
                            @NonNull String tagName) {
        Intent intent = createIntent(activity, owner, repoName, tagName);
        activity.startActivity(intent);
    }

    public static void show(Activity activity, @NonNull String owner,
                            @NonNull String repoName, @Nullable Release release) {
        Intent intent = new Intent(activity, ReleaseInfoActivity.class);
        intent.putExtras(BundleHelper.builder().put("release", release)
                .put("owner", owner).put("repoName", repoName).build());
        activity.startActivity(intent);
    }

    /** Opens the newest release of the repository (not drafts or pre-releases). */
    public static void showLatest(Activity activity, @NonNull String owner, @NonNull String repoName) {
        Intent intent = new Intent(activity, ReleaseInfoActivity.class);
        intent.putExtras(BundleHelper.builder().put("owner", owner).put("repoName", repoName)
                .put("latest", true).build());
        activity.startActivity(intent);
    }

    public static Intent createIntent(Activity activity, @NonNull String owner, @NonNull String repoName,
                                      @NonNull String tagName) {
        return new Intent(activity, ReleaseInfoActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("tagName", tagName)
                        .put("owner", owner)
                        .put("repoName", repoName).build());
    }

    NestedScrollView scrollView;
    CodeWebView webView;
    ImageView userAvatar;
    TextView userName;
    TextView releaseTitle;
    View badges;
    TextView badgeDraft;
    TextView badgePreRelease;
    TextView badgeLatest;
    View assetsSection;
    TextView assetsTitle;
    LinearLayout assetsList;
    FloatingActionButton downloadBn;
    ProgressBar loader;

    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    onFilesPicked(ContentFiles.fromPickerResult(this, result.getData()));
                }
            });

    private final ActivityResultLauncher<Intent> editor = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) mPresenter.reload();
            });

    @Override
    public void showReleaseInfo(Release release) {
        downloadBn.setVisibility(release.isDraft() ? View.GONE : View.VISIBLE);
        setToolbarTitle(StringUtils.isBlank(release.getTagName())
                        ? getString(R.string.release_draft_badge) : release.getTagName(),
                mPresenter.getOwner().concat("/").concat(mPresenter.getRepoName()));
        // GitHub's rendered HTML when we have it; a just-saved release only has the Markdown
        String body = !StringUtils.isBlank(release.getBodyHtml()) ? release.getBodyHtml()
                : StringUtils.isBlank(release.getBody()) ? "" : MarkdownHtml.render(release.getBody());
        webView.setMdSource(body, null);

        if (release.getAuthor() != null) {
            GlideApp.with(getActivity())
                    .load(release.getAuthor().getAvatarUrl())
                    .onlyRetrieveFromCache(!PrefUtils.isLoadImageEnable())
                    .into(userAvatar);
            String time = release.getDisplayDate() == null ? ""
                    : StringUtils.getNewsTimeStr(getActivity(), release.getDisplayDate());
            String action = getString(release.isDraft() ? R.string.release_drafted_this : R.string.released_this);
            String timeStr = time.contains("-")
                    ? action.concat(" ").concat(getString(R.string.on)).concat(" ").concat(time)
                    : action.concat(" ").concat(time);
            String login = release.getAuthor().getLogin();
            SpannableStringBuilder spannable = new SpannableStringBuilder(login.concat(" ").concat(timeStr));
            spannable.setSpan(new ForegroundColorSpan(ViewUtils.getAccentColor(getActivity())),
                    0, login.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            userName.setText(spannable);
        }

        String name = release.getName();
        boolean showTitle = !StringUtils.isBlank(name) && !name.equals(release.getTagName());
        releaseTitle.setText(showTitle ? name : "");
        releaseTitle.setVisibility(showTitle ? View.VISIBLE : View.GONE);

        badgeDraft.setVisibility(release.isDraft() ? View.VISIBLE : View.GONE);
        badgePreRelease.setVisibility(release.isPreRelease() ? View.VISIBLE : View.GONE);
        badgeLatest.setVisibility(mPresenter.isLatest() ? View.VISIBLE : View.GONE);
        badges.setVisibility(release.isDraft() || release.isPreRelease() || mPresenter.isLatest()
                ? View.VISIBLE : View.GONE);

        showAssets(release);
        invalidateOptionsMenu();
    }

    private void showAssets(@NonNull Release release) {
        assetsList.removeAllViews();
        List<ReleaseAsset> assets = release.getAssets();
        if (assets == null || assets.isEmpty()) {
            assetsSection.setVisibility(View.GONE);
            return;
        }
        assetsSection.setVisibility(View.VISIBLE);
        assetsTitle.setText(getString(R.string.release_assets_count, assets.size()));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReleaseAsset asset : assets) {
            View row = inflater.inflate(R.layout.layout_item_release_asset, assetsList, false);
            ((TextView) row.findViewById(R.id.asset_name)).setText(asset.getName());
            ((TextView) row.findViewById(R.id.asset_meta)).setText(assetMeta(asset));
            View action = row.findViewById(R.id.asset_action_bn);
            action.setVisibility(mPresenter.canEdit() ? View.VISIBLE : View.GONE);
            action.setOnClickListener(v -> showAssetMenu(v, asset));
            View edit = row.findViewById(R.id.asset_edit_bn);
            edit.setVisibility(mPresenter.canEdit() ? View.VISIBLE : View.GONE);
            edit.setOnClickListener(v -> renameAsset(asset));
            row.findViewById(R.id.asset_download_bn).setOnClickListener(v -> download(asset));
            row.setOnClickListener(v -> download(asset));
            row.setOnLongClickListener(v -> {
                AppUtils.copyToClipboard(this, asset.getDownloadUrl());
                return true;
            });
            assetsList.addView(row);
        }
    }

    @NonNull
    private String assetMeta(@NonNull ReleaseAsset asset) {
        StringBuilder meta = new StringBuilder(StringUtils.getSizeString(asset.getSize()));
        if (!mPresenter.getRelease().isDraft()) {
            meta.append(" · ").append(getResources().getQuantityString(R.plurals.release_downloads,
                    asset.getDownloadCount(), asset.getDownloadCount()));
        }
        if (!"uploaded".equals(asset.getState())) {
            meta.append(" · ").append(getString(R.string.release_asset_incomplete));
        }
        if (!StringUtils.isBlank(asset.getLabel())) meta.append(" · ").append(asset.getLabel());
        return meta.toString();
    }

    private void download(@NonNull ReleaseAsset asset) {
        // A draft's assets are not public yet; the API address serves them with the token.
        String url = mPresenter.getRelease().isDraft() && !StringUtils.isBlank(asset.getApiUrl())
                ? asset.getApiUrl() : asset.getDownloadUrl();
        AppOpener.startDownload(this, url, asset.getName());
    }

    private void renameAsset(@NonNull ReleaseAsset asset) {
        TextInputDialog.show(this, R.string.release_rename_file, R.string.release_file_name,
                asset.getName(), true, name -> {
                    String clean = ContentFiles.assetName(name);
                    if (!clean.equals(asset.getName())) mPresenter.renameAsset(asset, clean);
                });
    }

    private void showAssetMenu(@NonNull View anchor, @NonNull ReleaseAsset asset) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.rename);
        popup.getMenu().add(0, 2, 1, R.string.copy_link);
        popup.getMenu().add(0, 3, 2, R.string.delete);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                renameAsset(asset);
            } else if (item.getItemId() == 2) {
                AppUtils.copyToClipboard(this, asset.getDownloadUrl());
            } else if (item.getItemId() == 3) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.release_delete_file)
                        .setMessage(getString(R.string.release_delete_file_message, asset.getName()))
                        .setPositiveButton(R.string.delete, (d, w) -> mPresenter.deleteAsset(asset))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onNoRelease() {
        showInfoToast(getString(R.string.no_releases));
        finish();
    }

    @Override
    public void onPermissionChecked(boolean canEdit) {
        if (mPresenter.getRelease() != null) showAssets(mPresenter.getRelease());
        invalidateOptionsMenu();
    }

    @Override
    public void onReleaseDeleted() {
        showSuccessToast(getString(R.string.release_deleted));
        finish();
    }

    @Override
    public void onReleaseGone() {
        showInfoToast(getString(R.string.release_gone));
        finish();
    }

    @Override
    protected void setupActivityComponent(AppComponent appComponent) {
        DaggerActivityComponent.builder()
                .appComponent(appComponent)
                .activityModule(new ActivityModule(this))
                .build()
                .inject(this);
    }

    @Nullable
    @Override
    protected int getContentView() {
        return R.layout.activity_release_info;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        setToolbarBackEnable();
        setToolbarTitle(mPresenter.getTagName() == null ? getString(R.string.latest_release)
                        : mPresenter.getTagName(),
                mPresenter.getOwner().concat("/").concat(mPresenter.getRepoName()));
        downloadBn.setVisibility(View.GONE);
        setToolbarScrollAble(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_release_info, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Release release = mPresenter.getRelease();
        boolean edit = mPresenter.canEdit() && release != null;
        boolean draft = release != null && release.isDraft();
        menu.findItem(R.id.action_edit_release).setVisible(edit);
        menu.findItem(R.id.action_publish_release).setVisible(edit && draft);
        menu.findItem(R.id.action_make_latest).setVisible(edit && !draft
                && !release.isPreRelease() && !mPresenter.isLatest());
        menu.findItem(R.id.action_upload_assets).setVisible(edit);
        menu.findItem(R.id.action_delete_release).setVisible(edit);
        menu.findItem(R.id.action_share_release).setVisible(release != null && !draft);
        menu.findItem(R.id.action_release_in_browser).setVisible(release != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Release release = mPresenter.getRelease();
        if (release == null) return super.onOptionsItemSelected(item);
        if (id == R.id.action_edit_release) {
            editor.launch(EditReleaseActivity.editIntent(this, mPresenter.getOwner(),
                    mPresenter.getRepoName(), release));
            return true;
        } else if (id == R.id.action_publish_release) {
            confirmPublish(release);
            return true;
        } else if (id == R.id.action_make_latest) {
            mPresenter.makeLatest();
            return true;
        } else if (id == R.id.action_upload_assets) {
            filePicker.launch(ContentFiles.pickerIntent());
            return true;
        } else if (id == R.id.action_delete_release) {
            confirmDelete(release);
            return true;
        } else if (id == R.id.action_share_release) {
            AppOpener.shareText(this, releaseUrl(release));
            return true;
        } else if (id == R.id.action_release_in_browser) {
            AppOpener.openInCustomTabsOrBrowser(this, releaseUrl(release));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    private String releaseUrl(@NonNull Release release) {
        if (!StringUtils.isBlank(release.getHtmlUrl())) return release.getHtmlUrl();
        String base = AppConfig.GITHUB_BASE_URL + mPresenter.getOwner() + "/" + mPresenter.getRepoName();
        return StringUtils.isBlank(release.getTagName()) ? base + "/releases"
                : base + "/releases/tag/" + release.getTagName();
    }

    private void confirmPublish(@NonNull Release release) {
        int pending = 0;
        if (release.getAssets() != null) {
            for (ReleaseAsset asset : release.getAssets()) {
                if (!"uploaded".equals(asset.getState())) pending++;
            }
        }
        String message = getString(R.string.release_publish_message, release.getTagName());
        if (pending > 0) message += "\n\n" + getString(R.string.release_publish_incomplete_files);
        new AlertDialog.Builder(this)
                .setTitle(R.string.release_publish)
                .setMessage(message)
                .setPositiveButton(R.string.release_publish, (d, w) -> mPresenter.publish())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(@NonNull Release release) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.release_delete)
                .setNegativeButton(R.string.cancel, null);
        String title = StringUtils.isBlank(release.getTagName())
                ? getString(R.string.release_draft_badge) : release.getTagName();
        builder.setMessage(getString(R.string.release_delete_message, title));
        final CheckBox deleteTag;
        if (!release.isDraft() && !StringUtils.isBlank(release.getTagName())) {
            deleteTag = new CheckBox(this);
            deleteTag.setText(getString(R.string.release_delete_tag_too, release.getTagName()));
            FrameLayout frame = new FrameLayout(this);
            int pad = getResources().getDimensionPixelSize(R.dimen.spacing_large);
            frame.setPadding(pad, pad / 2, pad, 0);
            frame.addView(deleteTag);
            builder.setView(frame);
        } else {
            deleteTag = null;
        }
        builder.setPositiveButton(R.string.delete, (d, w) ->
                mPresenter.delete(deleteTag != null && deleteTag.isChecked()));
        builder.show();
    }

    private void onFilesPicked(@NonNull ArrayList<UploadItem> items) {
        Release release = mPresenter.getRelease();
        if (items.isEmpty() || release == null) return;
        Set<String> existing = new HashSet<>();
        if (release.getAssets() != null) {
            for (ReleaseAsset asset : release.getAssets()) existing.add(asset.getName());
        }
        Set<String> picked = new HashSet<>();
        for (UploadItem item : items) {
            if (existing.contains(item.getName()) || !picked.add(item.getName())) {
                showErrorToast(getString(R.string.upload_name_exists, item.getName()));
                return;
            }
        }
        UploadService.start(this, mPresenter.getOwner(), mPresenter.getRepoName(), release,
                items, false, null);
        showInfoToast(getResources().getQuantityString(R.plurals.upload_started,
                items.size(), items.size()));
    }

    public void showDownloadDialog() {
        DownloadSourceDialog.show(getActivity(), mPresenter.getRepoName(),
                mPresenter.getRelease().getTagName(), mPresenter.getRelease());
    }

    public void onUserClick() {
        Release release = mPresenter.getRelease();
        if (release == null || release.getAuthor() == null) return;
        ProfileActivity.show(getActivity(), userAvatar, release.getAuthor().getLogin(),
                release.getAuthor().getAvatarUrl());
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

    @Override
    protected void onToolbarDoubleClick() {
        super.onToolbarDoubleClick();
        scrollView.scrollTo(0, 0);
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        scrollView = root.findViewById(R.id.scroll_view);
        webView = root.findViewById(R.id.web_view);
        userAvatar = root.findViewById(R.id.user_avatar);
        userName = root.findViewById(R.id.user_name);
        releaseTitle = root.findViewById(R.id.release_title);
        badges = root.findViewById(R.id.badges);
        badgeDraft = root.findViewById(R.id.badge_draft);
        badgePreRelease = root.findViewById(R.id.badge_prerelease);
        badgeLatest = root.findViewById(R.id.badge_latest);
        assetsSection = root.findViewById(R.id.assets_section);
        assetsTitle = root.findViewById(R.id.assets_title);
        assetsList = root.findViewById(R.id.assets_list);
        downloadBn = root.findViewById(R.id.download_bn);
        loader = root.findViewById(R.id.loader);
        ViewUtils.setOnClick(root, R.id.download_bn, v -> showDownloadDialog());
        ViewUtils.setOnClick(root, R.id.user_name, v -> onUserClick());
        ViewUtils.setOnClick(root, R.id.user_avatar, v -> onUserClick());
    }
}
