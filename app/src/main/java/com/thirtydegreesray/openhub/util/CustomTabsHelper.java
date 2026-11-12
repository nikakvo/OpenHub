package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import androidx.browser.customtabs.CustomTabsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ThirtyDegreesRay on 2017/12/28 14:06:39
 */

public enum  CustomTabsHelper {
    INSTANCE;

    /** the action browsers declare for their Custom Tabs service ("android.support.customtabs.action.CustomTabsService") */
    private final String ACTION_CUSTOM_TABS_CONNECTION = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

    private final String CHROME_STABLE_PACKAGE = "com.android.chrome";
    private final String CHROME_BETA_PACKAGE = "com.chrome.beta";
    private final String CHROME_DEV_PACKAGE = "com.chrome.dev";
    private final String CHROME_LOCAL_PACKAGE = "com.google.android.apps.chrome";

    private final String FIREFOX_STABLE_PACKAGE = "org.mozilla.firefox";
    private final String FIREFOX_CN_STABLE_PACKAGE = "cn.mozilla.firefox";
    private final String FIREFOX_FOCUS_PACKAGE = "org.mozilla.focus";
    private final String FIREFOX_BETA_PACKAGE = "org.mozilla.firefox_beta";
    private final String FIREFOX_DEV_PACKAGE = "org.mozilla.fennec_aurora";

    //order for choose best package
    private final List<String> ORDERED_PACKAGES = Arrays.asList(
            CHROME_STABLE_PACKAGE, CHROME_BETA_PACKAGE, CHROME_DEV_PACKAGE, CHROME_LOCAL_PACKAGE,
            FIREFOX_STABLE_PACKAGE, FIREFOX_CN_STABLE_PACKAGE, FIREFOX_FOCUS_PACKAGE,
            FIREFOX_BETA_PACKAGE, FIREFOX_DEV_PACKAGE
    );

    /**
     * The browser to open Custom Tabs with: the default browser when it has Custom Tabs;
     * none when the default browser has no Custom Tabs (the link then opens in it the normal
     * way, the user chose it); without a default browser the first known one (Chrome,
     * Firefox, ...) or else the first that has them. Asked again each time, so a new
     * default browser or a removed one counts at once.
     *
     * @param context {@link Context} to use for accessing {@link PackageManager}.
     * @return The package name to use for Custom Tabs, or null.
     */
    public String getBestPackageName(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo defaultViewHandlerInfo = pm.resolveActivity(activityIntent, PackageManager.MATCH_DEFAULT_ONLY);
        String defaultViewHandlerPackageName = null;
        if (defaultViewHandlerInfo != null && defaultViewHandlerInfo.activityInfo != null
                && BrowserTargets.isDirectTarget(defaultViewHandlerInfo.activityInfo.packageName,
                defaultViewHandlerInfo.activityInfo.name, context.getPackageName())) {
            defaultViewHandlerPackageName = defaultViewHandlerInfo.activityInfo.packageName;
        }

        // All apps that can open web pages (MATCH_ALL: otherwise Android 12+ names only the default browser).
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, PackageManager.MATCH_ALL);
        List<String> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            String packageName = info.activityInfo.packageName;
            if (packagesSupportingCustomTabs.contains(packageName)) continue;
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(packageName);
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(packageName);
            }
        }

        if (packagesSupportingCustomTabs.isEmpty()) return null;
        if (!TextUtils.isEmpty(defaultViewHandlerPackageName)) {
            if (hasSpecializedHandlerIntents(context, activityIntent)) return null;
            return packagesSupportingCustomTabs.contains(defaultViewHandlerPackageName)
                    ? defaultViewHandlerPackageName : null;
        }
        String known = getFirstMatchedPackage(packagesSupportingCustomTabs);
        return known != null ? known : packagesSupportingCustomTabs.get(0);
    }

    /**
     * Used to check whether there is a specialized handler for a given intent.
     * @param intent The intent to check with.
     * @return Whether there is a specialized handler for the given intent.
     */
    private boolean hasSpecializedHandlerIntents(Context context, Intent intent) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> handlers = pm.queryIntentActivities(intent,
                    PackageManager.GET_RESOLVED_FILTER);
            if (handlers == null || handlers.size() == 0) {
                return false;
            }
            for (ResolveInfo resolveInfo : handlers) {
                IntentFilter filter = resolveInfo.filter;
                if (filter == null) continue;
                if (filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0) continue;
                if (resolveInfo.activityInfo == null) continue;
                return true;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Compare local custom tabs packages with ORDERED_PACKAGES, return the first matched
     */
    private String getFirstMatchedPackage(List<String> packagesSupportingCustomTabs){
        for(String packageName : ORDERED_PACKAGES){
            if(packagesSupportingCustomTabs.contains(packageName)){
                return packageName;
            }
        }
        return null;
    }

}
