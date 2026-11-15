package com.thirtydegreesray.openhub.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.thirtydegreesray.openhub.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which app really opens github.com links from other apps, asked the way Android starts a link
 * (resolveActivity with MATCH_DEFAULT_ONLY). OpenHub's own "Open by default" choice is not
 * enough: an app that has github.com verified (the official GitHub app) wins over it, and
 * Android may keep showing github.com ticked for OpenHub while links go to that app.
 */
public final class GitHubLinkOwner {

    /** Who opens github.com links. */
    public enum Owner {
        /** OpenHub. */
        OPENHUB,
        /** Another app that is not a browser (usually the official GitHub app). */
        OTHER_APP,
        /** A browser, or Android asks every time. */
        BROWSER
    }

    public static final class Result {
        @NonNull public final Owner owner;
        /** The other app's package and name, for {@link Owner#OTHER_APP}. */
        @Nullable public final String packageName;
        @Nullable public final CharSequence label;

        Result(@NonNull Owner owner, @Nullable String packageName, @Nullable CharSequence label) {
            this.owner = owner;
            this.packageName = packageName;
            this.label = label;
        }
    }

    private static final Set<String> CHOOSERS = new HashSet<>();

    static {
        CHOOSERS.add("android");
        CHOOSERS.add("com.android.intentresolver");
        CHOOSERS.add("com.android.internal.app");
    }

    private GitHubLinkOwner() {
    }

    /**
     * Pure: the owner from the package that would open the link, OpenHub's package and the
     * packages that open any web page (browsers).
     */
    @NonNull
    static Owner decide(@Nullable String winner, @NonNull String self, @NonNull Set<String> browsers) {
        if (winner == null || CHOOSERS.contains(winner) || browsers.contains(winner)) return Owner.BROWSER;
        if (winner.equals(self)) return Owner.OPENHUB;
        return Owner.OTHER_APP;
    }

    @NonNull
    public static Result check(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        String winner = null;
        try {
            ResolveInfo info = pm.resolveActivity(view("https://github.com/"), PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null) winner = info.activityInfo.packageName;
        } catch (Exception ignored) {
            // unknown: treated as a browser
        }
        Set<String> browsers = new HashSet<>();
        try {
            // an address no app owns: whatever opens it is a browser
            List<ResolveInfo> any = pm.queryIntentActivities(view("https://www.example.com/"),
                    PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo info : any) {
                if (info.activityInfo != null) browsers.add(info.activityInfo.packageName);
            }
        } catch (Exception ignored) {
            // no list: only the chooser counts as "browser"
        }
        Owner owner = decide(winner, self, browsers);
        CharSequence label = null;
        if (owner == Owner.OTHER_APP) {
            try {
                ApplicationInfo app = pm.getApplicationInfo(winner, 0);
                label = pm.getApplicationLabel(app);
            } catch (Exception e) {
                label = winner;
            }
        }
        return new Result(owner, owner == Owner.OTHER_APP ? winner : null, label);
    }

    /** The summary of Settings - "Open GitHub links in OpenHub". */
    @NonNull
    public static String summary(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return context.getString(R.string.open_links_summary_old);
        }
        Result result = check(context);
        switch (result.owner) {
            case OPENHUB:
                return context.getString(R.string.open_links_summary_on);
            case OTHER_APP:
                return context.getString(R.string.open_links_summary_other, result.label);
            default:
                return context.getString(R.string.open_links_summary_off);
        }
    }

    /**
     * Opens the "Open by default" page that helps now: the other app's page when another app
     * has github.com (its "Open supported links" must go off first), else OpenHub's own page.
     * A short hint says what to do there.
     */
    public static void openSettings(@NonNull Context context) {
        String target = context.getPackageName();
        String hint = context.getString(R.string.open_links_hint);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Result result = check(context);
            if (result.owner == Owner.OTHER_APP && result.packageName != null) {
                target = result.packageName;
                hint = context.getString(R.string.open_links_hint_other, result.label);
            } else if (result.owner == Owner.OPENHUB) {
                hint = null;
            }
        }
        Uri app = Uri.parse("package:" + target);
        Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, app)
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app));
        }
        if (hint != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toasts.info(context, hint, Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private static Intent view(@NonNull String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
    }
}
