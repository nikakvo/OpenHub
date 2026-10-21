package com.thirtydegreesray.openhub.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.thirtydegreesray.openhub.util.state.AutoAccess;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.ReleaseService;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.contract.base.IBaseContract;
import com.thirtydegreesray.openhub.ui.activity.base.SingleFragmentActivity;
import com.thirtydegreesray.openhub.ui.fragment.ReleasesFragment;
import com.thirtydegreesray.openhub.util.BundleHelper;
import com.thirtydegreesray.openhub.util.ReleaseChanges;
import com.thirtydegreesray.openhub.util.StringUtils;
import com.thirtydegreesray.openhub.util.ViewUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by ThirtyDegreesRay on 2017/9/16 10:58:03
 */

public class ReleasesActivity extends SingleFragmentActivity<IBaseContract.Presenter, ReleasesFragment> {

    public static void show(Activity activity, String owner, String repo) {
        Intent intent = createIntent(activity, owner, repo);
        activity.startActivity(intent);
    }

    public static Intent createIntent(Activity activity, String owner, String repo) {
        return new Intent(activity, ReleasesActivity.class)
                .putExtras(BundleHelper.builder()
                        .put("owner", owner)
                        .put("repo", repo).build());
    }

    @AutoAccess String owner;
    @AutoAccess String repo;

