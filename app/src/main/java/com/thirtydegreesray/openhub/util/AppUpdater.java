package com.thirtydegreesray.openhub.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;

import com.thirtydegreesray.openhub.AppConfig;
import com.thirtydegreesray.openhub.BuildConfig;
import com.thirtydegreesray.openhub.R;
import com.thirtydegreesray.openhub.http.GitHubApi;
import com.thirtydegreesray.openhub.http.ReleaseService;
import com.thirtydegreesray.openhub.mvp.model.Release;
import com.thirtydegreesray.openhub.mvp.model.ReleaseAsset;

import java.util.Locale;

import es.dmoral.toasty.Toasty;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Built-in updater: compares the installed version with the latest release of
 * nikakvo/OpenHub and offers the new APK. Checked when the app starts (at most every
 * few hours, can be turned off in Settings) and on demand from Settings.
 */
public final class AppUpdater {

    public static final String OWNER = "nikakvo";
    public static final String REPO = "OpenHub";

    /** SwitchPreference key in settings.xml */
    public static final String PREF_ENABLED = "checkUpdates";
    /** Preference key in settings.xml */
    public static final String PREF_CHECK_NOW = "checkUpdatesNow";
    private static final String PREF_SKIPPED = "updateSkippedVersion";
    private static final String PREF_LAST_CHECK = "updateLastCheck";

    private static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;

    private AppUpdater() {
    }

    public static boolean isEnabled() {
        return PrefUtils.getDefaultSp().getBoolean(PREF_ENABLED, true);
    }

    @NonNull
    public static String currentVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /** Quiet check at app start: only when enabled and not checked recently. */
    public static void checkOnStart(@NonNull Activity activity) {
        if (!isEnabled()) return;
        SharedPreferences sp = PrefUtils.getDefaultSp();
        long now = System.currentTimeMillis();
        if (now - sp.getLong(PREF_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return;
        check(activity, false);
    }

    /**
     * @param manual from Settings: also says "up to date", and ignores "Skip this version"
     */
    public static void check(@NonNull Activity activity, boolean manual) {
        if (manual) Toasty.info(activity, activity.getString(R.string.update_checking)).show();
        Single.fromCallable(() -> GitHubApi.call(GitHubApi.service(ReleaseService.class)
                        .getLatestRelease(true, OWNER, REPO)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(release -> {
                    PrefUtils.getDefaultSp().edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    onLatest(activity, release, manual);
                }, error -> {
                    if (manual && !activity.isFinishing()) {
                        Toasty.error(activity, activity.getString(R.string.update_check_failed,
                                StringUtils.isBlank(error.getMessage()) ? error.toString()
                                        : error.getMessage())).show();
                    }
                });
    }

    private static void onLatest(@NonNull Activity activity, @NonNull Release release, boolean manual) {
        String latest = versionOf(release.getTagName());
        String current = currentVersion();
        if (compare(latest, current) <= 0) {
            if (manual) {
                Toasty.success(activity, activity.getString(R.string.update_up_to_date, current)).show();
            }
            return;
        }
        String skipped = PrefUtils.getDefaultSp().getString(PREF_SKIPPED, null);
        if (!manual && latest.equals(skipped)) return;
        showDialog(activity, release, latest, current);
    }

    private static void showDialog(@NonNull Activity activity, @NonNull Release release,
                                   @NonNull String latest, @NonNull String current) {
        TextView notes = new TextView(activity);
        int pad = activity.getResources().getDimensionPixelSize(R.dimen.spacing_x_large);
        notes.setPadding(pad, pad / 2, pad, 0);
        notes.setTextIsSelectable(true);
        Markwon markwon = Markwon.builder(activity)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(activity))
                .usePlugin(LinkifyPlugin.create())
                .build();
        String body = StringUtils.isBlank(release.getBody()) ? "" : release.getBody().trim();
        String text = activity.getString(R.string.update_message, latest, current)
                + (body.isEmpty() ? "" : "\n\n" + body);
        markwon.setMarkdown(notes, text);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(notes);

        final ReleaseAsset apk = findApk(release);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_title)
                .setView(scroll)
                .setPositiveButton(apk != null ? R.string.update_download : R.string.update_open_release,
                        (d, w) -> {
                            if (apk != null) {
                                AppOpener.startDownload(activity, apk.getDownloadUrl(), apk.getName(), apk.getDigest());
                                Toasty.info(activity, activity.getString(R.string.update_downloading)).show();
                            } else {
                                AppOpener.openInCustomTabsOrBrowser(activity, releaseUrl(release));
                            }
                        })
                .setNeutralButton(R.string.update_skip, (d, w) -> PrefUtils.getDefaultSp().edit()
                        .putString(PREF_SKIPPED, latest).apply())
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    @Nullable
    private static ReleaseAsset findApk(@NonNull Release release) {
        if (release.getAssets() == null) return null;
        for (ReleaseAsset asset : release.getAssets()) {
            if (asset.getName() != null && asset.getName().toLowerCase(Locale.ROOT).endsWith(".apk")
                    && "uploaded".equals(asset.getState())) {
                return asset;
            }
        }
        return null;
    }

    @NonNull
    private static String releaseUrl(@NonNull Release release) {
        return !StringUtils.isBlank(release.getHtmlUrl()) ? release.getHtmlUrl()
                : AppConfig.OPENHUB_HOME + "/releases/latest";
    }

    // ---------------------------------------------------------------------------------
    // Versions: "v3.4.0" / "3.4.0" / "3.4.1-tears.2"
    // ---------------------------------------------------------------------------------

    @NonNull
    static String versionOf(@Nullable String tag) {
        if (tag == null) return "";
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t;
    }

    /**
     * Semantic-version order: numbers first; with equal numbers a version without a
     * suffix (a release) is newer than one with a suffix (a test build of it), and
     * suffixes compare part by part, numerically where both parts are numbers.
     */
    @VisibleForTesting
    static int compare(@NonNull String a, @NonNull String b) {
        String[] pa = splitSuffix(a);
        String[] pb = splitSuffix(b);
        int numbers = compareParts(pa[0].split("\\."), pb[0].split("\\."), true);
        if (numbers != 0) return numbers;
        if (pa[1] == null && pb[1] == null) return 0;
        if (pa[1] == null) return 1;
        if (pb[1] == null) return -1;
        return compareParts(pa[1].split("[.-]"), pb[1].split("[.-]"), false);
    }

    @NonNull
    private static String[] splitSuffix(@NonNull String version) {
        String v = version.split("\\+")[0];
        int dash = v.indexOf('-');
        return dash < 0 ? new String[]{v, null} : new String[]{v.substring(0, dash), v.substring(dash + 1)};
    }

    private static int compareParts(@NonNull String[] a, @NonNull String[] b, boolean padWithZero) {
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (!padWithZero && (i >= a.length || i >= b.length)) return Integer.compare(a.length, b.length);
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            Long nx = number(x);
            Long ny = number(y);
            int c;
            if (nx != null && ny != null) c = Long.compare(nx, ny);
            else if (nx != null) c = -1;
            else if (ny != null) c = 1;
            else c = x.compareTo(y);
            if (c != 0) return c;
        }
        return 0;
    }

    @Nullable
    private static Long number(@NonNull String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
