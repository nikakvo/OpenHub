

package com.thirtydegreesray.openhub.util;

import androidx.core.os.LocaleListCompat;
import androidx.appcompat.app.AppCompatDelegate;
import android.os.LocaleList;
import android.app.LocaleManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.thirtydegreesray.openhub.AppApplication;
import com.thirtydegreesray.openhub.R;

import java.util.IllformedLocaleException;
import java.util.Locale;


/**
 * Created on 2017/10/30 11:23:03
 * Copied from Copyright (C) 2017 Kosh.
 * Modified by Copyright (C) 2017 ThirtyDegreesRay.
 */

public class AppUtils {

    private static final String DOWNLOAD_SERVICE_PACKAGE_NAME = "com.android.providers.downloads";

    public static boolean checkApplicationEnabledSetting(Context context, String packageName) {
        int state = context.getPackageManager().getApplicationEnabledSetting(packageName);
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static boolean checkDownloadServiceEnabled(Context context) {
        return checkApplicationEnabledSetting(context, DOWNLOAD_SERVICE_PACKAGE_NAME);
    }

    public static void showDownloadServiceSetting(Context context) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + DOWNLOAD_SERVICE_PACKAGE_NAME));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }


    /**
     * The app is English only. Android 13+ keeps the language of each app in the system
     * (LocaleManager, also shown in the app's system settings), older versions get it
     * from AppCompat for every screen. Called once when the app starts.
     */
    public static void applyAppLanguage(@NonNull Context context) {
        Locale locale = getLocale(PrefUtils.getLanguage());
        Locale.setDefault(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            LocaleList wanted = new LocaleList(locale);
            if (manager != null && !wanted.equals(manager.getApplicationLocales())) {
                manager.setApplicationLocales(wanted);
            }
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale));
        }
    }

    /**
     * The default Locale (dates, numbers) follows the app language. A WebView can reset
     * it to the system language, so screens and pages call this again.
     */
    public static void updateAppLanguage(@NonNull Context context) {
        Locale locale = getLocale(PrefUtils.getLanguage());
        if (!locale.equals(Locale.getDefault())) Locale.setDefault(locale);
    }

    @NonNull
    public static Locale getLocale(String language) {
        String[] array = language.split("-");
        try {
            if (array.length > 1) {
                //zh-rCN, zh-rTW", pt-rPT, etc... remove the 'r'
                String country = array[1].replaceFirst("r", "");
                return new Locale.Builder().setLanguage(array[0]).setRegion(country).build();
            }
            return new Locale.Builder().setLanguage(language).build();
        } catch (IllformedLocaleException e) {
            return Locale.getDefault();
        }
    }

    public static boolean isNightMode() {
        String theme = PrefUtils.getTheme();
        return PrefUtils.DARK.equals(theme) || PrefUtils.AMOLED_DARK.equals(theme);
    }

    public static void copyToClipboard(@NonNull Context context, @NonNull String uri) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), uri);
        clipboard.setPrimaryClip(clip);
        Toasts.success(AppApplication.get(), context.getString(R.string.success_copied)).show();
    }

    public static boolean isLandscape(@NonNull Resources resources) {
        return resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @SuppressWarnings("WeakerAccess")
    public static void showKeyboard(@NonNull View v, @NonNull Context activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(v, 0);
    }

    public static void showKeyboard(@NonNull View v) {
        showKeyboard(v, v.getContext());
    }

    public static void hideKeyboard(@NonNull View view) {
        InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Nullable
    public static long getFirstInstallTime() {
        long time = 0;
        try {
            PackageInfo packageInfo = AppApplication.get().getPackageManager()
                    .getPackageInfo(AppApplication.get().getPackageName(), 0);
            time = packageInfo.firstInstallTime;
        } catch (PackageManager.NameNotFoundException e1) {
            e1.printStackTrace();
        }
        return time;
    }

}
