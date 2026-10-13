package com.thirtydegreesray.openhub.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;

import com.google.android.material.navigation.NavigationView;
import com.thirtydegreesray.openhub.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Colored side menu icons, in colors like GitHub's (brighter on dark themes, deeper on
 * light ones). Settings: Menu icons (Colored / Plain).
 */
public final class DrawerIcons {

    public static final String PREF = "menuIconStyle";
    public static final String STYLE_COLORED = "colored";
    public static final String STYLE_PLAIN = "plain";
    public static final String[] STYLES = {STYLE_COLORED, STYLE_PLAIN};

    /** Same colors as the GitHub interface: bright on dark themes, deeper on light ones. */
    private static final SparseIntArray DARK = new SparseIntArray();
    private static final SparseIntArray LIGHT = new SparseIntArray();

    static {
        put(R.id.nav_profile, 0xFF58A6FF, 0xFF0969DA);
        put(R.id.nav_notifications, 0xFFF0883E, 0xFFBC4C00);
        put(R.id.nav_news, 0xFF3FB950, 0xFF1A7F37);
        put(R.id.nav_issues, 0xFF3FB950, 0xFF1A7F37);
        put(R.id.nav_gists, 0xFFA371F7, 0xFF8250DF);
        put(R.id.nav_new_repo, 0xFF03A9F4, 0xFF0288D1);
        put(R.id.nav_owned, 0xFF58A6FF, 0xFF0969DA);
        put(R.id.nav_starred, 0xFFE3B341, 0xFF9A6700);
        put(R.id.nav_bookmarks, 0xFFDB61A2, 0xFFBF3989);
        put(R.id.nav_search, 0xFF79C0FF, 0xFF0969DA);
        put(R.id.nav_trace, 0xFF8B949E, 0xFF57606A);
        put(R.id.nav_trending, 0xFFF85149, 0xFFCF222E);
        put(R.id.nav_collections, 0xFFD2A8FF, 0xFF8250DF);
        put(R.id.nav_topics, 0xFF56D4DD, 0xFF1B7C83);
        put(R.id.nav_public_news, 0xFF3FB950, 0xFF1A7F37);
        put(R.id.nav_settings, 0xFF8B949E, 0xFF57606A);
        put(R.id.nav_help, 0xFF03A9F4, 0xFF0288D1);
        put(R.id.nav_about, 0xFF8B949E, 0xFF57606A);
        put(R.id.nav_logout, 0xFFF85149, 0xFFCF222E);
        put(R.id.nav_add_account, 0xFF3FB950, 0xFF1A7F37);
    }

    private static void put(int id, int dark, int light) {
        DARK.put(id, dark);
        LIGHT.put(id, light);
    }

    private DrawerIcons() {
    }

    @NonNull
    public static String getStyle(@NonNull Context context) {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString(PREF, STYLE_COLORED);
    }

    /** Colors the icons of the side menu according to the setting. */
    public static void apply(@NonNull NavigationView nav) {
        if (STYLE_PLAIN.equals(getStyle(nav.getContext()))) return;
        SparseIntArray colors = AppUtils.isNightMode() ? DARK : LIGHT;
        nav.setItemIconTintList(null);
        for (MenuItem item : items(nav.getMenu())) {
            Drawable icon = item.getIcon();
            if (icon == null) continue;
            Drawable tinted = icon.mutate();
            tinted.setTint(colors.get(item.getItemId(), colors.get(R.id.nav_profile)));
            item.setIcon(tinted);
        }
    }

    @NonNull
    private static List<MenuItem> items(@NonNull Menu menu) {
        List<MenuItem> list = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            list.add(item);
            SubMenu sub = item.getSubMenu();
            if (sub != null) list.addAll(items(sub));
        }
        return list;
    }

    /** For the settings summary. */
    public static int label(@NonNull String style) {
        return STYLE_PLAIN.equals(style) ? R.string.menu_icons_plain : R.string.menu_icons_colored;
    }
}