    private FloatingActionButton newReleaseButton;
    private boolean canCreate;
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** How many of the newest releases Clean up keeps, per radio button. */
    private static final int[] KEEP_COUNTS = {1, 3, 5};

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        String subTitle = owner.concat("/").concat(repo);
        setToolbarTitle(getString(R.string.releases), subTitle);
        setToolbarScrollAble(true);
        if (newReleaseButton != null) {
            newReleaseButton.setContentDescription(getString(R.string.release_new));
        }
    }

    /** Shows "New release" for users with push access. */
    public void setCanCreate(boolean canCreate) {
        if (this.canCreate != canCreate) {
            this.canCreate = canCreate;
            invalidateOptionsMenu();
        }
        if (newReleaseButton == null) return;
        if (canCreate) newReleaseButton.show();
        else newReleaseButton.hide();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (canCreate) {
            menu.add(Menu.NONE, R.id.action_clean_up_releases, Menu.NONE, R.string.releases_clean_up)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clean_up_releases) {
            loadForCleanUp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------
    // Clean up old releases
    // ---------------------------------------------------------------------------------

    /** Every release (drafts too) and the id of the one marked Latest. */
    private static final class AllReleases {
        final List<Release> releases;
        @Nullable final String latestId;

        AllReleases(List<Release> releases, @Nullable String latestId) {
            this.releases = releases;
            this.latestId = latestId;
        }
    }

    private ReleaseService releaseService() {
        return GitHubApi.service(ReleaseService.class);
    }

    private void loadForCleanUp() {
        showProgressDialog(getString(R.string.please_wait));
        disposables.add(Single.fromCallable(() -> {
                    List<Release> all = new ArrayList<>();
                    for (int page = 1; page <= 20; page++) {
                        ArrayList<Release> batch = GitHubApi.call(releaseService().getReleasesPage(owner, repo, page));
                        if (batch == null || batch.isEmpty()) break;
                        all.addAll(batch);
                        if (batch.size() < 100) break;
                    }
                    String latestId = null;
                    try {
                        Release latest = GitHubApi.call(releaseService().getLatestRelease(true, owner, repo));
                        if (latest != null) latestId = latest.getId();
                    } catch (Exception ignored) {
                        // no published release yet (404)
                    }
                    return new AllReleases(all, latestId);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    dismissProgressDialog();
                    showCleanUpDialog(result);
                }, error -> {
                    dismissProgressDialog();
                    showErrorToast(message(error));
                }));
    }

    private void showCleanUpDialog(@NonNull AllReleases all) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // the builder's context, so the radio buttons and check boxes get dialog text colors
        android.content.Context ctx = builder.getContext();
        int pad = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                getResources().getDisplayMetrics()));
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView keepLabel = new TextView(ctx);
        keepLabel.setText(R.string.releases_clean_up_keep);
        layout.addView(keepLabel);
        RadioGroup keepGroup = new RadioGroup(ctx);
        int[] labels = {R.string.releases_keep_1, R.string.releases_keep_3, R.string.releases_keep_5};
        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(ctx);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTag(KEEP_COUNTS[i]);
            keepGroup.addView(button);
            if (i == 0) keepGroup.check(button.getId());
        }
        layout.addView(keepGroup);

        CheckBox tags = new CheckBox(ctx);
        tags.setText(R.string.releases_clean_up_tags);
        tags.setChecked(true);
        layout.addView(tags);
        CheckBox drafts = new CheckBox(ctx);
        drafts.setText(R.string.releases_clean_up_drafts);
        layout.addView(drafts);

        TextView note = new TextView(ctx);
        note.setText(R.string.releases_clean_up_note);
        note.setPadding(0, pad / 2, 0, 0);
        layout.addView(note);

        builder.setTitle(R.string.releases_clean_up)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.releases_clean_up_next, (dialog, which) -> {
                    RadioButton checked = keepGroup.findViewById(keepGroup.getCheckedRadioButtonId());
                    int keep = checked == null ? 1 : (Integer) checked.getTag();
                    confirmCleanUp(pick(all, keep, drafts.isChecked()), tags.isChecked());
                })
                .show();
    }

    /**
     * The releases to delete: published ones (pre-releases too) after the {@code keep}
     * newest. The one marked Latest counts as one of the kept when it is among the newest,
     * and is kept anyway when it is not. Drafts only when asked.
     */
    @NonNull
    static List<Release> pick(@NonNull AllReleases all, int keep, boolean withDrafts) {
        List<Release> result = new ArrayList<>();
        int kept = 0;
        // GitHub lists releases newest first
        for (Release release : all.releases) {
            if (release.isDraft()) {
                if (withDrafts) result.add(release);
                continue;
            }
            if (kept < keep) {
                kept++;
                continue;
            }
            if (release.getId() != null && release.getId().equals(all.latestId)) continue;
            result.add(release);
        }
        return result;
    }

    private void confirmCleanUp(@NonNull List<Release> toDelete, boolean withTags) {
        if (toDelete.isEmpty()) {
            showInfoToast(getString(R.string.releases_clean_up_none));
            return;
        }
        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (Release release : toDelete) {
            if (shown == 15) {
                names.append("\n").append(getString(R.string.releases_clean_up_more, toDelete.size() - shown));
                break;
            }
            if (names.length() > 0) names.append("\n");
            String tag = StringUtils.isBlank(release.getTagName()) ? release.getName() : release.getTagName();
            names.append("• ").append(tag);
            if (release.isDraft()) names.append(" (").append(getString(R.string.release_draft_badge)).append(")");
            shown++;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.releases_clean_up)
                .setMessage(getResources().getQuantityString(confirmText(toDelete, withTags),
                        toDelete.size(), toDelete.size(), names.toString()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> runCleanUp(toDelete, withTags))
                .show();
    }

    /** Drafts have no tag yet, so only drafts means no word about tags. */
    private static int confirmText(@NonNull List<Release> toDelete, boolean withTags) {
        boolean anyPublished = false;
        for (Release release : toDelete) {
            if (!release.isDraft()) {
                anyPublished = true;
                break;
            }
        }
        if (!anyPublished) return R.plurals.releases_clean_up_confirm_drafts;
        return withTags ? R.plurals.releases_clean_up_confirm_tags : R.plurals.releases_clean_up_confirm;
    }

    private void runCleanUp(@NonNull List<Release> toDelete, boolean withTags) {
        int total = toDelete.size();
        showProgressDialog(getString(R.string.releases_clean_up_progress, 1, total));
        List<String> failed = new ArrayList<>();
        Set<String> tagsKept = new HashSet<>();
        disposables.add(Observable.<Integer>create(emitter -> {
                    for (int i = 0; i < total && !emitter.isDisposed(); i++) {
                        emitter.onNext(i + 1);
                        Release release = toDelete.get(i);
                        String tag = release.getTagName();
                        try {
                            GitHubApi.call(releaseService().deleteRelease(owner, repo, release.getId()));
                        } catch (Exception e) {
                            synchronized (failed) {
                                failed.add(StringUtils.isBlank(tag) ? release.getName() : tag);
                            }
                            continue;
                        }
                        if (withTags && !release.isDraft() && !StringUtils.isBlank(tag)) {
                            try {
                                GitHubApi.call(releaseService().deleteTag(owner, repo, Uri.encode(tag, "/")));
                            } catch (Exception e) {
                                // the release is gone; a tag that cannot go (protected, already
                                // deleted) is reported but is not an error
                                synchronized (tagsKept) {
                                    tagsKept.add(tag);
                                }
                            }
                        }
                    }
                    emitter.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        index -> getProgressDialog(getString(R.string.releases_clean_up_progress, index, total)),
                        error -> {
                            dismissProgressDialog();
                            ReleaseChanges.markChanged();
                            refreshList();
                            showErrorToast(message(error));
                        },
                        () -> {
                            dismissProgressDialog();
                            ReleaseChanges.markChanged();
                            refreshList();
                            int deleted = total - failed.size();
                            if (failed.isEmpty() && tagsKept.isEmpty()) {
                                showSuccessToast(getResources().getQuantityString(
                                        R.plurals.releases_clean_up_done, deleted, deleted));
                            } else {
                                List<String> notes = new ArrayList<>();
                                if (!failed.isEmpty()) {
                                    notes.add(getString(R.string.releases_clean_up_failed,
                                            String.join(", ", failed)));
                                }
                                if (!tagsKept.isEmpty()) {
                                    notes.add(getString(R.string.releases_clean_up_tags_kept,
                                            String.join(", ", tagsKept)));
                                }
                                new AlertDialog.Builder(this)
                                        .setTitle(getResources().getQuantityString(
                                                R.plurals.releases_clean_up_done, deleted, deleted))
                                        .setMessage(String.join("\n\n", notes))
                                        .setPositiveButton(R.string.ok, null)
                                        .show();
                            }
                        }));
    }

    /** The list reloads itself on resume when releases changed. */
    private void refreshList() {
        ReleasesFragment fragment = getFragment();
        if (fragment != null) fragment.onResume();
    }

    @NonNull
    private static String message(@NonNull Throwable error) {
        return StringUtils.isBlank(error.getMessage()) ? error.toString() : error.getMessage();
    }

    @Override
    protected void bindViews(View root) {
        super.bindViews(root);
        newReleaseButton = root.findViewById(R.id.float_action_bn);
        ViewUtils.setOnClick(root, R.id.float_action_bn, v ->
                startActivity(EditReleaseActivity.createIntent(this, owner, repo)));
    }

    @Override
    protected ReleasesFragment createFragment() {
        return ReleasesFragment.create(owner, repo);
    }
}
