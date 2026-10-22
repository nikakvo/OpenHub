package com.thirtydegreesray.openhub.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.R;

/**
 * The launcher icon. Every icon is an activity-alias of the splash screen in the manifest
 * (com.thirtydegreesray.openhub.Icon*); exactly one of them is enabled. Settings: App icon.
 * The enabled alias itself is the stored choice, so nothing is kept in the preferences.
 */
public final class AppIcons {

    public static final String PREF = "appIcon";

    public static final String MATRIX = "matrix";
    public static final String CLASSIC = "classic";
    public static final String COSMIC = "cosmic";
    public static final String OUTLINE = "outline";
    public static final String[] ICONS = {MATRIX, CLASSIC, COSMIC, OUTLINE};
    /** The one enabled in the manifest. */
    private static final String DEFAULT = MATRIX;

    private static final String ALIAS_PACKAGE = "com.thirtydegreesray.openhub.";

    private AppIcons() {
    }

    @NonNull
    private static ComponentName component(@NonNull Context context, @NonNull String icon) {
        String alias;
        switch (icon) {
            case CLASSIC:
                alias = "IconClassic";
                break;
            case COSMIC:
                alias = "IconCosmic";
                break;
            case OUTLINE:
                alias = "IconOutline";
                break;
            default:
                alias = "IconMatrix";
                break;
        }
        return new ComponentName(context.getPackageName(), ALIAS_PACKAGE + alias);
    }

    /** The icon the launcher shows now. */
    @NonNull
    public static String current(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        for (String icon : ICONS) {
            if (pm.getComponentEnabledSetting(component(context, icon))
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return icon;
            }
        }
        return DEFAULT;
    }

    /**
     * Turns the chosen icon on and every other one off. The new one is enabled first, so
     * there is never a moment without a launcher entry. The app keeps running.
     */
    public static void set(@NonNull Context context, @NonNull String icon) {
        if (icon.equals(current(context))) return;
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(component(context, icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (String other : ICONS) {
            if (other.equals(icon)) continue;
            pm.setComponentEnabledSetting(component(context, other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    /** The launcher icon the user chose, for the About screen. */
    @NonNull
    public static android.graphics.drawable.Drawable drawable(@NonNull Context context) {
        try {
            return context.getPackageManager().getActivityIcon(component(context, current(context)));
        } catch (PackageManager.NameNotFoundException e) {
            return java.util.Objects.requireNonNull(
                    androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.logo_matrix));
        }
    }

    /** For the settings summary and the chooser. */
    public static int label(@NonNull String icon) {
        switch (icon) {
            case CLASSIC:
                return R.string.app_icon_classic;
            case COSMIC:
                return R.string.app_icon_cosmic;
            case OUTLINE:
                return R.string.app_icon_outline;
            default:
                return R.string.app_icon_matrix;
        }
    }
}
